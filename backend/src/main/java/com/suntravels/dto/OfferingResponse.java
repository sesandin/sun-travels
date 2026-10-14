package com.suntravels.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.suntravels.entity.OfferingStatus;
public record OfferingResponse(Integer offeringId,Integer contractId,Integer roomTypeId,String roomTypeName,BigDecimal ratePerPersonPerNight,Integer contractedRoomCount,Integer maxAdults,OfferingStatus status,String currencyCode) {}
