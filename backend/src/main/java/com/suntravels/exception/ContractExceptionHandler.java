package com.suntravels.exception;

import com.suntravels.controller.ContractController;

import java.util.*;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ContractController.class)
public class ContractExceptionHandler {
    private static final String MESSAGE = "message";

    @ExceptionHandler(ContractException.class)
    public ResponseEntity<Map<String, String>> contract(ContractException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(MESSAGE, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                Map.of(MESSAGE, "Check the contract details", "fieldErrors", errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(
                Map.of(MESSAGE, "Provide valid JSON, ISO dates and a numeric contract ID"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> database(Exception ex) {
        return ResponseEntity.status(409).body(Map.of(MESSAGE,
                "Database verification or save failed. Reload the contract before retrying."));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> concurrent(Exception ex) {
        return ResponseEntity.status(409).body(
                Map.of(MESSAGE, "Another operation is updating this contract. Refresh and try again."));
    }
}
