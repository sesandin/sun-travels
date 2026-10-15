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
 public static final int MAX_ROOMS=8,MAX_ADULTS=10,MAX_NIGHTS=31,MAX_ADVANCE_DAYS=730,MAX_RESULTS=20;
 public static final String POLICY="LOWEST_TOTAL_PER_CONTRACT";
 private final QuotationSearchRepository data; private final HotelRepository hotels; private final RoomTypeRepository rooms;
 private final Validator validator; private final Clock clock; private final NightlyQuotationPricing pricing;
 private final StayAvailability inventory; private final RoomAllocationEngine allocator;
 public QuotationSearchService(QuotationSearchRepository data,HotelRepository hotels,RoomTypeRepository rooms,Validator validator,Clock clock,NightlyQuotationPricing pricing,StayAvailability inventory,RoomAllocationEngine allocator) {
  this.data=data;this.hotels=hotels;this.rooms=rooms;this.validator=validator;this.clock=clock;this.pricing=pricing;this.inventory=inventory;this.allocator=allocator;
 }
 public Limits limits() { return new Limits(LocalDate.now(clock),MAX_ROOMS,MAX_ADULTS,MAX_NIGHTS,MAX_ADVANCE_DAYS,MAX_RESULTS,POLICY); }
 public QuotationSearchFilters filters() {
  var hotelOptions=hotels.findAll(Sort.by(Sort.Order.asc("name"),Sort.Order.asc("hotelId"))).stream()
   .filter(h->h.getStatus()==HotelStatus.ACTIVE)
   .map(h->new QuotationSearchFilters.HotelOption(h.getHotelId(),h.getName(),h.getCity())).toList();
  var roomOptions=rooms.findAll(Sort.by(Sort.Order.asc("name"),Sort.Order.asc("roomTypeId"))).stream()
   .map(r->new QuotationSearchFilters.RoomTypeOption(r.getRoomTypeId(),r.getName())).toList();
  return new QuotationSearchFilters(hotelOptions,roomOptions);
 }
 public QuotationSearchResponse search(QuotationSearchRequest request) {
  LocalDate today=LocalDate.now(clock); validate(request,today);
  List<Candidate> candidates=data.candidates(request);
  if(candidates.size()>QuotationSearchRepository.MAX_OFFERINGS) throw QuotationSearchException.tooBroad();
  int nights=(int)ChronoUnit.DAYS.between(request.checkIn(),request.checkOut());
  if(candidates.isEmpty()) return response(request,today,nights,List.of());
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
  List<Quotation> results=new ArrayList<>();
  Map<Integer,List<Candidate>> groups=candidates.stream().collect(Collectors.groupingBy(Candidate::contractId,TreeMap::new,Collectors.toList()));
  for(List<Candidate> group:groups.values()) {
   group.sort(Comparator.comparingInt(Candidate::offeringId)); Candidate parent=group.get(0);
   if(group.stream().anyMatch(c->c.hotelId()!=parent.hotelId() || !c.currency().equals(parent.currency()) || c.markup().compareTo(parent.markup())!=0)) throw QuotationSearchException.inconsistent();
   int count=request.rooms().size(); BigDecimal[][] costs=new BigDecimal[count][group.size()]; int[] capacities=new int[group.size()];
   for(int o=0;o<group.size();o++) {
    Candidate c=group.get(o); capacities[o]=available.get(c.offeringId());
    for(int r=0;r<count;r++) if(request.rooms().get(r).adults()<=c.maxAdults()) costs[r][o]=pricing.total(tariffs.get(c.offeringId()),request.rooms().get(r).adults(),c.markup());
   }
   Optional<int[]> allocation=allocator.allocate(costs,capacities); if(allocation.isEmpty()) continue;
   int[] assigned=allocation.orElseThrow(),used=new int[group.size()]; for(int o:assigned) used[o]++;
   List<RoomAllocation> allocated=new ArrayList<>(); BigDecimal total=new BigDecimal("0.00");
   for(int r=0;r<count;r++) {
    int o=assigned[r],adults=request.rooms().get(r).adults();Candidate c=group.get(o);BigDecimal price=costs[r][o];total=total.add(price);
    allocated.add(new RoomAllocation(r+1,adults,c.offeringId(),c.roomTypeId(),c.roomTypeName(),c.contractedRooms(),capacities[o],used[o],capacities[o]-used[o],price,pricing.breakdown(tariffs.get(c.offeringId()),adults,c.markup())));
   }
   results.add(new Quotation(parent.hotelId(),parent.hotelName(),parent.city(),parent.contractId(),parent.contractCode(),parent.currency(),request.checkIn(),request.checkOut(),nights,List.copyOf(allocated),total,"INFORMATIONAL"));
  }
  results.sort(Comparator.comparing(Quotation::currencyCode).thenComparing(Quotation::totalPrice).thenComparing(Quotation::hotelId).thenComparing(Quotation::contractId));
  return response(request,today,nights,results);
 }
 private QuotationSearchResponse response(QuotationSearchRequest r,LocalDate today,int nights,List<Quotation> quotes) {
  return new QuotationSearchResponse(today,r.checkIn(),r.checkOut(),nights,List.copyOf(quotes.subList(0,Math.min(MAX_RESULTS,quotes.size()))),quotes.size(),quotes.size()>MAX_RESULTS,POLICY,"Informational quotation only. Prices and availability must be rechecked atomically at reservation confirmation. Results are ordered by currency, then total price.");
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
