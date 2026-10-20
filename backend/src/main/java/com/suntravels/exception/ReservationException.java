package com.suntravels.exception;

import com.suntravels.dto.ReservationResponse.Quote;

import java.io.Serial;

public class ReservationException extends RuntimeException
{
    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final Quote quotation;

    // Creates the exception with its status, message, and any error details.
    public ReservationException( int status, String code, String message )
    {
        this( status, code, message, null );
    }

    // Creates the exception with its status, message, and any error details.
    public ReservationException( int status, String code, String message, Quote quotation )
    {
        super( message );
        this.status = status;
        this.code = code;
        this.quotation = quotation;
    }

    // Returns a safe error when the operation cannot be completed.
    public static ReservationException unavailable()
    {
        return new ReservationException( 409, "UNAVAILABLE",
                "Selected accommodation is no longer eligible or available. Search again." );
    }

    // Returns the status.
    public int getStatus()
    {
        return status;
    }

    // Returns the code.
    public String getCode()
    {
        return code;
    }

    // Returns the quotation.
    public Quote getQuotation()
    {
        return quotation;
    }
}
