package com.suntravels.dto;
import com.suntravels.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
/** Administrative commercial terms, not available room inventory or selling prices. */
public record OfferingDirectoryResponse(Integer offeringId, Integer hotelId, String hotelName,
        HotelStatus hotelStatus, Integer contractId, String contractCode, ContractStatus contractStatus,
        LocalDate validFrom, LocalDate validTo, Integer roomTypeId, String roomTypeName,
        BigDecimal ratePerPersonPerNight, String currencyCode, Integer contractedRoomCount,
        Integer maxAdults, OfferingStatus status, long seasonalRateCount) {}
