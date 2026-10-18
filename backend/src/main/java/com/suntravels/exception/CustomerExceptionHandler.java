package com.suntravels.exception;

import com.suntravels.controller.CustomerController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = CustomerController.class)
public class CustomerExceptionHandler {
    private static final String MESSAGE = "message";

    // Returns a not-found response for the requested record.
    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(CustomerNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of(MESSAGE, ex.getMessage()));
    }

    // Creates or returns a validation error for invalid input.
    @ExceptionHandler(InvalidCustomerException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidCustomerException ex) {
        return ResponseEntity.badRequest().body(Map.of(MESSAGE, ex.getMessage()));
    }

    // Returns readable field-validation errors.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                Map.of(MESSAGE, "Check the customer details", "fieldErrors", errors));
    }

    // Returns a safe error for a malformed request.
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(
                Map.of(MESSAGE, "Provide valid customer data and numeric identifiers"));
    }

    // Returns a safe response for a database constraint failure.
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> database(DataAccessException ex) {
        return ResponseEntity.status(409).body(Map.of(MESSAGE,
                "The customer could not be saved because the data conflicts with the database"));
    }
}
