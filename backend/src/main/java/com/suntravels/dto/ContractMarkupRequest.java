package com.suntravels.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ContractMarkupRequest(@NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal markupPercentage) {
}
