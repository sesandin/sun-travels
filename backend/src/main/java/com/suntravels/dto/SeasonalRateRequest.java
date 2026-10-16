package com.suntravels.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SeasonalRateRequest(
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validTo,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal ratePerPersonPerNight
) {
    @AssertTrue(message = "Start date must be on or before end date")
    public boolean isDateRangeValid() {
        return validFrom == null || validTo == null || !validFrom.isAfter(validTo);
    }
}
