package com.suntravels.controller;

import com.suntravels.dto.RoomTypeRequest;
import com.suntravels.dto.RoomTypeResponse;
import com.suntravels.service.RoomTypeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping( "/api/room-types" )
public class RoomTypeController
{
    private final RoomTypeService service;

    // Initializes the room type controller and its required dependencies.
    public RoomTypeController( RoomTypeService service )
    {
        this.service = service;
    }

    // Returns the all room types.
    @GetMapping
    public ResponseEntity<List<RoomTypeResponse>> getAllRoomTypes()
    {
        return ResponseEntity.ok( service.getAllRoomTypes() );
    }

    // Returns the room type by id.
    @GetMapping( "/{id}" )
    public ResponseEntity<RoomTypeResponse> getRoomTypeById( @PathVariable( "id" ) Integer id )
    {
        return ResponseEntity.ok( service.getRoomTypeById( id ) );
    }

    // Creates the room type.
    @PostMapping
    public ResponseEntity<RoomTypeResponse> createRoomType( @Valid @RequestBody RoomTypeRequest request )
    {
        return ResponseEntity.status( 201 ).body( service.createRoomType( request ) );
    }

    // Updates the room type.
    @PutMapping( "/{id}" )
    public ResponseEntity<RoomTypeResponse> updateRoomType(
            @PathVariable( "id" ) Integer id,
            @Valid @RequestBody RoomTypeRequest request )
    {
        return ResponseEntity.ok( service.updateRoomType( id, request ) );
    }
}
