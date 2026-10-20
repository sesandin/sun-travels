package com.suntravels.dto;

import com.suntravels.entity.HotelStatus;
import jakarta.validation.constraints.NotNull;

public class HotelStatusUpdateRequest
{

    @NotNull
    private HotelStatus status;

    // Returns the status.
    public HotelStatus getStatus()
    {
        return status;
    }

    // Updates the status.
    public void setStatus( HotelStatus status )
    {
        this.status = status;
    }
}