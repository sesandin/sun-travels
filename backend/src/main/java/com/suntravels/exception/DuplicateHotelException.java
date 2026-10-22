package com.suntravels.exception;

public class DuplicateHotelException extends RuntimeException
{
    public static final String MESSAGE = "A hotel with this name, country, city and street address already exists, including inactive hotels.";
    public DuplicateHotelException() { super(MESSAGE); }
}
