package com.suntravels.exception;

import com.suntravels.controller.ReservationController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice( assignableTypes = ReservationController.class )
public class ReservationExceptionHandler
{
    private static final String MESSAGE = "message";

    // Maps a business exception to its HTTP error response.
    @ExceptionHandler( ReservationException.class )
    public ResponseEntity<Map<String,Object>> business( ReservationException ex )
    {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put( "code", ex.getCode() );
        result.put( MESSAGE, ex.getMessage() );
        if( ex.getQuotation() != null )
        {
            result.put( "quotation", ex.getQuotation() );
        }
        return ResponseEntity.status( ex.getStatus() ).body( result );
    }

    // Returns a safe error when stored data cannot be processed consistently.
    @ExceptionHandler( QuotationSearchException.class )
    public ResponseEntity<Map<String,String>> inconsistent( QuotationSearchException ex )
    {
        return ResponseEntity.status( ex.getStatus() ).body(
                Map.of( "code", "UNAVAILABLE", MESSAGE, ex.getMessage() ) );
    }

    // Creates or returns a validation error for invalid input.
    @ExceptionHandler( { MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class } )
    public ResponseEntity<Map<String,String>> invalid( Exception ex )
    {
        return ResponseEntity.badRequest().body( Map.of( "code", "INVALID_REQUEST", MESSAGE,
                "Check customer, ISO dates, whole-number room counts and accepted selling quotation" ) );
    }

    // Returns a safe error when the operation cannot be completed.
    @ExceptionHandler( { DataAccessException.class, TransactionException.class } )
    public ResponseEntity<Map<String,String>> unavailable( Exception ex )
    {
        return ResponseEntity.status( 503 ).body( Map.of( "code", "TEMPORARILY_UNAVAILABLE", MESSAGE,
                "Reservation operation could not complete. Check the reservation directory before retrying an uncertain confirmation." ) );
    }
}
