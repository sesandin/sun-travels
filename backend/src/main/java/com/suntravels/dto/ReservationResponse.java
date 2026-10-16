package com.suntravels.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

/**
 * Public selling prices only; internal hotelier rates and markup never leave this API.
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
) {
    public ReservationResponse(int reservationId, int customerId, String customerName, String phone,
            String email, LocalDate checkIn, LocalDate checkOut, long nights, String status,
            LocalDateTime createdAt, String currencyCode, BigDecimal totalPrice, List<Item> rooms) {
        this(reservationId, customerId, customerName, phone, email, checkIn, checkOut, nights, status,
                createdAt, currencyCode, totalPrice, rooms, List.of());
    }

    public enum WarningCode {
        HOTEL_INACTIVE,
        HOTEL_CLOSURE,
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
    ) {
        public OperationalWarning(int hotelId, String hotelName, WarningCode code, String message) {
            this(hotelId, hotelName, code, message, null, null, null);
        }
    }

    public record Night(
            LocalDate occupiedDate,
            BigDecimal sellingPrice
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
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
            List<Night> nightlyPrices
    ) {
    }

    public record Page(
            List<ReservationResponse> reservations,
            int page,
            int size,
            long totalElements
    ) {
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
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record QuotedRoom(
            int offeringId,
            String roomTypeName,
            int adults,
            BigDecimal totalPrice,
            List<Night> nightlyPrices
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
