package com.suntravels.dto;

/** Read-only projection: management selectors include inactive hotels. */
public interface ManagementHotelOption
{
    Integer getHotelId();
    String getName();
    String getCity();
    String getCountry();
    String getStatus();
}
