package com.suntravels.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.suntravels.entity.OfferingStatus;
public record OfferingRateRequest(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=2) BigDecimal ratePerPersonPerNight) {}
