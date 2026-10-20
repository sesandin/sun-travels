package com.suntravels.dto;

import com.suntravels.dto.ReservationRequest.Room;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.util.List;

public record QuotationSelectionRequest(
        @NotNull @Positive @JsonDeserialize( using = StrictSearchInteger.class ) Integer hotelId,

        @NotNull @Positive @JsonDeserialize( using = StrictSearchInteger.class ) Integer contractId,

        @NotNull @JsonDeserialize( using = StrictSearchDate.class ) LocalDate checkIn,

        @NotNull @JsonDeserialize( using = StrictSearchDate.class ) LocalDate checkOut,

        @NotNull @Size( min = 1, max = 8 ) List<@NotNull @Valid Room> rooms
)
{
}
