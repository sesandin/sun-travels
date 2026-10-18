package com.suntravels.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// Code and status are server-owned. Creation always starts in DRAFT.
public record ContractRequest(
        @NotNull @Positive Integer hotelId,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validTo,
        @NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal markupPercentage,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "Currency must be three uppercase letters") String currencyCode
) {
    // Checks that the start date is not after the end date.
    @AssertTrue(message = "Valid from must not be later than valid to")
    public boolean isDateRangeValid() {
        return validFrom == null || validTo == null || !validFrom.isAfter(validTo);
    }
}
