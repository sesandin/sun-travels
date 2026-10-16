package com.suntravels.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Integer id) {
        super("Customer " + id + " was not found");
    }
}
