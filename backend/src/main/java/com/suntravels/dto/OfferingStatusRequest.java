package com.suntravels.dto;

import jakarta.validation.constraints.*;
import com.suntravels.entity.OfferingStatus;

public record OfferingStatusRequest(@NotNull OfferingStatus status) {
}
