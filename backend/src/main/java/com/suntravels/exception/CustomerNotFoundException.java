package com.suntravels.exception;

public class CustomerNotFoundException extends RuntimeException
{
    // Creates the exception with its status, message, and any error details.
    public CustomerNotFoundException( Integer id )
    {
        super( "Customer " + id + " was not found" );
    }
}
