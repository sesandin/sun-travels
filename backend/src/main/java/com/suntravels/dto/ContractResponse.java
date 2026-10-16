package com.suntravels.dto;

import com.suntravels.entity.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractResponse(
        Integer contractId,
        String contractCode,
        Integer hotelId,
        String hotelName,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal markupPercentage,
        String currencyCode,
        ContractStatus status
) {
}
