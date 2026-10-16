package com.suntravels.service;
import com.suntravels.dto.*;
import com.suntravels.entity.*;
import com.suntravels.exception.*;
import com.suntravels.repository.*;
import jakarta.validation.Validator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class OfferingService {
 private final ContractRepository contracts;
 private final OfferingRepository offerings;
 private final RoomTypeRepository rooms;
 private final SeasonalRateRepository seasons;
 private final Validator validator;
 public OfferingService(ContractRepository contracts,OfferingRepository offerings,RoomTypeRepository rooms,SeasonalRateRepository seasons,Validator validator) {
  this.contracts=contracts; this.offerings=offerings; this.rooms=rooms; this.seasons=seasons; this.validator=validator;
 }
 /** Initial full administrative listing: two queries independent of row count.
  * Client paging bounds rendering, not database/result-set size. */
 public List<OfferingDirectoryResponse> directory() {
  var rows = offerings.findDirectoryOfferings();
  if (rows.isEmpty()) return List.of();
  java.util.Map<Integer,Long> counts = new java.util.HashMap<>();
  seasons.countPeriodsByOffering().forEach(count -> counts.put(count.getOfferingId(), count.getPeriodCount()));
  return rows.stream().map(o -> {
   Contract c = o.getContract(); Hotel h = c.getHotel();
   return new OfferingDirectoryResponse(o.getOfferingId(), h.getHotelId(), h.getName(), h.getStatus(),
    c.getContractId(), c.getContractCode(), c.getStatus(), c.getValidFrom(), c.getValidTo(),
    o.getRoomType().getRoomTypeId(), o.getRoomType().getName(), o.getRatePerPersonPerNight(),
    c.getCurrencyCode(), o.getContractedRoomCount(), o.getMaxAdults(), o.getStatus(),
    counts.getOrDefault(o.getOfferingId(), 0L));
  }).toList();
 }
 public List<OfferingResponse> list(Integer contractId) {
  contracts.findById(contractId).orElseThrow(() -> OfferingException.missing("Contract",contractId));
  return offerings.findByContractContractIdOrderByOfferingId(contractId).stream().map(this::response).toList();
 }
 public OfferingResponse get(Integer id) { return response(find(id)); }
 public List<SeasonalRateResponse> seasonalRates(Integer id) {
  find(id); return seasons.findByOfferingOfferingIdOrderByValidFrom(id).stream().map(this::response).toList();
 }
 // Read-only nightly hotelier-rate selection for the future quotation module; no snapshot writes.
 public java.math.BigDecimal effectiveRate(Integer id, java.time.LocalDate occupiedDate) {
  ContractRoomOffering o=find(id);
  if(occupiedDate==null || occupiedDate.isBefore(o.getContract().getValidFrom()) || occupiedDate.isAfter(o.getContract().getValidTo()))
   throw OfferingException.invalid("Occupied date must be within the contract validity period");
  var matches=seasons.findByOfferingOfferingIdOrderByValidFrom(id).stream()
   .filter(r -> !occupiedDate.isBefore(r.getValidFrom()) && !occupiedDate.isAfter(r.getValidTo())).toList();
  if(matches.size()>1) throw OfferingException.conflict("Overlapping persisted seasonal rates require correction");
  return matches.isEmpty()?o.getRatePerPersonPerNight():matches.get(0).getRatePerPersonPerNight();
 }
 @Transactional public OfferingResponse create(Integer contractId,OfferingRequest request) {
  Contract c=contracts.findForUpdate(contractId).orElseThrow(() -> OfferingException.missing("Contract",contractId));
  require(c,ContractStatus.DRAFT); validate(request); duplicate(contractId,request.roomTypeId(),0);
  RoomType room=room(request.roomTypeId());
  ContractRoomOffering o=new ContractRoomOffering(); o.setContract(c); apply(o,request,room);
  return response(offerings.saveAndFlush(o));
 }
 @Transactional public OfferingResponse update(Integer id,OfferingRequest request) {
  ContractRoomOffering o=locked(id); require(o.getContract(),ContractStatus.DRAFT); validate(request);
  duplicate(o.getContract().getContractId(),request.roomTypeId(),id); RoomType room=room(request.roomTypeId());
  apply(o,request,room); return response(offerings.saveAndFlush(o));
 }
 @Transactional public OfferingResponse rate(Integer id,OfferingRateRequest request) {
  ContractRoomOffering o=locked(id); require(o.getContract(),ContractStatus.APPROVED); validate(request);
  o.setRatePerPersonPerNight(request.ratePerPersonPerNight()); return response(offerings.saveAndFlush(o));
 }
 @Transactional public OfferingResponse status(Integer id,OfferingStatusRequest request) {
  ContractRoomOffering o=locked(id);
  if(o.getContract().getStatus()==ContractStatus.TERMINATED) throw OfferingException.conflict("Offering status cannot change under a TERMINATED contract");
  validate(request);
  o.setStatus(request.status()); return response(offerings.saveAndFlush(o));
 }
 @Transactional public SeasonalRateResponse createSeason(Integer id,SeasonalRateRequest request) { return saveSeason(id,null,request); }
 @Transactional public SeasonalRateResponse updateSeason(Integer id,Integer rateId,SeasonalRateRequest request) { return saveSeason(id,rateId,request); }
 private SeasonalRateResponse saveSeason(Integer id,Integer rateId,SeasonalRateRequest request) {
  ContractRoomOffering o=locked(id); maintainable(o.getContract()); validate(request);
  OfferingSeasonalRate s=rateId==null ? new OfferingSeasonalRate() : seasons.findBySeasonalRateIdAndOfferingOfferingId(rateId,id)
   .orElseThrow(() -> OfferingException.missing("Seasonal rate for this offering",rateId));
  Contract c=o.getContract();
  if(request.validFrom().isBefore(c.getValidFrom()) || request.validTo().isAfter(c.getValidTo()))
   throw OfferingException.invalid("Seasonal dates must remain within the contract validity period");
  if(!seasons.overlaps(id,rateId==null?0:rateId,request.validFrom(),request.validTo()).isEmpty())
   throw OfferingException.conflict("Seasonal period overlaps another period for this offering (dates are inclusive)");
  if(rateId==null) s.setOffering(o);
  s.setValidFrom(request.validFrom()); s.setValidTo(request.validTo()); s.setRatePerPersonPerNight(request.ratePerPersonPerNight());
  return response(seasons.saveAndFlush(s));
 }
 // Future hotel coordination must use HOTEL -> CONTRACT -> OFFERING -> SEASONAL RATE.
 // Scalar lookup cannot retain stale lifecycle state before acquiring the contract lock.
 private ContractRoomOffering locked(Integer id) {
  Integer contractId=offerings.findContractId(id).orElseThrow(() -> OfferingException.missing("Offering",id));
  contracts.findForUpdate(contractId).orElseThrow(() -> OfferingException.missing("Contract",contractId));
  return offerings.findForUpdate(id).orElseThrow(() -> OfferingException.missing("Offering",id));
 }
 private ContractRoomOffering find(Integer id) { return offerings.findById(id).orElseThrow(() -> OfferingException.missing("Offering",id)); }
 private RoomType room(Integer id) { return rooms.findById(id).orElseThrow(() -> OfferingException.missing("Room type",id)); }
 private void duplicate(Integer contractId,Integer roomId,Integer excluded) {
  if(offerings.existsByContractContractIdAndRoomTypeRoomTypeIdAndOfferingIdNot(contractId,roomId,excluded)) throw OfferingException.conflict("Room type already appears in this contract");
 }
 private void require(Contract c,ContractStatus status) { if(c.getStatus()!=status) throw OfferingException.conflict("This operation requires a "+status+" contract"); }
 private void maintainable(Contract c) { if(c.getStatus()!=ContractStatus.DRAFT && c.getStatus()!=ContractStatus.APPROVED) throw OfferingException.conflict("Offering and seasonal maintenance requires a DRAFT or APPROVED contract"); }
 private void validate(Object request) {
  if(request==null) throw OfferingException.invalid("Details are required");
  var violations=validator.validate(request);
  if(!violations.isEmpty()) throw OfferingException.invalid(violations.stream().map(v -> v.getPropertyPath()+": "+v.getMessage()).sorted().reduce((a,b)->a+"; "+b).orElseThrow());
 }
 private void apply(ContractRoomOffering o,OfferingRequest r,RoomType room) {
  o.setRoomType(room); o.setRatePerPersonPerNight(r.ratePerPersonPerNight()); o.setContractedRoomCount(r.contractedRoomCount()); o.setMaxAdults(r.maxAdults()); o.setStatus(r.status());
 }
 private OfferingResponse response(ContractRoomOffering o) { return new OfferingResponse(o.getOfferingId(),o.getContract().getContractId(),o.getRoomType().getRoomTypeId(),o.getRoomType().getName(),o.getRatePerPersonPerNight(),o.getContractedRoomCount(),o.getMaxAdults(),o.getStatus(),o.getContract().getCurrencyCode()); }
 private SeasonalRateResponse response(OfferingSeasonalRate s) { return new SeasonalRateResponse(s.getSeasonalRateId(),s.getOffering().getOfferingId(),s.getValidFrom(),s.getValidTo(),s.getRatePerPersonPerNight()); }
}
