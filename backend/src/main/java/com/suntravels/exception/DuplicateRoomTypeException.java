package com.suntravels.exception;

public class DuplicateRoomTypeException extends RuntimeException {
    public DuplicateRoomTypeException(String name) {
        super("A room type with this name already exists: " + name);
    }
}
