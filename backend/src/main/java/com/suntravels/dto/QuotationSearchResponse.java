package com.suntravels.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public record QuotationSearchResponse(LocalDate businessDate, LocalDate checkIn, LocalDate checkOut,
 int nights, List<Quotation> quotations, int matchingOptions, boolean truncated,
 String allocationPolicy, String notice, List<UnavailableOption> unavailableOptions,
 int matchingUnavailableOptions, boolean unavailableTruncated, List<SelectionOffering> selectionOfferings) {
 public QuotationSearchResponse(LocalDate businessDate,LocalDate checkIn,LocalDate checkOut,int nights,
  List<Quotation> quotations,int matchingOptions,boolean truncated,String allocationPolicy,String notice) {
  this(businessDate,checkIn,checkOut,nights,quotations,matchingOptions,truncated,allocationPolicy,notice,List.of(),0,false);
 }
 public QuotationSearchResponse(LocalDate businessDate,LocalDate checkIn,LocalDate checkOut,int nights,
  List<Quotation> quotations,int matchingOptions,boolean truncated,String allocationPolicy,String notice,
  List<UnavailableOption> unavailableOptions,int matchingUnavailableOptions,boolean unavailableTruncated) {
  this(businessDate,checkIn,checkOut,nights,quotations,matchingOptions,truncated,allocationPolicy,notice,
   unavailableOptions,matchingUnavailableOptions,unavailableTruncated,List.of());
 }
 public record SelectionOffering(int hotelId,String hotelName,int contractId,int offeringId,String roomTypeName,
  int maxAdults,int availableRoomsForStay,String currencyCode,boolean hotelClosed,List<SelectionRoom> rooms) {}
 public record SelectionRoom(int roomNumber,int adults,BigDecimal totalPrice) {}
 public enum AvailabilityStatus { AVAILABLE, NOT_AVAILABLE }
 public enum ReasonCode { INSUFFICIENT_INVENTORY, MAX_ADULT_OCCUPANCY, HOTEL_CLOSED }
 public record UnavailabilityReason(ReasonCode code,String message) {}
 public record UnavailableOffering(int offeringId,int roomTypeId,String roomTypeName,int maxAdults,int availableRoomsForStay) {}
 public record UnavailableOption(int hotelId,String hotelName,String city,int contractId,String contractCode,
  String currencyCode,AvailabilityStatus availabilityStatus,List<UnavailableOffering> offerings,
  List<UnavailabilityReason> reasons,BigDecimal indicativeTotalPrice) {}
 public record Quotation(Integer hotelId, String hotelName, String city, Integer contractId,
  String contractCode, String currencyCode, LocalDate checkIn, LocalDate checkOut, int nights,
  List<RoomAllocation> rooms, BigDecimal totalPrice, String eligibility, AvailabilityStatus availabilityStatus) {
  public Quotation(Integer hotelId,String hotelName,String city,Integer contractId,String contractCode,
   String currencyCode,LocalDate checkIn,LocalDate checkOut,int nights,List<RoomAllocation> rooms,BigDecimal totalPrice,String eligibility) {
   this(hotelId,hotelName,city,contractId,contractCode,currencyCode,checkIn,checkOut,nights,rooms,totalPrice,eligibility,AvailabilityStatus.AVAILABLE);
  }
 }
 public record RoomAllocation(int roomNumber, int adults, Integer offeringId, Integer roomTypeId,
  String roomTypeName, int contractedRooms, int availableRoomsForStay, int allocatedRoomsForOffering,
  int remainingRoomsAfterAllocation, BigDecimal totalPrice, List<NightlyPrice> nightlyPrices) {}
 public record NightlyPrice(LocalDate occupiedDate, String source, Integer seasonalRateId, BigDecimal sellingPrice) {}
 public record Limits(LocalDate businessDate, int maxRooms, int maxNights,
  int maxAdvanceDays, int maxResults, String allocationPolicy) {}
}
