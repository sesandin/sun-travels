package com.suntravels.repository;

import com.suntravels.entity.OfferingSeasonalRate;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SeasonalRateRepository extends JpaRepository<OfferingSeasonalRate, Integer> {
    interface PeriodCount {
        Integer getOfferingId();

        long getPeriodCount();
    }

    @Query("select s.offering.offeringId as offeringId, count(s) as periodCount from OfferingSeasonalRate s group by s.offering.offeringId")
    List<PeriodCount> countPeriodsByOffering();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OfferingSeasonalRate s where s.offering.contract.contractId = :id order by s.offering.offeringId, s.validFrom, s.seasonalRateId")
    List<OfferingSeasonalRate> findContractRatesForUpdate(@Param("id") Integer id);

    List<OfferingSeasonalRate> findByOfferingOfferingIdOrderByValidFrom(Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OfferingSeasonalRate> findBySeasonalRateIdAndOfferingOfferingId(Integer rateId,
            Integer offeringId);

    // Current/locking read avoids a stale MySQL REPEATABLE READ snapshot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OfferingSeasonalRate s where s.offering.offeringId = :id and s.seasonalRateId <> :excluded and s.validFrom <= :to and s.validTo >= :from")
    List<OfferingSeasonalRate> overlaps(@Param("id") Integer id, @Param("excluded") Integer excluded,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(s) from OfferingSeasonalRate s where s.offering.contract.contractId = :id and (s.validFrom < :from or s.validTo > :to)")
    long countOutsideContract(@Param("id") Integer id, @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
