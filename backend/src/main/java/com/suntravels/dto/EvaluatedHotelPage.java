package com.suntravels.dto;

import com.suntravels.dto.QuotationSearchResponse.AvailabilityStatus;
import com.suntravels.dto.QuotationSearchResponse.Quotation;
import com.suntravels.dto.QuotationSearchResponse.SelectionOffering;
import com.suntravels.dto.QuotationSearchResponse.UnavailableOption;
import java.time.LocalDate;
import java.util.List;

/** One evaluated candidate batch, ordered by hotel ID. */
public record EvaluatedHotelPage(
        LocalDate businessDate, LocalDate checkIn, LocalDate checkOut, int nights,
        List<Hotel> hotels, Integer nextAfterHotelId )
{
    /** Filter after evaluation; continuation always follows the last candidate, not the last visible hotel. */
    public EvaluatedHotelPage availableOnly()
    {
        var availableHotels = hotels.stream().map( hotel -> new Hotel(
                hotel.hotelId(), hotel.hotelName(), hotel.city(), hotel.offerings().stream()
                .filter( o -> o.availabilityStatus() == AvailabilityStatus.AVAILABLE ).toList() ) )
                .filter( hotel -> !hotel.offerings().isEmpty() ).toList();
        return new EvaluatedHotelPage( businessDate, checkIn, checkOut, nights, availableHotels, nextAfterHotelId );
    }

    public record Hotel(int hotelId, String hotelName, String city, List<Offering> offerings) {}

    /** Each quotation uses this offering for ALL requested rooms. No automatic mixing.
     * Room choices retain individual prices for a later explicit mixed-room selection.
     * Unavailable options retain existing reasons and indicative prices where applicable.
     */
    public record Offering(
            int offeringId, int roomTypeId, int contractedRooms,
            SelectionOffering roomChoices, AvailabilityStatus availabilityStatus,
            Quotation quotation, UnavailableOption unavailableOption, String roomDescription )
    {
        public Offering( int offeringId, int roomTypeId, int contractedRooms,
                         SelectionOffering roomChoices, AvailabilityStatus availabilityStatus,
                         Quotation quotation, UnavailableOption unavailableOption )
        {
            this( offeringId, roomTypeId, contractedRooms, roomChoices, availabilityStatus,
                    quotation, unavailableOption, null );
        }
    }
}
