package com.suntravels.exception;

public class ContractException extends RuntimeException {
    private final int status;

    private ContractException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ContractException missing(Integer id) {
        return new ContractException(404, "Contract not found with ID: " + id);
    }

    public static ContractException conflict(String message) {
        return new ContractException(409, message);
    }

    public static ContractException invalid(String message) {
        return new ContractException(400, message);
    }
}
