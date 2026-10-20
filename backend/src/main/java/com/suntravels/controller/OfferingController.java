package com.suntravels.controller;

import com.suntravels.dto.OfferingDirectoryResponse;
import com.suntravels.dto.OfferingRateRequest;
import com.suntravels.dto.OfferingRequest;
import com.suntravels.dto.OfferingResponse;
import com.suntravels.dto.OfferingStatusRequest;
import com.suntravels.dto.SeasonalRateRequest;
import com.suntravels.dto.SeasonalRateResponse;
import com.suntravels.service.OfferingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping( "/api" )
public class OfferingController
{
    private final OfferingService service;

    // Initializes the offering controller and its required dependencies..
    public OfferingController( OfferingService service )
    {
        this.service = service;
    }

    // Lists room offering directory entries with their related details.
    @GetMapping( "/offerings" )
    public List<OfferingDirectoryResponse> directory()
    {
        return service.directory();
    }

    // Lists room offering records matching the supplied filters.
    @GetMapping( "/contracts/{id}/offerings" )
    public List<OfferingResponse> list( @PathVariable( "id" ) Integer id )
    {
        return service.list( id );
    }

    // Retrieves the requested room offering details.
    @GetMapping( "/offerings/{id}" )
    public OfferingResponse get( @PathVariable( "id" ) Integer id )
    {
        return service.get( id );
    }

    // Creates a room offering from the validated request.
    @PostMapping( "/contracts/{id}/offerings" )
    public ResponseEntity<OfferingResponse> create(
            @PathVariable( "id" ) Integer id,
            @Valid @RequestBody OfferingRequest request )
    {
        return ResponseEntity.status( 201 ).body( service.create( id, request ) );
    }

    // Updates the editable fields of the selected room offering.
    @PutMapping( "/offerings/{id}" )
    public OfferingResponse update(
            @PathVariable( "id" ) Integer id,
            @Valid @RequestBody OfferingRequest request )
    {
        return service.update( id, request );
    }

    // Updates the selected offering’s base nightly rate.
    @PatchMapping( "/offerings/{id}/rate" )
    public OfferingResponse rate(
            @PathVariable( "id" ) Integer id,
            @Valid @RequestBody OfferingRateRequest request )
    {
        return service.rate( id, request );
    }

    // Changes the selected offering’s sales status.
    @PatchMapping( "/offerings/{id}/status" )
    public OfferingResponse status(
            @PathVariable( "id" ) Integer id,
            @Valid @RequestBody OfferingStatusRequest request )
    {
        return service.status( id, request );
    }

    // Lists seasonal rate periods for the selected offering.
    @GetMapping( "/offerings/{id}/seasonal-rates" )
    public List<SeasonalRateResponse> seasons( @PathVariable( "id" ) Integer id )
    {
        return service.seasonalRates( id );
    }

    // Creates a seasonal rate period for the selected offering.
    @PostMapping( "/offerings/{id}/seasonal-rates" )
    public ResponseEntity<SeasonalRateResponse> createSeason(
            @PathVariable( "id" ) Integer id,
            @Valid @RequestBody SeasonalRateRequest request )
    {
        return ResponseEntity.status( 201 ).body( service.createSeason( id, request ) );
    }

    // Updates an existing seasonal rate period.
    @PutMapping( "/offerings/{id}/seasonal-rates/{rateId}" )
    public SeasonalRateResponse updateSeason(
            @PathVariable( "id" ) Integer id,
            @PathVariable( "rateId" ) Integer rateId, @Valid @RequestBody SeasonalRateRequest request )
    {
        return service.updateSeason( id, rateId, request );
    }
}
