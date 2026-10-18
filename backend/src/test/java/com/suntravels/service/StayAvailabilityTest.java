package com.suntravels.service;

import com.suntravels.repository.QuotationSearchRepository.BookedRoom;
import com.suntravels.exception.QuotationSearchException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import static org.junit.jupiter.api.Assertions.*;

class StayAvailabilityTest {
    final StayAvailability inventory = new StayAvailability();
    final LocalDate in = LocalDate.of(2026, 11, 1), out = in.plusDays(3);

    // Creates a booked-room fixture for availability tests.
    static BookedRoom b(int id, int start, int end) {
        LocalDate date = LocalDate.of(2026, 11, 1);
        return new BookedRoom(id, 7, date.plusDays(start), date.plusDays(end), 2);
    }

    // Supplies boundary cases for the parameterized availability test.
    static Stream<Arguments> cases() {
        return Stream.of(Arguments.of(List.of(), 3), Arguments.of(List.of(b(1, 0, 1)), 2),
                Arguments.of(List.of(b(1, 0, 1), b(2, 1, 3)), 2),
                Arguments.of(List.of(b(1, 0, 2), b(2, 1, 3)), 1), Arguments.of(List.of(b(1, -2, 0)), 3),
                Arguments.of(List.of(b(1, 3, 4)), 3), Arguments.of(List.of(b(1, -2, 5)), 2),
                Arguments.of(List.of(b(1, 0, 3), b(2, 0, 3), b(3, 1, 2)), 0));
    }

    // Verifies computes peak not distinct stay intersections.
    @ParameterizedTest
    @MethodSource("cases")
    void computesPeakNotDistinctStayIntersections(List<BookedRoom> rows, int expected) {
        assertEquals(expected, inventory.available(7, 3, rows, in, out));
    }

    // Verifies adults do not multiply inventory consumption.
    @Test
    void adultsDoNotMultiplyInventoryConsumption() {
        assertEquals(2, inventory.available(7, 3, List.of(new BookedRoom(1, 7, in, out, 10)), in, out));
    }

    // Verifies malformed or overbooked data fails closed.
    @Test
    void malformedOrOverbookedDataFailsClosed() {
        for (var rows : List.of(List.of(b(1, 0, 0)), List.of(b(1, 2, 1)), List.of(b(1, 0, 1), b(1, 0, 1)),
                List.of(b(1, 0, 3), b(2, 0, 3), b(3, 0, 3), b(4, 0, 3)),
                List.of(new BookedRoom(1, 7, in, out, 0))))
            assertEquals(409, assertThrows(QuotationSearchException.class,
                    () -> inventory.available(7, 3, rows, in, out)).getStatus());
    }
}
