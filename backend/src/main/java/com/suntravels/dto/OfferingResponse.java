package com.suntravels.dto;

import java.math.BigDecimal;

import com.suntravels.entity.OfferingStatus;

public record OfferingResponse(
        Integer offeringId,
        Integer contractId,
        Integer roomTypeId,
        String roomTypeName,
        BigDecimal ratePerPersonPerNight,
        Integer contractedRoomCount,
        Integer maxAdults,
        OfferingStatus status,
        String currencyCode
) {
}
