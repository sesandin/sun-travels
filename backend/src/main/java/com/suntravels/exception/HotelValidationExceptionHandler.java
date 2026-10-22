package com.suntravels.exception;

import com.suntravels.controller.HotelController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = HotelController.class)
public class HotelValidationExceptionHandler
{
    @ExceptionHandler(DuplicateHotelException.class)
    public ResponseEntity<Map<String,String>> duplicate(DuplicateHotelException ex)
    {
        return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,String>> concurrentDuplicate(org.springframework.dao.DataIntegrityViolationException ex)
    {
        // Only translate this specific unique constraint; unrelated integrity errors remain errors.
        for (Throwable cause = ex; cause != null; cause = cause.getCause())
            if (cause.getMessage() != null && cause.getMessage().contains("uq_hotel_identity"))
                return ResponseEntity.status(409).body(Map.of("message", DuplicateHotelException.MESSAGE));
        throw ex;
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex)
    {
        Map<String,String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                Map.of("message", "Check the hotel details", "fieldErrors", errors));
    }
}
