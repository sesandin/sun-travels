package com.suntravels.controller;

import com.suntravels.dto.HotelRequest;
import com.suntravels.dto.HotelResponse;
import com.suntravels.dto.HotelStatusUpdateRequest;
import com.suntravels.service.HotelService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

import com.suntravels.dto.HotelUpdateRequest;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    // Initializes the hotel controller and its required dependencies.
    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    // Creates the hotel.
    @PostMapping
    public ResponseEntity<HotelResponse> createHotel(@Valid @RequestBody HotelRequest request) {

        HotelResponse response = hotelService.createHotel(request);

        return ResponseEntity.status(201).body(response);
    }

    // Returns the all hotels.
    @GetMapping
    public ResponseEntity<List<HotelResponse>> getAllHotels() {

        List<HotelResponse> hotels = hotelService.getAllHotels();

        return ResponseEntity.ok(hotels);
    }

    // Returns the hotel by id.
    @GetMapping("/{id}")
    public ResponseEntity<HotelResponse> getHotelById(@PathVariable("id") Integer hotelId) {

        HotelResponse response = hotelService.getHotelById(hotelId);

        return ResponseEntity.ok(response);
    }

    // Updates the hotel.
    @PutMapping("/{id}")
    public ResponseEntity<HotelResponse> updateHotel(@PathVariable("id") Integer hotelId,
            @Valid @RequestBody HotelUpdateRequest request) {

        HotelResponse response = hotelService.updateHotel(hotelId, request);

        return ResponseEntity.ok(response);
    }

    // Updates the hotel status.
    @PatchMapping("/{id}/status")
    public ResponseEntity<HotelResponse> updateHotelStatus(@PathVariable("id") Integer hotelId,
            @Valid @RequestBody HotelStatusUpdateRequest request) {

        HotelResponse response = hotelService.updateHotelStatus(hotelId, request);

        return ResponseEntity.ok(response);
    }
}