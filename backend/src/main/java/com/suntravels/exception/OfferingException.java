package com.suntravels.exception;

public class OfferingException extends RuntimeException {
    private final int status;

    // Creates the exception with its status, message, and any error details.
    public OfferingException(int status, String message) {
        super(message);
        this.status = status;
    }

    // Returns the status.
    public int getStatus() {
        return status;
    }

    // Creates a not-found error for the requested record.
    public static OfferingException missing(String resource, Integer id) {
        return new OfferingException(404, resource + " not found with ID: " + id);
    }

    // Creates or returns a conflict error for the requested operation.
    public static OfferingException conflict(String message) {
        return new OfferingException(409, message);
    }

    // Creates or returns a validation error for invalid input.
    public static OfferingException invalid(String message) {
        return new OfferingException(400, message);
    }
}
