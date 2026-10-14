package com.suntravels.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.suntravels.entity.OfferingStatus;
public record OfferingStatusRequest(@NotNull OfferingStatus status) {}
