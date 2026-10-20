package com.suntravels.dto;

import com.suntravels.entity.OfferingStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OfferingRequest(
        @NotNull @Positive Integer roomTypeId,
        @NotNull @DecimalMin( value = "0", inclusive = false ) @Digits( integer = 8, fraction = 2 ) BigDecimal ratePerPersonPerNight,
        @NotNull @Positive Integer contractedRoomCount,
        @NotNull @Positive Integer maxAdults,
        @NotNull OfferingStatus status
)
{
}
