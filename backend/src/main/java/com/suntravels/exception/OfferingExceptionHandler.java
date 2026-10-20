package com.suntravels.exception;

import com.suntravels.controller.OfferingController;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice( assignableTypes = OfferingController.class )
public class OfferingExceptionHandler
{
    private static final String MESSAGE = "message";

    // Maps an offering business exception to its HTTP response.
    @ExceptionHandler( OfferingException.class )
    public ResponseEntity<Map<String,String>> offering( OfferingException ex )
    {
        return ResponseEntity.status( ex.getStatus() ).body( Map.of( MESSAGE, ex.getMessage() ) );
    }

    // Returns readable field-validation errors.
    @ExceptionHandler( MethodArgumentNotValidException.class )
    public ResponseEntity<Map<String,Object>> validation( MethodArgumentNotValidException ex )
    {
        Map<String,String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                e -> errors.putIfAbsent( e.getField(), e.getDefaultMessage() ) );
        return ResponseEntity.badRequest().body(
                Map.of( MESSAGE, "Check the offering or seasonal rate details", "fieldErrors", errors ) );
    }

    // Returns a safe error for a malformed request.
    @ExceptionHandler( { HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class } )
    public ResponseEntity<Map<String,String>> malformed( Exception ex )
    {
        return ResponseEntity.badRequest().body( Map.of( MESSAGE,
                "Provide valid JSON, ISO dates, numeric IDs and ENABLED/SUSPENDED offering status" ) );
    }

    // Creates or returns a conflict error for the requested operation.
    @ExceptionHandler( { DataIntegrityViolationException.class, PessimisticLockingFailureException.class } )
    public ResponseEntity<Map<String,String>> conflict( Exception ex )
    {
        return ResponseEntity.status( 409 ).body(
                Map.of( MESSAGE, "Conflicting or concurrent update. Refresh and try again." ) );
    }
}
