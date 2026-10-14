package com.suntravels.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.suntravels.entity.OfferingStatus;
public record SeasonalRateResponse(Integer seasonalRateId,Integer offeringId,LocalDate validFrom,LocalDate validTo,BigDecimal ratePerPersonPerNight) {}
