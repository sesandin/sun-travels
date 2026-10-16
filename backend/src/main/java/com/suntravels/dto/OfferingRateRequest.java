package com.suntravels.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record OfferingRateRequest(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=2) BigDecimal ratePerPersonPerNight) {}
