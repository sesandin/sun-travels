package com.suntravels.controller;

import com.suntravels.dto.EvaluatedHotelPage;
import com.suntravels.dto.HotelSuggestion;
import com.suntravels.dto.CitySuggestion;
import com.suntravels.dto.OfferingOccupancy;
import com.suntravels.dto.QuotationSearchFilters;
import com.suntravels.dto.QuotationSearchRequest;
import com.suntravels.dto.QuotationSearchResponse;
import com.suntravels.dto.QuotationSelectionRequest;
import com.suntravels.service.QuotationSearchService;
import com.suntravels.exception.QuotationSearchException;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping( "/api/search" )
public class QuotationSearchController
{
    private final QuotationSearchService service;

    // Initializes the quotation search controller and its required dependencies.
    public QuotationSearchController( QuotationSearchService service )
    {
        this.service = service;
    }

    // Retrieves business dates and supported quotation-search limits.
    @GetMapping( "/limits" )
    public QuotationSearchResponse.Limits limits()
    {
        return service.limits();
    }

    // Retrieves the available hotel and room-type search filters.
    @GetMapping( "/filters" )
    public QuotationSearchFilters filters()
    {
        return service.filters();
    }

    @GetMapping( "/room-types" )
    public List<QuotationSearchFilters.RoomTypeOption> roomTypes()
    {
        return service.roomTypeFilters();
    }

    @GetMapping( "/hotels" )
    public List<HotelSuggestion> hotels( @RequestParam( defaultValue = "" ) String term,
            @RequestParam( required = false ) String city, @RequestParam( required = false ) String country )
    {
        return service.hotelSuggestions( term, city, country );
    }

    @GetMapping( "/cities" )
    public List<CitySuggestion> cities( @RequestParam( defaultValue = "" ) String term,
            @RequestParam( required = false ) String country )
    {
        return service.citySuggestions( term, country );
    }

    @GetMapping( "/hotels/{hotelId}" )
    public HotelSuggestion hotel( @PathVariable int hotelId )
    {
        return service.hotelOption( hotelId );
    }

    // Retrieves adult-capacity guidance for eligible room offerings.
    @GetMapping( "/occupancy" )
    public List<OfferingOccupancy> occupancy(
            @RequestParam Integer hotelId, @RequestParam Integer roomTypeId,
            @RequestParam @DateTimeFormat( iso = DateTimeFormat.ISO.DATE ) LocalDate checkIn,
            @RequestParam @DateTimeFormat( iso = DateTimeFormat.ISO.DATE ) LocalDate checkOut,
            @RequestParam( required = false ) String city )
    {
        return service.occupancy( hotelId, roomTypeId, checkIn, checkOut, city );
    }

    // Quotes the exact hotel, contract, and room offering submitted by the agent.
    @PostMapping( "/selection" )
    public QuotationSearchResponse.Quotation selection(
            @Valid @RequestBody QuotationSelectionRequest request )
    {
        return service.customize( request );
    }

    // Returns available hotel groups while preserving candidate-page continuation.
    @PostMapping( "/hotel-pages" )
    public EvaluatedHotelPage hotelPage(
            @Valid @RequestBody QuotationSearchRequest request,
            @RequestParam( required = false ) Integer afterHotelId )
    {
        if( request.hotelId() == null && ( request.city() == null || request.city().isBlank() ) )
        {
            throw QuotationSearchException.invalid( "Enter a city or select a hotel" );
        }
        return service.evaluatedPage( request, afterHotelId ).availableOnly();
    }

    // Legacy search remains available for explicit room customization and existing callers.
    @PostMapping( "/quotations" )
    public QuotationSearchResponse search( @Valid @RequestBody QuotationSearchRequest request )
    {
        return service.search( request );
    }
}
