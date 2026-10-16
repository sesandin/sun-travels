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
 @GetMapping public ReservationResponse.Page list(@RequestParam(name="query",defaultValue="") String query,
  @RequestParam(name="status",defaultValue="") String status,@RequestParam(name="page",defaultValue="0") int page,@RequestParam(name="size",defaultValue="10") int size,
  @RequestParam(name="stayFrom",required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate stayFrom,
  @RequestParam(name="stayTo",required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate stayTo,
  @RequestParam(name="stayPeriod",defaultValue="") String stayPeriod) {
  return service.list(query,status,page,size,stayFrom,stayTo,stayPeriod);
 }
 @GetMapping("/{id}") public ReservationResponse detail(@PathVariable int id) { return service.detail(id); }
 @PostMapping("/{id}/cancel") public ReservationResponse cancel(@PathVariable int id) { return service.cancel(id); }
}
