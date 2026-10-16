package com.suntravels.dto;

import java.util.List;

public record QuotationSearchFilters(
        List<HotelOption> hotels,
        List<RoomTypeOption> roomTypes
) {
    public record HotelOption(
            Integer hotelId,
            String name,
            String city
    ) {
    }

    public record RoomTypeOption(
            Integer roomTypeId,
            String name
    ) {
    }
}
