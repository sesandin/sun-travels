package com.suntravels.exception;

public class HotelNotFoundException extends RuntimeException {

    // Creates the exception with its status, message, and any error details.
    public HotelNotFoundException(Integer hotelId) {
        super("Hotel not found with ID: " + hotelId);
    }
}