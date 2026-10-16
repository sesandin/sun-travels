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

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(CustomerNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of(MESSAGE, ex.getMessage()));
    }

    @ExceptionHandler(InvalidCustomerException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidCustomerException ex) {
        return ResponseEntity.badRequest().body(Map.of(MESSAGE, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                Map.of(MESSAGE, "Check the customer details", "fieldErrors", errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(
                Map.of(MESSAGE, "Provide valid customer data and numeric identifiers"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> database(DataAccessException ex) {
        return ResponseEntity.status(409).body(Map.of(MESSAGE,
                "The customer could not be saved because the data conflicts with the database"));
    }
}
