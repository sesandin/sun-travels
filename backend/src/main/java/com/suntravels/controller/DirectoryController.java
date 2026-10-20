package com.suntravels.controller;

import com.suntravels.dto.ContractResponse;
import com.suntravels.dto.DirectoryPage;
import com.suntravels.dto.HotelResponse;
import com.suntravels.dto.OfferingDirectoryResponse;
import com.suntravels.dto.RoomTypeResponse;
import com.suntravels.service.DirectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping( "/api" )
public class DirectoryController
{
    private final DirectoryService service;

    // Initializes the directory controller and its required dependencies.
    public DirectoryController( DirectoryService service )
    {
        this.service = service;
    }

    // Returns a filtered hotel directory page and its totals.
    @GetMapping( "/hotels/page" )
    public DirectoryPage<HotelResponse> hotels(
            @RequestParam( name = "query", defaultValue = "" ) String query,
            @RequestParam( name = "status", defaultValue = "" ) String status, @RequestParam( name = "page", defaultValue = "0" ) int page )
    {
        return service.hotels( query, status, page );
    }

    // Returns a filtered room-type directory page and its totals.
    @GetMapping( "/room-types/page" )
    public DirectoryPage<RoomTypeResponse> rooms(
            @RequestParam( name = "query", defaultValue = "" ) String query,
            @RequestParam( name = "page", defaultValue = "0" ) int page )
    {
        return service.rooms( query, page );
    }

    // Returns a filtered contract directory page and its totals.
    @GetMapping( "/contracts/page" )
    public DirectoryPage<ContractResponse> contracts(
            @RequestParam( name = "query", defaultValue = "" ) String query,
            @RequestParam( name = "status", defaultValue = "" ) String status, @RequestParam( name = "page", defaultValue = "0" ) int page )
    {
        return service.contracts( query, status, page );
    }

    // Returns a filtered offering directory page and its totals.
    @GetMapping( "/offerings/page" )
    public DirectoryPage<OfferingDirectoryResponse> offerings(
            @RequestParam Map<String,String> filters,
            @RequestParam( name = "page", defaultValue = "0" ) int page )
    {
        return service.offerings( filters, page );
    }
}
