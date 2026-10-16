package com.suntravels.exception;

public class OfferingException extends RuntimeException {
    private final int status;

    public OfferingException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static OfferingException missing(String resource, Integer id) {
        return new OfferingException(404, resource + " not found with ID: " + id);
    }

    public static OfferingException conflict(String message) {
        return new OfferingException(409, message);
    }

    public static OfferingException invalid(String message) {
        return new OfferingException(400, message);
    }
}
