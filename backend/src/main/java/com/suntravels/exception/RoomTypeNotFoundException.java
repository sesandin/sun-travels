package com.suntravels.exception;

public class RoomTypeNotFoundException extends RuntimeException
{
    // Creates the exception with its status, message, and any error details.
    public RoomTypeNotFoundException( Integer id )
    {
        super( "Room type not found with ID: " + id );
    }
}
