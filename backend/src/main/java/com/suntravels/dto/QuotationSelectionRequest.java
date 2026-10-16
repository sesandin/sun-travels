
package com.suntravels.dto;

import com.suntravels.dto.ReservationRequest.Room;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record QuotationSelectionRequest(
        @NotNull
        @Positive
        @JsonDeserialize(using = StrictSearchInteger.class)
        Integer hotelId,

        @NotNull
        @Positive
        @JsonDeserialize(using = StrictSearchInteger.class)
        Integer contractId,

        @NotNull
        @JsonDeserialize(using = StrictSearchDate.class)
        LocalDate checkIn,

        @NotNull
        @JsonDeserialize(using = StrictSearchDate.class)
        LocalDate checkOut,

        @NotNull
        @Size(min = 1, max = 8)
        List<@NotNull @Valid Room> rooms
) {}
