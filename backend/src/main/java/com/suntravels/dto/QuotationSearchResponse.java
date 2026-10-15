package com.suntravels.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public record QuotationSearchResponse(LocalDate businessDate, LocalDate checkIn, LocalDate checkOut,
 int nights, List<Quotation> quotations, int matchingOptions, boolean truncated,
 String allocationPolicy, String notice) {
 public record Quotation(Integer hotelId, String hotelName, String city, Integer contractId,
  String contractCode, String currencyCode, LocalDate checkIn, LocalDate checkOut, int nights,
  List<RoomAllocation> rooms, BigDecimal totalPrice, String eligibility) {}
 public record RoomAllocation(int roomNumber, int adults, Integer offeringId, Integer roomTypeId,
  String roomTypeName, int contractedRooms, int availableRoomsForStay, int allocatedRoomsForOffering,
  int remainingRoomsAfterAllocation, BigDecimal totalPrice, List<NightlyPrice> nightlyPrices) {}
 public record NightlyPrice(LocalDate occupiedDate, String source, Integer seasonalRateId,
  BigDecimal hotelierRate, BigDecimal markupPercentage, BigDecimal sellingPrice) {}
 public record Limits(LocalDate businessDate, int maxRooms, int maxAdultsPerRoom, int maxNights,
  int maxAdvanceDays, int maxResults, String allocationPolicy) {}
}
