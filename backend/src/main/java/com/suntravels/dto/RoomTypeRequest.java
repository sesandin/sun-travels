package com.suntravels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RoomTypeRequest
{
    @NotBlank( message = "Room type name is required" )
    @Size( max = 100, message = "Name must be at most 100 characters" )
    private String name;
    @Size( max = 255, message = "Description must be at most 255 characters" )
    private String description;

    // Returns the name.
    public String getName()
    {
        return name;
    }

    // Updates the name.
    public void setName( String name )
    {
        this.name = name;
    }

    // Returns the description.
    public String getDescription()
    {
        return description;
    }

    // Updates the description.
    public void setDescription( String description )
    {
        this.description = description;
    }
}
