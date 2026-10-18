package com.suntravels.exception;

import com.suntravels.dto.ReservationResponse.Night;
import com.suntravels.dto.ReservationResponse.Quote;
import com.suntravels.dto.ReservationResponse.QuotedRoom;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReservationExceptionTest {
    // Verifies serialization retains the price change quotation and nightly prices.
    @Test
    void serializationRetainsThePriceChangeQuotationAndNightlyPrices() throws IOException, ClassNotFoundException {
        LocalDate checkIn = LocalDate.of(2026, 10, 16);
        BigDecimal sellingPrice = new BigDecimal("115.00");
        var room = new QuotedRoom(7, "Standard", 2, sellingPrice,
                List.of(new Night(checkIn, sellingPrice)));
        var quotation = new Quote(1, "Ocean Hotel", 4, checkIn, checkIn.plusDays(1),
                1, "USD", sellingPrice, List.of(room));
        var original = new ReservationException(409, "PRICE_CHANGED", "Review updated price", quotation);
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        ReservationException restored;
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (ReservationException) input.readObject();
        }

        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getCode(), restored.getCode());
        assertEquals(original.getMessage(), restored.getMessage());
        assertEquals(quotation, restored.getQuotation());
    }
}
