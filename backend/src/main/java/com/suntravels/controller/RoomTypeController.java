package com.suntravels.controller;
import java.util.List;
import com.suntravels.dto.*;
import com.suntravels.service.RoomTypeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/room-types")
public class RoomTypeController {
 private final RoomTypeService service;
 public RoomTypeController(RoomTypeService service) { this.service=service; }
 @GetMapping public ResponseEntity<List<RoomTypeResponse>> getAllRoomTypes() { return ResponseEntity.ok(service.getAllRoomTypes()); }
 @GetMapping("/{id}") public ResponseEntity<RoomTypeResponse> getRoomTypeById(@PathVariable("id") Integer id) { return ResponseEntity.ok(service.getRoomTypeById(id)); }
 @PostMapping public ResponseEntity<RoomTypeResponse> createRoomType(@Valid @RequestBody RoomTypeRequest request) { return ResponseEntity.status(201).body(service.createRoomType(request)); }
 @PutMapping("/{id}") public ResponseEntity<RoomTypeResponse> updateRoomType(@PathVariable("id") Integer id,@Valid @RequestBody RoomTypeRequest request) { return ResponseEntity.ok(service.updateRoomType(id,request)); }
}
