package com.suntravels.service;

import com.suntravels.dto.*;
import com.suntravels.dto.QuotationSearchResponse.*;
import com.suntravels.entity.OfferingStatus;
import com.suntravels.entity.HotelStatus;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.*;
import com.suntravels.repository.QuotationSearchRepository.*;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=15)
public class QuotationSearchService {
 public static final int MAX_ROOMS=8,MAX_NIGHTS=31,MAX_ADVANCE_DAYS=730,MAX_RESULTS=20;
 private final QuotationSearchRepository data; private final HotelRepository hotels; private final RoomTypeRepository rooms;
 private final Validator validator; private final Clock clock; private final NightlyQuotationPricing pricing;
 private final StayAvailability inventory;
 public QuotationSearchService(QuotationSearchRepository data,HotelRepository hotels,RoomTypeRepository rooms,Validator validator,Clock clock,NightlyQuotationPricing pricing,StayAvailability inventory) {
  this.data=data;this.hotels=hotels;this.rooms=rooms;this.validator=validator;this.clock=clock;this.pricing=pricing;this.inventory=inventory;
 }
 public Limits limits() { return new Limits(LocalDate.now(clock),MAX_ROOMS,MAX_NIGHTS,MAX_ADVANCE_DAYS,MAX_RESULTS,"INDIVIDUAL_ROOM_TYPES"); }
 public QuotationSearchFilters filters() {
  var hotelOptions=hotels.findAll(Sort.by(Sort.Order.asc("name"),Sort.Order.asc("hotelId"))).stream()
   .filter(h->h.getStatus()==HotelStatus.ACTIVE)
   .map(h->new QuotationSearchFilters.HotelOption(h.getHotelId(),h.getName(),h.getCity())).toList();
  var roomOptions=rooms.findAll(Sort.by(Sort.Order.asc("name"),Sort.Order.asc("roomTypeId"))).stream()
   .map(r->new QuotationSearchFilters.RoomTypeOption(r.getRoomTypeId(),r.getName())).toList();
  return new QuotationSearchFilters(hotelOptions,roomOptions);
 }
 public List<OfferingOccupancy> occupancy(Integer hotelId,Integer roomTypeId,LocalDate checkIn,LocalDate checkOut,String city) {
  if(hotelId==null || roomTypeId==null) throw QuotationSearchException.invalid("Select a hotel and room type for occupancy guidance");
  var request=new QuotationSearchRequest(checkIn,checkOut,List.of(new QuotationSearchRequest.RequestedRoom(1)),city,hotelId,roomTypeId);
  validate(request,LocalDate.now(clock));
  var candidates=data.candidates(request);
  if(candidates.size()>QuotationSearchRepository.MAX_OFFERINGS) throw QuotationSearchException.tooBroad();
  if(candidates.stream().anyMatch(c->c.maxAdults()<1)) throw QuotationSearchException.inconsistent();
  return candidates.stream().map(c->new OfferingOccupancy(c.offeringId(),c.roomTypeId(),c.roomTypeName(),c.maxAdults())).toList();
 }
 public QuotationSearchResponse search(QuotationSearchRequest request) { return evaluate(request,null); }
 public Quotation customize(QuotationSelectionRequest selection) {
  if(selection==null || !validator.validate(selection).isEmpty()) throw QuotationSearchException.invalid("Check selected hotel, contract, dates and rooms");
  var request=new QuotationSearchRequest(selection.checkIn(),selection.checkOut(),selection.rooms().stream()
   .map(r->new QuotationSearchRequest.RequestedRoom(r.adults())).toList(),null,selection.hotelId(),null);
  var result=evaluate(request,selection);
  if(result.quotations().size()!=1 || !result.quotations().get(0).hotelId().equals(selection.hotelId())) {
   String reason=result.unavailableOptions().stream().flatMap(o->o.reasons().stream()).map(UnavailabilityReason::message).collect(Collectors.joining(" "));
   throw QuotationSearchException.invalid(reason.isBlank()?"Selected rooms are unavailable or do not belong to the same eligible hotel and contract":reason);
  }
  return result.quotations().get(0);
 }
 private QuotationSearchResponse evaluate(QuotationSearchRequest request,QuotationSelectionRequest selection) {
  LocalDate today=LocalDate.now(clock); validate(request,today);
  List<Candidate> closed=data.closedCandidates(request);
  Set<Integer> closedOfferingIds=closed.stream().map(Candidate::offeringId).collect(Collectors.toSet());
  List<Candidate> candidates=new ArrayList<>(data.candidates(request));candidates.addAll(closed);
  if(selection!=null) candidates.removeIf(c->c.contractId()!=selection.contractId());
  if(candidates.size()>QuotationSearchRepository.MAX_OFFERINGS) throw QuotationSearchException.tooBroad();
  int nights=(int)ChronoUnit.DAYS.between(request.checkIn(),request.checkOut());
  if(candidates.isEmpty()) return response(request,today,nights,List.of(),List.of());
  Set<Integer> ids=candidates.stream().map(Candidate::offeringId).collect(Collectors.toSet());
  Set<Integer> contracts=candidates.stream().map(Candidate::contractId).collect(Collectors.toSet());
  Set<Integer> hotelIds=candidates.stream().map(Candidate::hotelId).collect(Collectors.toSet());
  if(data.inconsistent(ids,contracts,hotelIds,today)) throw QuotationSearchException.inconsistent();
  List<Season> seasons=data.seasons(ids); List<BookedRoom> bookings=data.bookedRooms(ids,request.checkIn(),request.checkOut());
  if(seasons.size()>QuotationSearchRepository.MAX_SEASONS || bookings.size()>QuotationSearchRepository.MAX_BOOKED_ITEMS) throw QuotationSearchException.tooBroad();
  Map<Integer,List<Season>> bySeason=seasons.stream().collect(Collectors.groupingBy(Season::offeringId));
  Map<Integer,List<BookedRoom>> byBooking=bookings.stream().collect(Collectors.groupingBy(BookedRoom::offeringId));
  Map<Integer,List<NightlyQuotationPricing.Tariff>> tariffs=new HashMap<>(); Map<Integer,Integer> available=new HashMap<>();
  for(Candidate c:candidates) {
   if(c.offeringId()<=0 || c.contractId()<=0 || c.contractCode()==null || c.contractCode().isBlank() || c.contractCode().length()>45
    || c.hotelName()==null || c.hotelName().isBlank() || c.roomTypeName()==null || c.roomTypeName().isBlank()
    || !validator.validate(new ContractRequest(c.hotelId(),c.validFrom(),c.validTo(),c.markup(),c.currency())).isEmpty()
    || !validator.validate(new OfferingRequest(c.roomTypeId(),c.baseRate(),c.contractedRooms(),c.maxAdults(),OfferingStatus.ENABLED)).isEmpty()
    || c.validFrom().isAfter(request.checkIn()) || c.validTo().isBefore(request.checkOut().minusDays(1))) throw QuotationSearchException.inconsistent();
   tariffs.put(c.offeringId(),pricing.tariffs(c,bySeason.getOrDefault(c.offeringId(),List.of()),request.checkIn(),request.checkOut()));
   available.put(c.offeringId(),inventory.available(c.offeringId(),c.contractedRooms(),byBooking.getOrDefault(c.offeringId(),List.of()),request.checkIn(),request.checkOut()));
  }
  List<Quotation> results=new ArrayList<>();List<UnavailableOption> unavailable=new ArrayList<>();
  List<SelectionOffering> choices=candidates.stream().map(c->new SelectionOffering(c.hotelId(),c.hotelName(),c.contractId(),c.offeringId(),c.roomTypeName(),c.maxAdults(),available.get(c.offeringId()),c.currency(),closedOfferingIds.contains(c.offeringId()),
   java.util.stream.IntStream.range(0,request.rooms().size()).mapToObj(r->new SelectionRoom(r+1,request.rooms().get(r).adults(),
    request.rooms().get(r).adults()<=c.maxAdults()?pricing.total(tariffs.get(c.offeringId()),request.rooms().get(r).adults(),c.markup()):null)).toList())).toList();
  Map<Integer,List<Candidate>> groups=candidates.stream().collect(Collectors.groupingBy(c->selection!=null?c.contractId():c.offeringId(),TreeMap::new,Collectors.toList()));
  for(List<Candidate> group:groups.values()) {
   group.sort(Comparator.comparingInt(Candidate::offeringId)); Candidate parent=group.get(0);
   if(group.stream().anyMatch(c->c.hotelId()!=parent.hotelId() || !c.currency().equals(parent.currency()) || c.markup().compareTo(parent.markup())!=0)) throw QuotationSearchException.inconsistent();
   int count=request.rooms().size();
   Map<Integer,Candidate> byId=group.stream().collect(Collectors.toMap(Candidate::offeringId,c->c));
   List<Candidate> selected=new ArrayList<>();
   Map<Integer,Integer> demand=new HashMap<>();
   List<String> occupancyErrors=new ArrayList<>();
   for(int r=0;r<count;r++) {
    // Primary results use one room type; customized results use exactly the submitted offering IDs.
    Candidate c=selection==null?parent:byId.get(selection.rooms().get(r).offeringId());
    if(c==null) throw QuotationSearchException.invalid("Selected offering does not belong to the same eligible hotel and approved contract");
    selected.add(c);demand.merge(c.offeringId(),1,Integer::sum);
    int adults=request.rooms().get(r).adults();
    if(adults>c.maxAdults()) occupancyErrors.add("Maximum "+c.maxAdults()+" adults per room. Room "+(r+1)+" requests "+adults+" adults; maximum is "+c.maxAdults()+".");
   }
   List<UnavailabilityReason> reasons=new ArrayList<>();
   if(selected.stream().anyMatch(c->closedOfferingIds.contains(c.offeringId())))
    reasons.add(new UnavailabilityReason(ReasonCode.HOTEL_CLOSED,"Hotel is closed during the requested stay."));
   if(!occupancyErrors.isEmpty()) reasons.add(new UnavailabilityReason(ReasonCode.MAX_ADULT_OCCUPANCY,String.join(" ",occupancyErrors)));
   for(Candidate c:group) {
    int requested=demand.getOrDefault(c.offeringId(),0),remaining=available.get(c.offeringId());
    if(requested>remaining) reasons.add(new UnavailabilityReason(ReasonCode.INSUFFICIENT_INVENTORY,
     "Only "+remaining+(remaining==1?" room available; ":" rooms available; ")+requested+" requested."));
   }
   // Pricing always follows the same explicit offerings, including any non-bookable indicative total.
   List<BigDecimal> roomPrices=new ArrayList<>();BigDecimal total=new BigDecimal("0.00");
   if(occupancyErrors.isEmpty()) for(int r=0;r<count;r++) {
    Candidate c=selected.get(r);BigDecimal price=pricing.total(tariffs.get(c.offeringId()),request.rooms().get(r).adults(),c.markup());
    roomPrices.add(price);total=total.add(price);
   }
   if(!reasons.isEmpty()) {
    unavailable.add(new UnavailableOption(parent.hotelId(),parent.hotelName(),parent.city(),parent.contractId(),parent.contractCode(),parent.currency(),
     AvailabilityStatus.NOT_AVAILABLE,group.stream().map(c->new UnavailableOffering(c.offeringId(),c.roomTypeId(),c.roomTypeName(),c.maxAdults(),available.get(c.offeringId()))).toList(),List.copyOf(reasons),occupancyErrors.isEmpty()?total:null));
    continue;
   }
   List<RoomAllocation> allocated=new ArrayList<>();
   for(int r=0;r<count;r++) {
    Candidate c=selected.get(r);int adults=request.rooms().get(r).adults(),availableRooms=available.get(c.offeringId()),used=demand.get(c.offeringId());
    allocated.add(new RoomAllocation(r+1,adults,c.offeringId(),c.roomTypeId(),c.roomTypeName(),c.contractedRooms(),availableRooms,used,availableRooms-used,roomPrices.get(r),pricing.breakdown(tariffs.get(c.offeringId()),adults,c.markup())));
   }
   results.add(new Quotation(parent.hotelId(),parent.hotelName(),parent.city(),parent.contractId(),parent.contractCode(),parent.currency(),request.checkIn(),request.checkOut(),nights,List.copyOf(allocated),total,"INFORMATIONAL"));
  }
  results.sort(Comparator.comparing(Quotation::currencyCode).thenComparing(Quotation::totalPrice).thenComparing(Quotation::hotelId).thenComparing(Quotation::contractId));
  unavailable.sort(Comparator.comparing(UnavailableOption::hotelName).thenComparingInt(UnavailableOption::hotelId).thenComparingInt(UnavailableOption::contractId));
  var result=response(request,today,nights,results,unavailable);
  return new QuotationSearchResponse(result.businessDate(),result.checkIn(),result.checkOut(),result.nights(),result.quotations(),result.matchingOptions(),result.truncated(),
   selection==null?"INDIVIDUAL_ROOM_TYPES":"EXPLICIT_SELECTION",result.notice(),result.unavailableOptions(),result.matchingUnavailableOptions(),result.unavailableTruncated(),choices);
 }
 private QuotationSearchResponse response(QuotationSearchRequest r,LocalDate today,int nights,List<Quotation> quotes,List<UnavailableOption> unavailable) {
  return new QuotationSearchResponse(today,r.checkIn(),r.checkOut(),nights,List.copyOf(quotes.subList(0,Math.min(MAX_RESULTS,quotes.size()))),quotes.size(),quotes.size()>MAX_RESULTS,"INDIVIDUAL_ROOM_TYPES",
   "Informational quotation only. Prices and availability must be rechecked atomically at reservation confirmation. Available options are shown first, ordered by currency, then total price.",
   List.copyOf(unavailable.subList(0,Math.min(MAX_RESULTS,unavailable.size()))),unavailable.size(),unavailable.size()>MAX_RESULTS);
 }
 private void validate(QuotationSearchRequest r,LocalDate today) {
  if(r==null) throw QuotationSearchException.invalid("Search details are required");
  var violations=validator.validate(r);
  if(!violations.isEmpty()) throw QuotationSearchException.invalid(violations.stream().map(v->v.getPropertyPath()+": "+v.getMessage()).sorted().collect(Collectors.joining("; ")));
  long nights=ChronoUnit.DAYS.between(r.checkIn(),r.checkOut());
  if(r.checkIn().isBefore(today)) throw QuotationSearchException.invalid("Check-in cannot be before today's business date");
  if(nights<1 || nights>MAX_NIGHTS) throw QuotationSearchException.invalid("Stay must be between 1 and 31 occupied nights");
  if(r.checkIn().isAfter(today.plusDays(MAX_ADVANCE_DAYS))) throw QuotationSearchException.invalid("Check-in must be within 730 days of the business date");
  if(r.hotelId()!=null && !hotels.existsById(r.hotelId())) throw new QuotationSearchException(404,"Selected hotel was not found");
  if(r.roomTypeId()!=null && !rooms.existsById(r.roomTypeId())) throw new QuotationSearchException(404,"Selected room type was not found");
 }
}
