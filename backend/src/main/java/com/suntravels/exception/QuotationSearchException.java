package com.suntravels.exception;

public class QuotationSearchException extends RuntimeException {
    private final int status;

    // Creates the exception with its status, message, and any error details.
    public QuotationSearchException(int status, String message) {
        super(message);
        this.status = status;
    }

    // Returns the status.
    public int getStatus() {
        return status;
    }

    // Creates or returns a validation error for invalid input.
    public static QuotationSearchException invalid(String message) {
        return new QuotationSearchException(400, message);
    }

    // Returns a safe error when stored data cannot be processed consistently.
    public static QuotationSearchException inconsistent() {
        return new QuotationSearchException(409,
                "Search cannot safely quote inconsistent contract, seasonal or inventory data. Ask an administrator to review the selected hotel/contract.");
    }

    // Creates an error asking the caller to narrow an oversized search.
    public static QuotationSearchException tooBroad() {
        return new QuotationSearchException(409,
                "Search exceeds the safe processing limit. Select a hotel or room type and try again.");
    }
}
