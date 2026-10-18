package com.suntravels.exception;

public class ContractException extends RuntimeException {
    private final int status;

    // Creates the exception with its status, message, and any error details.
    private ContractException(int status, String message) {
        super(message);
        this.status = status;
    }

    // Returns the status.
    public int getStatus() {
        return status;
    }

    // Creates a not-found error for the requested record.
    public static ContractException missing(Integer id) {
        return new ContractException(404, "Contract not found with ID: " + id);
    }

    // Creates or returns a conflict error for the requested operation.
    public static ContractException conflict(String message) {
        return new ContractException(409, message);
    }

    // Creates or returns a validation error for invalid input.
    public static ContractException invalid(String message) {
        return new ContractException(400, message);
    }
}
