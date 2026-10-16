package com.suntravels.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import com.suntravels.entity.OfferingStatus;
public record OfferingRequest(@NotNull @Positive Integer roomTypeId, @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=2) BigDecimal ratePerPersonPerNight, @NotNull @Positive Integer contractedRoomCount, @NotNull @Positive Integer maxAdults, @NotNull OfferingStatus status) {}
