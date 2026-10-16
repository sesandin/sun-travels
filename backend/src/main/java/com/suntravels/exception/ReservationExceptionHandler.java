package com.suntravels.exception;

import com.suntravels.controller.ReservationController;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=ReservationController.class)
public class ReservationExceptionHandler {
 @ExceptionHandler(ReservationException.class) public ResponseEntity<Map<String,Object>> business(ReservationException ex) {
  Map<String,Object> result=new LinkedHashMap<>();result.put("code",ex.getCode());result.put("message",ex.getMessage());
  if(ex.getQuotation()!=null) result.put("quotation",ex.getQuotation());return ResponseEntity.status(ex.getStatus()).body(result);
 }
 @ExceptionHandler(QuotationSearchException.class) public ResponseEntity<Map<String,String>> inconsistent(QuotationSearchException ex) {
  return ResponseEntity.status(ex.getStatus()).body(Map.of("code","UNAVAILABLE","message",ex.getMessage()));
 }
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<Map<String,String>> invalid(Exception ex) { return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","message","Check customer, ISO dates, whole-number room counts and accepted selling quotation")); }
 @ExceptionHandler({DataAccessException.class,TransactionException.class}) public ResponseEntity<Map<String,String>> unavailable(Exception ex) {
  return ResponseEntity.status(503).body(Map.of("code","TEMPORARILY_UNAVAILABLE","message","Reservation operation could not complete. Check the reservation directory before retrying an uncertain confirmation."));
 }
}
