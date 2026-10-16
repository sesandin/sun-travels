package com.suntravels.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SeasonalRateResponse(
        Integer seasonalRateId,
        Integer offeringId,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal ratePerPersonPerNight
) {
}
