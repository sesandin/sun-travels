package com.suntravels.exception;

public class DuplicateRoomTypeException extends RuntimeException {
    // Creates the exception with its status, message, and any error details.
    public DuplicateRoomTypeException(String name) {
        super("A room type with this name already exists: " + name);
    }
}
