package com.suntravels.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;
import java.util.List;
public record QuotationSearchRequest(@NotNull @JsonDeserialize(using=StrictSearchDate.class) LocalDate checkIn, @NotNull @JsonDeserialize(using=StrictSearchDate.class) LocalDate checkOut,
 @NotNull @Size(min=1,max=8) List<@NotNull @Valid RequestedRoom> rooms,
 @Size(max=100) String city,
 @Positive @JsonDeserialize(using=StrictSearchInteger.class) Integer hotelId,
 @Positive @JsonDeserialize(using=StrictSearchInteger.class) Integer roomTypeId) {
 public record RequestedRoom(@NotNull @Min(1) @Max(10) @JsonDeserialize(using=StrictSearchInteger.class) Integer adults) {}
}
