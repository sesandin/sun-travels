package com.suntravels.repository;

import com.suntravels.entity.OfferingSeasonalRate;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SeasonalRateRepository extends JpaRepository<OfferingSeasonalRate, Integer> {
    interface PeriodCount {
        // Returns the offering id.
        Integer getOfferingId();

        // Returns the period count.
        long getPeriodCount();
    }

    // Counts seasonal rate periods for each requested offering.
    @Query("select s.offering.offeringId as offeringId, count(s) as periodCount from OfferingSeasonalRate s group by s.offering.offeringId")
    List<PeriodCount> countPeriodsByOffering();

    // Retrieves and locks seasonal rates belonging to a contract.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OfferingSeasonalRate s where s.offering.contract.contractId = :id order by s.offering.offeringId, s.validFrom, s.seasonalRateId")
    List<OfferingSeasonalRate> findContractRatesForUpdate(@Param("id") Integer id);

    // Lists an offering’s seasonal rates in start-date order.
    List<OfferingSeasonalRate> findByOfferingOfferingIdOrderByValidFrom(Integer id);

    // Finds a seasonal rate only within its owning offering.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OfferingSeasonalRate> findBySeasonalRateIdAndOfferingOfferingId(Integer rateId,
            Integer offeringId);

    // Current/locking read avoids a stale MySQL REPEATABLE READ snapshot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OfferingSeasonalRate s where s.offering.offeringId = :id and s.seasonalRateId <> :excluded and s.validFrom <= :to and s.validTo >= :from")
    List<OfferingSeasonalRate> overlaps(@Param("id") Integer id, @Param("excluded") Integer excluded,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    // Counts seasonal periods that extend beyond the contract dates.
    @Query("select count(s) from OfferingSeasonalRate s where s.offering.contract.contractId = :id and (s.validFrom < :from or s.validTo > :to)")
    long countOutsideContract(@Param("id") Integer id, @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
