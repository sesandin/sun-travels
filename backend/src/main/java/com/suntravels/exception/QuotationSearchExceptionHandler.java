package com.suntravels.exception;
import com.suntravels.controller.QuotationSearchController;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=QuotationSearchController.class)
public class QuotationSearchExceptionHandler {
    private static final String MESSAGE = "message";

 @ExceptionHandler(QuotationSearchException.class) public ResponseEntity<Map<String,String>> business(QuotationSearchException ex) { return ResponseEntity.status(ex.getStatus()).body(Map.of(MESSAGE,ex.getMessage())); }
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<Map<String,Object>> invalid(MethodArgumentNotValidException ex) {
  Map<String,String> errors=new TreeMap<>(); ex.getBindingResult().getFieldErrors().forEach(e->errors.putIfAbsent(e.getField(),e.getDefaultMessage()));
  return ResponseEntity.badRequest().body(Map.of(MESSAGE,"Check the search details","fieldErrors",errors));
 }
 @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<Map<String,String>> malformed(Exception ex) { return ResponseEntity.badRequest().body(Map.of(MESSAGE,"Use valid ISO dates, a rooms array and JSON whole numbers for adults and filter IDs")); }
 @ExceptionHandler({DataAccessException.class,TransactionException.class}) public ResponseEntity<Map<String,String>> unavailable(Exception ex) { return ResponseEntity.status(503).body(Map.of(MESSAGE,"Accommodation search is temporarily unavailable. Please retry.")); }
}
