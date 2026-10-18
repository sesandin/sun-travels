package com.suntravels.service;

import com.suntravels.dto.QuotationSearchResponse.NightlyPrice;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.QuotationSearchRepository.*;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

import org.springframework.stereotype.Component;

/**
 * Shared selling-price calculation for quotations and locked confirmation; performs no writes.
 */
@Component
public class NightlyQuotationPricing {
    public record Tariff(
            LocalDate date,
            BigDecimal rate,
            Integer seasonId
    ) {
    }

    /**
     * Resolves each occupied night's seasonal override or base rate, excluding checkout.
     * Validates all supplied seasons so corrupt commercial terms fail closed even when
     * the requested stay does not intersect the corrupt period.
     *
     * @return immutable tariffs in occupied-date order
     * @throws QuotationSearchException if persisted seasonal terms are inconsistent
     */
    public List<Tariff> tariffs(Candidate offering, List<Season> seasons, LocalDate in, LocalDate out) {
        List<Season> sorted = new ArrayList<>(seasons);
        sorted.sort(Comparator.comparing(Season::from,
                Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingInt(Season::id));
        validateSeasons(offering, sorted);
        List<Tariff> result = new ArrayList<>();
        int index = 0;
        for (LocalDate date = in; date.isBefore(out); date = date.plusDays(1)) {
            while (index < sorted.size() && sorted.get(index).to().isBefore(date)) {
                index++;
            }
            Season season =
                    index < sorted.size() && !sorted.get(index).from().isAfter(date) ? sorted.get(index) :
                            null;
            result.add(new Tariff(date, season == null ? offering.baseRate() : season.rate(),
                    season == null ? null : season.id()));
        }
        return List.copyOf(result);
    }

    // Rejects invalid or conflicting seasonal periods before using the contract.
    private void validateSeasons(Candidate offering, List<Season> sorted) {
        LocalDate previousEnd = null;
        for (Season season : sorted) {
            if (season.offeringId() != offering.offeringId() || season.from() == null ||
                    season.to() == null || season.to().isBefore(season.from()) ||
                    season.from().isBefore(offering.validFrom()) || season.to().isAfter(offering.validTo()) ||
                    !validRate(season.rate()) || previousEnd != null && !season.from().isAfter(previousEnd)) {
                throw QuotationSearchException.inconsistent();
            }
            previousEnd = season.to();
        }
    }

    // Checks whether a stored rate is valid for pricing.
    public boolean validRate(BigDecimal rate) {
        return rate != null && rate.signum() > 0 && rate.scale() <= 2 &&
                rate.compareTo(new BigDecimal("99999999.99")) <= 0;
    }

    /**
     * Applies adults and percentage markup, rounding each room/night to two decimals with HALF_UP.
     */
    public BigDecimal nightly(Tariff tariff, int adults, BigDecimal markup) {
        return tariff.rate().multiply(BigDecimal.valueOf(adults)).multiply(
                BigDecimal.ONE.add(markup.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Sums already rounded nightly prices, keeping quotation totals consistent with saved snapshots.
     */
    public BigDecimal total(List<Tariff> tariffs, int adults, BigDecimal markup) {
        return tariffs.stream().map(t -> nightly(t, adults, markup)).reduce(new BigDecimal("0.00"),
                BigDecimal::add);
    }

    // Builds the nightly selling-price breakdown returned with a quotation.
    public List<NightlyPrice> breakdown(List<Tariff> tariffs, int adults, BigDecimal markup) {
        return tariffs.stream().map(
                t -> new NightlyPrice(t.date(), t.seasonId() == null ? "BASE" : "SEASONAL", t.seasonId(),
                        nightly(t, adults, markup))).toList();
    }
}
