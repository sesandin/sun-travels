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

    // Initializes the room type controller and its required dependencies.
    public RoomTypeController(RoomTypeService service) {
        this.service = service;
    }

    // Returns the all room types.
    @GetMapping
    public ResponseEntity<List<RoomTypeResponse>> getAllRoomTypes() {
        return ResponseEntity.ok(service.getAllRoomTypes());
    }

    // Returns the room type by id.
    @GetMapping("/{id}")
    public ResponseEntity<RoomTypeResponse> getRoomTypeById(@PathVariable("id") Integer id) {
        return ResponseEntity.ok(service.getRoomTypeById(id));
    }

    // Creates the room type.
    @PostMapping
    public ResponseEntity<RoomTypeResponse> createRoomType(@Valid @RequestBody RoomTypeRequest request) {
        return ResponseEntity.status(201).body(service.createRoomType(request));
    }

    // Updates the room type.
    @PutMapping("/{id}")
    public ResponseEntity<RoomTypeResponse> updateRoomType(@PathVariable("id") Integer id,
            @Valid @RequestBody RoomTypeRequest request) {
        return ResponseEntity.ok(service.updateRoomType(id, request));
    }
}
