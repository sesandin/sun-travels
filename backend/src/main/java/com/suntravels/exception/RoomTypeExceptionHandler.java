package com.suntravels.exception;
import java.util.*;
import com.suntravels.controller.RoomTypeController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(assignableTypes=RoomTypeController.class)
public class RoomTypeExceptionHandler {
 @ExceptionHandler(RoomTypeNotFoundException.class)
 public ResponseEntity<Map<String,String>> notFound(RoomTypeNotFoundException ex) { return ResponseEntity.status(404).body(Map.of("message",ex.getMessage())); }
 @ExceptionHandler(DuplicateRoomTypeException.class)
 public ResponseEntity<Map<String,String>> conflict(DuplicateRoomTypeException ex) { return ResponseEntity.status(409).body(Map.of("message",ex.getMessage())); }
 @ExceptionHandler(MethodArgumentNotValidException.class)
 public ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex) {
  Map<String,String> errors=new LinkedHashMap<>();
  ex.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(),error.getDefaultMessage()));
  return ResponseEntity.badRequest().body(Map.of("message","Check the room type details","fieldErrors",errors));
 }
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<Map<String,String>> invalidRequest(Exception ex) { return ResponseEntity.badRequest().body(Map.of("message","Provide valid JSON and a numeric room type ID")); }
}
