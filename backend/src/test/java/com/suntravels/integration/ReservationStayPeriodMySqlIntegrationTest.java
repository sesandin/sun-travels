package com.suntravels.integration;

import com.suntravels.dto.ReservationResponse;
import com.suntravels.repository.ReservationRepository;
import com.suntravels.service.ReservationService;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "suntravels.auth.bootstrap.enabled=false")
@Import(ReservationStayPeriodMySqlIntegrationTest.FixedDate.class)
@Transactional
@EnabledIfEnvironmentVariable(named = "SUNTRAVELS_RESERVATION_MYSQL_TESTS", matches = "true")
class ReservationStayPeriodMySqlIntegrationTest {
    @TestConfiguration
    static class FixedDate {
        @Bean
        @Primary
        Clock stayPeriodClock() {
            return Clock.fixed(Instant.parse("2026-10-15T20:00:00Z"), ZoneId.of("Asia/Colombo"));
        }
    }

    @Autowired
    ReservationService service;
    @Autowired
    ReservationRepository repository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;
    final LocalDate today = LocalDate.of(2026, 10, 16);
    final List<Integer> ids = new ArrayList<>();
    String customerName;
    int customer, octoberUpcoming, ongoing, checkoutToday, november, cancelledUpcoming;

    @BeforeEach
    void fixtures() {
        assertTrue(Objects.requireNonNull(jdbc.queryForObject("select database()", String.class)).endsWith(
                "_test"));
        assertTrue(Objects.requireNonNull(jdbc.queryForObject("select version()", String.class)).startsWith(
                "8."));
        assertEquals(today, LocalDate.now(clock));
        customerName = "Stay period fixture " + UUID.randomUUID();
        customer = repository.insert("insert into customer(name,email) values (?,?)", customerName,
                UUID.randomUUID() + "@example.test");
        octoberUpcoming = add("CONFIRMED", today.plusDays(1), today.plusDays(2));
        ongoing = add("CONFIRMED", today, today.plusDays(1));
        checkoutToday = add("CONFIRMED", today.minusDays(1), today);
        november = add("CONFIRMED", LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 18));
        cancelledUpcoming = add("CANCELLED", today.plusDays(1), today.plusDays(2));
        for (int i = 0; i < 20; i++) add("CONFIRMED", today.minusDays(4), today.minusDays(2));
    }

    int add(String status, LocalDate in, LocalDate out) {
        int id = repository.insert(
                "insert into reservation(customer_id,check_in_date,check_out_date,status,currency_code) values (?,?,?,?,?)",
                customer, in, out, status, "USD");
        ids.add(id);
        return id;
    }

    ReservationResponse.Page list(String status, int page, LocalDate from, LocalDate to, String period) {
        return service.list(customerName, status, page, 10, from, to, period);
    }

    Set<Integer> returned(ReservationResponse.Page page) {
        return new HashSet<>(page.reservations().stream().map(ReservationResponse::reservationId).toList());
    }

    @Test
    void exactOctoberSixteenthBoundariesAreAppliedBySql() {
        var upcoming = list("", 0, null, null, "UPCOMING");
        assertEquals(3, upcoming.totalElements());
        assertEquals(Set.of(octoberUpcoming, november, cancelledUpcoming), returned(upcoming));
        assertEquals(Set.of(ongoing), returned(list("", 0, null, null, "ONGOING")));
        var past = list("", 0, null, null, "PAST");
        assertEquals(21, past.totalElements());
        assertTrue(past.reservations().stream().allMatch(r -> !r.checkOut().isAfter(today)));
        assertFalse(returned(past).contains(november));
        assertTrue(returned(list("", 2, null, null, "PAST")).contains(checkoutToday));
    }

    @Test
    void countAndRowsShareStatusSearchPeriodAndRangeFilters() {
        assertEquals(2, list("CONFIRMED", 0, null, null, "UPCOMING").totalElements());
        var cancelled = list("CANCELLED", 0, null, null, "UPCOMING");
        assertEquals(1, cancelled.totalElements());
        assertEquals("CANCELLED", cancelled.reservations().get(0).status());
        assertEquals(Set.of(octoberUpcoming),
                returned(list("CONFIRMED", 0, today.plusDays(1), today.plusDays(1), "UPCOMING")));
        assertEquals(0, list("", 0, LocalDate.of(2026, 11, 1), null, "PAST").totalElements());
        var absent = service.list(customerName + " absent", "", 0, 10, null, null, "PAST");
        assertEquals(0, absent.totalElements());
        assertTrue(absent.reservations().isEmpty());
    }

    @Test
    void allStaysPagesAreTenTenFiveAndNewestFirst() {
        assertEquals(25, list("", 0, null, null, "ALL").totalElements());
        List<Integer> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            var result = list("", page, null, null, "");
            assertEquals(25, result.totalElements());
            assertEquals(10, result.size());
            assertEquals(page == 2 ? 5 : 10, result.reservations().size());
            actual.addAll(result.reservations().stream().map(ReservationResponse::reservationId).toList());
        }
        var expected = new ArrayList<>(ids);
        expected.sort(Comparator.reverseOrder());
        assertEquals(expected, actual);
    }
}
