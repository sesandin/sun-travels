package com.suntravels.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler
{

    // Returns a not-found response for the requested hotel.
    @ExceptionHandler( HotelNotFoundException.class )
    public ResponseEntity<Map<String,String>> handleHotelNotFound( HotelNotFoundException exception )
    {

        Map<String,String> error = Map.of( "message", exception.getMessage() );

        return ResponseEntity.status( HttpStatus.NOT_FOUND ).body( error );
    }
}