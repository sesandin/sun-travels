package com.suntravels.service;

import com.suntravels.repository.QuotationSearchRepository.*;
import com.suntravels.exception.QuotationSearchException;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NightlyQuotationPricingTest {
    final NightlyQuotationPricing pricing = new NightlyQuotationPricing();
    final LocalDate from = LocalDate.of(2026, 11, 1), in = from.plusDays(7), out = in.plusDays(3);

    Candidate offering(String rate) {
        return new Candidate(7, 4, 1, "Hotel", "Colombo", "C004", "USD", new BigDecimal("15.00"), from,
                from.plusDays(29), 2, "Double", new BigDecimal(rate), 10, 2);
    }

    @Test
    void baseFallbackAndMarkupUseExactDecimals() {
        var tariffs = pricing.tariffs(offering("100"), List.of(), in, out);
        var nights = pricing.breakdown(tariffs, 2, new BigDecimal("15"));
        assertEquals(new BigDecimal("230.00"), nights.get(0).sellingPrice());
        assertEquals("BASE", nights.get(0).source());
        assertEquals(new BigDecimal("690.00"), pricing.total(tariffs, 2, new BigDecimal("15")));
    }

    @Test
    void seasonalEndIsInclusiveAndStayCanSpanBaseAndMultiplePeriods() {
        var c = offering("100");
        var tariffs = pricing.tariffs(c, List.of(new Season(1, 7, in, in, new BigDecimal("120")),
                new Season(2, 7, in.plusDays(2), in.plusDays(2), new BigDecimal("200"))), in, out);
        var nights = pricing.breakdown(tariffs, 2, c.markup());
        assertEquals(List.of("SEASONAL", "BASE", "SEASONAL"), nights.stream().map(n -> n.source()).toList());
        assertEquals(new BigDecimal("966.00"), pricing.total(tariffs, 2, c.markup()));
        assertEquals(
                nights.stream().map(n -> n.sellingPrice()).reduce(new BigDecimal("0.00"), BigDecimal::add),
                pricing.total(tariffs, 2, c.markup()));
    }

    @Test
    void eachRoomNightRoundsHalfUpBeforeSummation() {
        var tariffs = pricing.tariffs(offering("0.05"), List.of(), in, in.plusDays(2));
        assertEquals(new BigDecimal("0.12"), pricing.total(tariffs, 1, new BigDecimal("10")));
        assertEquals(new BigDecimal("0.34"), pricing.total(tariffs, 3, new BigDecimal("10")));
    }

    @Test
    void crossingCheckoutUsesOnlyOccupiedNights() {
        var c = offering("100");
        var tariffs = pricing.tariffs(c,
                List.of(new Season(1, 7, c.validTo(), c.validTo(), new BigDecimal("150"))), c.validTo(),
                c.validTo().plusDays(1));
        assertEquals(1, tariffs.size());
        assertEquals(new BigDecimal("345.00"), pricing.total(tariffs, 2, c.markup()));
    }

    @Test
    void conflictingOrInvalidPeriodsFailInsteadOfArbitrarySelection() {
        var c = offering("100");
        for (var periods : List.of(List.of(new Season(1, 7, in, out, new BigDecimal("150")),
                        new Season(2, 7, out, out, new BigDecimal("200"))),
                List.of(new Season(1, 7, from.minusDays(1), in, BigDecimal.ONE)),
                List.of(new Season(1, 7, in, in, BigDecimal.ZERO)),
                List.of(new Season(1, 7, out, in, BigDecimal.ONE))))
            assertThrows(QuotationSearchException.class, () -> pricing.tariffs(c, periods, in, out));
    }
}
