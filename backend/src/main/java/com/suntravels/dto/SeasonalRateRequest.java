package com.suntravels.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SeasonalRateRequest(
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validTo,
        @NotNull @DecimalMin( value = "0", inclusive = false ) @Digits( integer = 8, fraction = 2 ) BigDecimal ratePerPersonPerNight
)
{
    // Checks that the start date is not after the end date.
    @AssertTrue( message = "Start date must be on or before end date" )
    public boolean isDateRangeValid()
    {
        return validFrom == null || validTo == null || !validFrom.isAfter( validTo );
    }
}
