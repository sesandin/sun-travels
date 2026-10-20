package com.suntravels.controller;

import com.suntravels.dto.ReservationRequest;
import com.suntravels.dto.ReservationResponse;
import com.suntravels.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping( "/api/reservations" )
public class ReservationController
{
    private final ReservationService service;

    // Initializes the reservation controller and its required dependencies.
    public ReservationController( ReservationService service )
    {
        this.service = service;
    }

    // Confirms the reservation using the accepted quotation and current availability.
    @PostMapping
    public ResponseEntity<ReservationResponse> confirm( @Valid @RequestBody ReservationRequest request )
    {
        var result = service.confirm( request );
        return ResponseEntity.status( 201 ).body( result );
    }

    // Lists reservation records matching the supplied filters.
    @GetMapping
    public ReservationResponse.Page list(
            @RequestParam( name = "query", defaultValue = "" ) String query,
            @RequestParam( name = "status", defaultValue = "" ) String status,
            @RequestParam( name = "page", defaultValue = "0" ) int page,
            @RequestParam( name = "size", defaultValue = "10" ) int size,
            @RequestParam( name = "stayFrom", required = false ) @DateTimeFormat( iso = DateTimeFormat.ISO.DATE ) LocalDate stayFrom,
            @RequestParam( name = "stayTo", required = false ) @DateTimeFormat( iso = DateTimeFormat.ISO.DATE ) LocalDate stayTo,
            @RequestParam( name = "stayPeriod", defaultValue = "" ) String stayPeriod )
    {
        return service.list( query, status, page, size, stayFrom, stayTo, stayPeriod );
    }

    // Retrieves the requested reservation details.
    @GetMapping( "/{id}" )
    public ReservationResponse detail( @PathVariable int id )
    {
        return service.detail( id );
    }

    // Cancels the selected reservation while preserving its history.
    @PostMapping( "/{id}/cancel" )
    public ReservationResponse cancel( @PathVariable int id )
    {
        return service.cancel( id );
    }
}
