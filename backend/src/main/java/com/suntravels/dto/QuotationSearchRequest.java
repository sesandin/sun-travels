package com.suntravels.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.util.List;

public record QuotationSearchRequest(
        @NotNull @JsonDeserialize( using = StrictSearchDate.class ) LocalDate checkIn,
        @NotNull @JsonDeserialize( using = StrictSearchDate.class ) LocalDate checkOut,
        @NotNull @Size( min = 1, max = 8 ) List<@NotNull @Valid RequestedRoom> rooms,
        @Size( max = 100 ) String city,
        @Positive @JsonDeserialize( using = StrictSearchInteger.class ) Integer hotelId,
        @Positive @JsonDeserialize( using = StrictSearchInteger.class ) Integer roomTypeId,
        @Size( max = 100 ) String country
)
{
    // Preserve callers constructing the original request without a country filter.
    public QuotationSearchRequest(LocalDate checkIn, LocalDate checkOut, List<RequestedRoom> rooms,
                                  String city, Integer hotelId, Integer roomTypeId)
    {
        this( checkIn, checkOut, rooms, city, hotelId, roomTypeId, null );
    }
    public record RequestedRoom(@NotNull @Min( 1 ) @JsonDeserialize( using = StrictSearchInteger.class ) Integer adults)
    {
    }
}
