package com.suntravels.exception;

public class InvalidCustomerException extends RuntimeException
{
    // Creates the exception with its status, message, and any error details.
    public InvalidCustomerException( String message )
    {
        super( message );
    }
}
