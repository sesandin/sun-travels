package com.suntravels.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReservationRequest(
 @NotNull @Positive @JsonDeserialize(using=StrictSearchInteger.class) Integer customerId,
 @NotNull @Positive @JsonDeserialize(using=StrictSearchInteger.class) Integer hotelId,
 @NotNull @Positive @JsonDeserialize(using=StrictSearchInteger.class) Integer contractId,
 @NotNull @JsonDeserialize(using=StrictSearchDate.class) LocalDate checkIn,
 @NotNull @JsonDeserialize(using=StrictSearchDate.class) LocalDate checkOut,
 @NotNull @Size(min=1,max=8) List<@NotNull @Valid Room> rooms,
 @NotNull @DecimalMin("0.01") @Digits(integer=14,fraction=2) BigDecimal acceptedTotal,
 @NotNull @Pattern(regexp="[A-Z]{3}") String acceptedCurrency) {
 public record Room(
  @NotNull @Positive @JsonDeserialize(using=StrictSearchInteger.class) Integer offeringId,
  @NotNull @Min(1) @JsonDeserialize(using=StrictSearchInteger.class) Integer adults) {}
}
