package com.suntravels.exception;

public class HotelNotFoundException extends RuntimeException {

    public HotelNotFoundException(Integer hotelId) {
        super("Hotel not found with ID: " + hotelId);
    }
}