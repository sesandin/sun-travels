package com.suntravels.exception;
public class RoomTypeNotFoundException extends RuntimeException {
 public RoomTypeNotFoundException(Integer id) { super("Room type not found with ID: " + id); }
}
