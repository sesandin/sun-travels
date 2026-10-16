package com.suntravels.exception;

public class QuotationSearchException extends RuntimeException {
    private final int status;

    public QuotationSearchException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static QuotationSearchException invalid(String message) {
        return new QuotationSearchException(400, message);
    }

    public static QuotationSearchException inconsistent() {
        return new QuotationSearchException(409,
                "Search cannot safely quote inconsistent contract, seasonal, closure or inventory data. Ask an administrator to review the selected hotel/contract.");
    }

    public static QuotationSearchException tooBroad() {
        return new QuotationSearchException(409,
                "Search exceeds the safe processing limit. Select a hotel or room type and try again.");
    }
}
