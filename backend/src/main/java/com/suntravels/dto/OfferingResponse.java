package com.suntravels.dto;

import com.suntravels.entity.OfferingStatus;

import java.math.BigDecimal;

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
)
{
}
