package com.suntravels.service;

import com.suntravels.dto.*;
import com.suntravels.dto.ReservationResponse.*;
import com.suntravels.entity.*;
import com.suntravels.exception.ReservationException;
import com.suntravels.repository.*;
import com.suntravels.repository.QuotationSearchRepository.*;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true)
public class ReservationService {
 private final ReservationRepository reservations;
 private final HotelRepository hotels;
 private final CustomerRepository customers;
 private final ContractRepository contracts;
 private final OfferingRepository offerings;
 private final QuotationSearchRepository search;
 private final NightlyQuotationPricing pricing;
 private final StayAvailability availability;
 private final Validator validator;
 private final Clock clock;
 public ReservationService(ReservationRepository reservations,HotelRepository hotels,CustomerRepository customers,
  QuotationSearchRepository search,NightlyQuotationPricing pricing,StayAvailability availability,Validator validator,Clock clock,
  ContractRepository contracts,OfferingRepository offerings) {
  this.reservations=reservations;this.hotels=hotels;this.customers=customers;this.search=search;
  this.pricing=pricing;this.availability=availability;this.validator=validator;this.clock=clock;
  this.contracts=contracts;this.offerings=offerings;
 }
 public ReservationResponse detail(int id) { return reservations.detail(id); }
 public Page list(String query,String status,int page,int size) { return list(query,status,page,size,null,null); }
 public Page list(String query,String status,int page,int size,LocalDate stayFrom,LocalDate stayTo) { return list(query,status,page,size,stayFrom,stayTo,""); }

 public Page list(String query,String status,int page,int size,LocalDate stayFrom,LocalDate stayTo,String stayPeriod) { return reservations.list(query.trim(),status,page,size,stayFrom,stayTo,stayPeriod); }

 // READ_COMMITTED makes all JDBC revalidation reads fresh AFTER the shared hotel mutex is acquired.
 // Contract lifecycle uses this hotel mutex; offering/seasonal maintenance also uses the contract lock.
 @Transactional(isolation=Isolation.READ_COMMITTED,timeout=20)
 public ReservationResponse confirm(ReservationRequest request) {
  validate(request);
  hotels.findForUpdate(request.hotelId()).orElseThrow(ReservationException::unavailable);
  if(!customers.existsById(request.customerId())) throw new ReservationException(404,"CUSTOMER_NOT_FOUND","Select an existing customer");
  var duplicate=reservations.recentDuplicate(request);
  if(duplicate.isPresent()) return duplicate.get();
  if(!contracts.findHotelId(request.contractId()).filter(request.hotelId()::equals).isPresent()) throw ReservationException.unavailable();
  var lockedContract=contracts.findForUpdate(request.contractId()).orElseThrow(ReservationException::unavailable);
  if(!lockedContract.getHotel().getHotelId().equals(request.hotelId())) throw ReservationException.unavailable();
  // Ascending offering IDs match approval's order and coordinate with existing rate/season writes.
  offerings.findAllForUpdate(request.contractId());
  var query=new QuotationSearchRequest(request.checkIn(),request.checkOut(),request.rooms().stream()
   .map(r->new QuotationSearchRequest.RequestedRoom(r.adults())).toList(),null,request.hotelId(),null);
  var candidates=search.candidates(query);
  if(candidates.size()>QuotationSearchRepository.MAX_OFFERINGS) throw ReservationException.unavailable();
  Map<Integer,Candidate> selected=new TreeMap<>();
  for(var room:request.rooms()) {
   var c=candidates.stream().filter(v->v.offeringId()==room.offeringId() && v.contractId()==request.contractId()).findFirst().orElseThrow(ReservationException::unavailable);
   if(c.hotelId()!=request.hotelId() || room.adults()>c.maxAdults()) throw ReservationException.unavailable();
   if(!validator.validate(new ContractRequest(c.hotelId(),c.validFrom(),c.validTo(),c.markup(),c.currency())).isEmpty()
    || !validator.validate(new OfferingRequest(c.roomTypeId(),c.baseRate(),c.contractedRooms(),c.maxAdults(),OfferingStatus.ENABLED)).isEmpty()) throw ReservationException.unavailable();
   selected.put(c.offeringId(),c);
  }
  Set<Integer> ids=selected.keySet();
  if(search.inconsistent(ids,Set.of(request.contractId()),Set.of(request.hotelId()),LocalDate.now(clock))) throw ReservationException.unavailable();
  var seasons=search.seasons(ids);var bookings=search.bookedRooms(ids,request.checkIn(),request.checkOut());
  if(seasons.size()>QuotationSearchRepository.MAX_SEASONS || bookings.size()>QuotationSearchRepository.MAX_BOOKED_ITEMS) throw ReservationException.unavailable();
  var bySeason=seasons.stream().collect(Collectors.groupingBy(Season::offeringId));
  var byBooking=bookings.stream().collect(Collectors.groupingBy(BookedRoom::offeringId));
  var requested=request.rooms().stream().collect(Collectors.groupingBy(ReservationRequest.Room::offeringId,Collectors.counting()));
  Map<Integer,List<NightlyQuotationPricing.Tariff>> tariffs=new HashMap<>();
  for(var c:selected.values()) {
   if(availability.available(c.offeringId(),c.contractedRooms(),byBooking.getOrDefault(c.offeringId(),List.of()),request.checkIn(),request.checkOut())<requested.get(c.offeringId())) throw ReservationException.unavailable();
   tariffs.put(c.offeringId(),pricing.tariffs(c,bySeason.getOrDefault(c.offeringId(),List.of()),request.checkIn(),request.checkOut()));
  }
  var parent=selected.values().iterator().next();
  if(selected.values().stream().anyMatch(c->!c.currency().equals(parent.currency()) || c.markup().compareTo(parent.markup())!=0)) throw ReservationException.unavailable();
  var quoted=request.rooms().stream().map(room->{
   var c=selected.get(room.offeringId());var nights=tariffs.get(c.offeringId());
   return new QuotedRoom(c.offeringId(),c.roomTypeName(),room.adults(),pricing.total(nights,room.adults(),c.markup()),
    nights.stream().map(t->new Night(t.date(),pricing.nightly(t,room.adults(),c.markup()))).toList());
  }).toList();
  var total=quoted.stream().map(QuotedRoom::totalPrice).reduce(new BigDecimal("0.00"),BigDecimal::add);
  var current=new Quote(parent.hotelId(),parent.hotelName(),parent.contractId(),request.checkIn(),request.checkOut(),
   ChronoUnit.DAYS.between(request.checkIn(),request.checkOut()),parent.currency(),total,quoted);
  if(!parent.currency().equals(request.acceptedCurrency()) || total.compareTo(request.acceptedTotal())!=0)
   throw new ReservationException(409,"PRICE_CHANGED","The selling quotation changed. Review and explicitly accept the current quotation before confirming again.",current);

  int reservation=reservations.insert("insert into reservation(customer_id,check_in_date,check_out_date,status,currency_code) values (?,?,?,'CONFIRMED',?)",request.customerId(),request.checkIn(),request.checkOut(),parent.currency());
  for(var room:request.rooms()) {
   var c=selected.get(room.offeringId());var nights=tariffs.get(c.offeringId());
   boolean uniform=nights.stream().allMatch(t->t.rate().compareTo(nights.get(0).rate())==0);
   // Single-rate fields describe one occupied night, as in the existing reservation fixtures.
   // Mixed seasons have no truthful single rate/selling price; nightly snapshots are authoritative.
   int item=reservations.insert("insert into reservation_item(reservation_id,offering_id,number_of_adults,booked_rate_per_person_per_night,booked_markup_percentage,booked_selling_price) values (?,?,?,?,?,?)",
    reservation,c.offeringId(),room.adults(),uniform?nights.get(0).rate():null,c.markup(),uniform?pricing.nightly(nights.get(0),room.adults(),c.markup()):null);
   for(var night:nights) reservations.night(item,night.date(),night.rate(),c.markup(),pricing.nightly(night,room.adults(),c.markup()));
  }
  return reservations.detail(reservation);
 }
 private void validate(ReservationRequest request) {
  if(request==null || !validator.validate(request).isEmpty()) throw new ReservationException(400,"INVALID_REQUEST","Check customer, dates, rooms and accepted quotation");
  var today=LocalDate.now(clock);long nights=ChronoUnit.DAYS.between(request.checkIn(),request.checkOut());
  if(request.checkIn().isBefore(today) || request.checkIn().isAfter(today.plusDays(QuotationSearchService.MAX_ADVANCE_DAYS)) || nights<1 || nights>QuotationSearchService.MAX_NIGHTS)
   throw new ReservationException(400,"INVALID_DATES","Use a future or current check-in within 730 days and a stay of 1–31 nights");
 }
 @Transactional(isolation=Isolation.READ_COMMITTED,timeout=20)
 public ReservationResponse cancel(int id) {
  // Existing historical multi-hotel reservations, if any, use ascending hotel lock order.
  for(int hotel:reservations.hotelIds(id)) hotels.findForUpdate(hotel).orElseThrow(ReservationException::unavailable);
  String status=reservations.lockStatus(id);
  if(!status.equals("CONFIRMED") && !status.equals("CANCELLED")) throw new ReservationException(409,"INVALID_STATUS","Only CONFIRMED reservations can be cancelled");
  if(status.equals("CONFIRMED")) reservations.cancel(id);
  return reservations.detail(id);
 }
}
