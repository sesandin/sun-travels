package com.suntravels.controller;

import com.suntravels.dto.*;
import com.suntravels.service.ReservationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
 private final ReservationService service;
 public ReservationController(ReservationService service) { this.service=service; }
 @PostMapping public ResponseEntity<ReservationResponse> confirm(@Valid @RequestBody ReservationRequest request) {
  var result=service.confirm(request);return ResponseEntity.status(201).body(result);
 }
 @GetMapping public ReservationResponse.Page list(@RequestParam(defaultValue="") String query,
  @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate stayFrom,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate stayTo,
  @RequestParam(defaultValue="") String stayPeriod) {
  return service.list(query,status,page,size,stayFrom,stayTo,stayPeriod);
 }
 @GetMapping("/{id}") public ReservationResponse detail(@PathVariable int id) { return service.detail(id); }
 @PostMapping("/{id}/cancel") public ReservationResponse cancel(@PathVariable int id) { return service.cancel(id); }
}
