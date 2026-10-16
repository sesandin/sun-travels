package com.suntravels.controller;

import com.suntravels.dto.*;
import com.suntravels.service.OfferingService;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OfferingController {
    private final OfferingService service;

    public OfferingController(OfferingService service) {
        this.service = service;
    }

    @GetMapping("/offerings")
    public List<OfferingDirectoryResponse> directory() {
        return service.directory();
    }

    @GetMapping("/contracts/{id}/offerings")
    public List<OfferingResponse> list(@PathVariable("id") Integer id) {
        return service.list(id);
    }

    @GetMapping("/offerings/{id}")
    public OfferingResponse get(@PathVariable("id") Integer id) {
        return service.get(id);
    }

    @PostMapping("/contracts/{id}/offerings")
    public ResponseEntity<OfferingResponse> create(@PathVariable("id") Integer id,
            @Valid @RequestBody OfferingRequest request) {
        return ResponseEntity.status(201).body(service.create(id, request));
    }

    @PutMapping("/offerings/{id}")
    public OfferingResponse update(@PathVariable("id") Integer id,
            @Valid @RequestBody OfferingRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/offerings/{id}/rate")
    public OfferingResponse rate(@PathVariable("id") Integer id,
            @Valid @RequestBody OfferingRateRequest request) {
        return service.rate(id, request);
    }

    @PatchMapping("/offerings/{id}/status")
    public OfferingResponse status(@PathVariable("id") Integer id,
            @Valid @RequestBody OfferingStatusRequest request) {
        return service.status(id, request);
    }

    @GetMapping("/offerings/{id}/seasonal-rates")
    public List<SeasonalRateResponse> seasons(@PathVariable("id") Integer id) {
        return service.seasonalRates(id);
    }

    @PostMapping("/offerings/{id}/seasonal-rates")
    public ResponseEntity<SeasonalRateResponse> createSeason(@PathVariable("id") Integer id,
            @Valid @RequestBody SeasonalRateRequest request) {
        return ResponseEntity.status(201).body(service.createSeason(id, request));
    }

    @PutMapping("/offerings/{id}/seasonal-rates/{rateId}")
    public SeasonalRateResponse updateSeason(@PathVariable("id") Integer id,
            @PathVariable("rateId") Integer rateId, @Valid @RequestBody SeasonalRateRequest request) {
        return service.updateSeason(id, rateId, request);
    }
}
