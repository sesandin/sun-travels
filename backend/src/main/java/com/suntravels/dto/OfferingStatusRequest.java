package com.suntravels.dto;

import com.suntravels.entity.OfferingStatus;
import jakarta.validation.constraints.NotNull;

public record OfferingStatusRequest(@NotNull OfferingStatus status)
{
}
