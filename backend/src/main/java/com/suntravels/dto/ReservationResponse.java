package com.suntravels.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reservation details include booked commercial snapshots for authenticated staff.
 * Informational quotes continue to expose selling prices only.
 */
public record ReservationResponse(
        int reservationId,
        int customerId,
        String customerName,
        String phone,
        String email,
        LocalDate checkIn,
        LocalDate checkOut,
        long nights,
        String status,
        LocalDateTime createdAt,
        String currencyCode,
        BigDecimal totalPrice,
        List<Item> rooms,
        List<OperationalWarning> warnings
)
{
    // Initializes the reservation response and its required dependencies.
    public ReservationResponse(
            int reservationId, int customerId, String customerName, String phone,
            String email, LocalDate checkIn, LocalDate checkOut, long nights, String status,
            LocalDateTime createdAt, String currencyCode, BigDecimal totalPrice, List<Item> rooms )
    {
        this( reservationId, customerId, customerName, phone, email, checkIn, checkOut, nights, status,
                createdAt, currencyCode, totalPrice, rooms, List.of() );
    }

    public enum WarningCode
    {
        HOTEL_INACTIVE,
        CONTRACT_SUSPENDED,
        OFFERING_SUSPENDED
    }

    public record OperationalWarning(
            int hotelId,
            String hotelName,
            WarningCode code,
            String message,
            Integer contractId,
            Integer offeringId,
            String roomTypeName
    )
    {
        // Initializes the reservation response and its required dependencies.
        public OperationalWarning( int hotelId, String hotelName, WarningCode code, String message )
        {
            this( hotelId, hotelName, code, message, null, null, null );
        }
    }

    public record Night(
            LocalDate occupiedDate,
            BigDecimal sellingPrice
    ) implements Serializable
    {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record BookedNight(
            LocalDate occupiedDate,
            BigDecimal sellingPrice,
            BigDecimal bookedRatePerAdult,
            BigDecimal bookedMarkupPercentage
    )
    {
    }

    public record Item(
            int reservationItemId,
            int offeringId,
            int hotelId,
            String hotelName,
            int contractId,
            String roomTypeName,
            int adults,
            BigDecimal totalPrice,
            List<BookedNight> nightlyPrices
    )
    {
    }

    public record RoomSummary(int hotelId, String hotelName, String roomTypeName, int adults) {}

    public record Summary(int reservationId, int customerId, String customerName,
            LocalDate checkIn, LocalDate checkOut, String status, String currencyCode,
            BigDecimal totalPrice, List<RoomSummary> rooms, boolean hasOperationalWarnings) {}

    public record Page(
            List<Summary> reservations,
            int page,
            int size,
            long totalElements
    )
    {
    }

    public record Quote(
            int hotelId,
            String hotelName,
            int contractId,
            LocalDate checkIn,
            LocalDate checkOut,
            long nights,
            String currencyCode,
            BigDecimal totalPrice,
            List<QuotedRoom> rooms
    ) implements Serializable
    {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record QuotedRoom(
            int offeringId,
            String roomTypeName,
            int adults,
            BigDecimal totalPrice,
            List<Night> nightlyPrices
    ) implements Serializable
    {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
