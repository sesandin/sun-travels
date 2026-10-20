package com.suntravels.service;

import com.suntravels.dto.RoomTypeRequest;
import com.suntravels.entity.RoomType;
import com.suntravels.exception.DuplicateRoomTypeException;
import com.suntravels.exception.RoomTypeNotFoundException;
import com.suntravels.repository.RoomTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class RoomTypeServiceTest
{
    @Mock
    RoomTypeRepository repository;
    @InjectMocks
    RoomTypeService service;

    // Builds a valid request fixture for the tested operation.
    RoomTypeRequest request( String name, String description )
    {
        RoomTypeRequest r = new RoomTypeRequest();
        r.setName( name );
        r.setDescription( description );
        return r;
    }

    // Verifies creates normalized reference data.
    @Test
    void createsNormalizedReferenceData()
    {
        when( repository.save( any() ) ).thenAnswer( i -> i.getArgument( 0 ) );
        var response = service.createRoomType( request( " Deluxe ", "  Spacious  " ) );
        assertEquals( "Deluxe", response.name() );
        assertEquals( "Spacious", response.description() );
        verify( repository ).existsByNameIgnoreCase( "Deluxe" );
    }

    // Verifies rejects duplicate create without saving.
    @Test
    void rejectsDuplicateCreateWithoutSaving()
    {
        when( repository.existsByNameIgnoreCase( "Deluxe" ) ).thenReturn( true );
        var requestForAssertion1 = request( "Deluxe", null );

        assertThrows( DuplicateRoomTypeException.class, () -> service.createRoomType( requestForAssertion1 ) );
        verify( repository, never() ).save( any() );
    }

    // Verifies updates existing type and allows its own name.
    @Test
    void updatesExistingTypeAndAllowsItsOwnName()
    {
        RoomType room = new RoomType();
        room.setName( "Deluxe" );
        when( repository.findById( 3 ) ).thenReturn( Optional.of( room ) );
        when( repository.save( room ) ).thenReturn( room );
        var response = service.updateRoomType( 3, request( "Deluxe", "  " ) );
        assertNull( response.description() );
        verify( repository ).existsByNameIgnoreCaseAndRoomTypeIdNot( "Deluxe", 3 );
    }

    // Verifies duplicate update preserves existing data.
    @Test
    void duplicateUpdatePreservesExistingData()
    {
        RoomType room = new RoomType();
        room.setName( "Standard" );
        when( repository.findById( 3 ) ).thenReturn( Optional.of( room ) );
        when( repository.existsByNameIgnoreCaseAndRoomTypeIdNot( "Deluxe", 3 ) ).thenReturn( true );
        var requestForAssertion2 = request( "Deluxe", null );

        assertThrows( DuplicateRoomTypeException.class, () -> service.updateRoomType( 3, requestForAssertion2 ) );
        assertEquals( "Standard", room.getName() );
        verify( repository, never() ).save( any() );
    }

    // Verifies missing read and update are not found.
    @Test
    void missingReadAndUpdateAreNotFound()
    {
        assertThrows( RoomTypeNotFoundException.class, () -> service.getRoomTypeById( 9 ) );
        var requestForAssertion3 = request( "Standard", null );

        assertThrows( RoomTypeNotFoundException.class, () -> service.updateRoomType( 9, requestForAssertion3 ) );
    }

    // Verifies lists response dtos.
    @Test
    void listsResponseDtos()
    {
        RoomType room = new RoomType();
        room.setName( "Standard" );
        when( repository.findAll( any( Sort.class ) ) ).thenReturn( List.of( room ) );
        assertEquals( "Standard", service.getAllRoomTypes().get( 0 ).name() );
    }
}
