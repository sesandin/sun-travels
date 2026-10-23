package com.suntravels.service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.suntravels.entity.HotelStatus;
import com.suntravels.dto.ManagementHotelOption;

import com.suntravels.dto.HotelRequest;
import com.suntravels.dto.HotelResponse;
import com.suntravels.dto.HotelStatusUpdateRequest;
import com.suntravels.dto.HotelUpdateRequest;
import com.suntravels.entity.Hotel;
import com.suntravels.exception.HotelNotFoundException;
import com.suntravels.repository.HotelRepository;
import com.suntravels.validation.CountryCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HotelService
{

    private final HotelRepository hotelRepository;

    // Initializes the hotel service and its required dependencies.
    public HotelService( HotelRepository hotelRepository )
    {
        this.hotelRepository = hotelRepository;
    }

    // Creates the hotel.
    @Transactional
    public HotelResponse createHotel( HotelRequest request )
    {
        String name = stripWhitespace( request.getName() );
        String city = stripWhitespace( request.getCity() );
        String address = stripWhitespace( request.getAddress() );
        checkDuplicate( name, request.getCountry(), city, address, null );
        Hotel hotel = new Hotel();

        hotel.setName( name );
        hotel.setAddress( address );
        hotel.setCity( city );
        hotel.setCountry( CountryCatalog.canonicalName( request.getCountry() ) );
        hotel.setPhone( request.getPhone() );
        hotel.setEmail( request.getEmail() );
        hotel.setStarRating( request.getStarRating() );
        hotel.setStatus( request.getStatus() );

        Hotel savedHotel = hotelRepository.saveAndFlush( hotel );

        return toResponse( savedHotel );
    }

    // Bound management suggestions in SQL instead of loading the catalogue.
    public List<ManagementHotelOption> options(String term, HotelStatus status)
    {
        if (term.length() > 150) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Hotel name must be at most 150 characters");
        String text = term.trim();
        if (text.length() < 2) return List.of();
        String pattern = "%" + text.replace( "!", "!!" ).replace( "%", "!%" ).replace( "_", "!_" ) + "%";
        return status == null ? hotelRepository.allStatusOptions( pattern ) :
                       hotelRepository.options( pattern, status.name() );
    }

    public ManagementHotelOption option(Integer id)
    {
        return hotelRepository.option(id).orElseThrow(() -> new HotelNotFoundException(id));
    }

    // Returns the all hotels.
    public List<HotelResponse> getAllHotels()
    {

        List<Hotel> hotels = hotelRepository.findAll();

        return hotels.stream().map( this::toResponse ).toList();
    }

    // Returns the hotel by id.
    public HotelResponse getHotelById( Integer hotelId )
    {

        Hotel hotel = hotelRepository.findById( hotelId ).orElseThrow(
                () -> new HotelNotFoundException( hotelId ) );

        return toResponse( hotel );
    }

    // Updates the hotel.
    @Transactional
    public HotelResponse updateHotel( Integer hotelId, HotelUpdateRequest request )
    {

        Hotel hotel = hotelRepository.findById( hotelId ).orElseThrow(
                () -> new HotelNotFoundException( hotelId ) );

        String name = stripWhitespace( request.getName() );
        String city = stripWhitespace( request.getCity() );
        String address = stripWhitespace( request.getAddress() );
        checkDuplicate( name, request.getCountry(), city, address, hotelId );

        hotel.setName( name );
        hotel.setAddress( address );
        hotel.setCity( city );
        hotel.setCountry( CountryCatalog.canonicalName( request.getCountry() ) );
        hotel.setPhone( request.getPhone() );
        hotel.setEmail( request.getEmail() );
        hotel.setStarRating( request.getStarRating() );

        Hotel updatedHotel = hotelRepository.saveAndFlush( hotel );

        return toResponse( updatedHotel );
    }

    private String stripWhitespace( String value )
    {
        return value == null ? null : value.strip();
    }

    private void checkDuplicate(String name, String country, String city, String address, Integer excluded)
    {
        if (hotelRepository.countIdentity(name, CountryCatalog.canonicalName(country), city, address, excluded) > 0)
            throw new com.suntravels.exception.DuplicateHotelException();
    }

    // Maps the stored record to its API response fields.
    HotelResponse toResponse( Hotel hotel )
    {

        HotelResponse response = new HotelResponse();

        response.setHotelId( hotel.getHotelId() );
        response.setName( hotel.getName() );
        response.setAddress( hotel.getAddress() );
        response.setCity( hotel.getCity() );
        response.setCountry( hotel.getCountry() );
        response.setPhone( hotel.getPhone() );
        response.setEmail( hotel.getEmail() );
        response.setStarRating( hotel.getStarRating() );
        response.setStatus( hotel.getStatus() );

        return response;
    }

    /**
     * Coordinates sales eligibility with confirmation through the shared hotel lock.
     * Inactivation changes only the hotel; existing confirmed bookings and snapshots
     * remain historical commitments and receive derived operational warnings.
     */
    @Transactional( timeout = 20 )
    public HotelResponse updateHotelStatus( Integer hotelId, HotelStatusUpdateRequest request )
    {

        Hotel hotel = hotelRepository.findForUpdate( hotelId ).orElseThrow(
                () -> new HotelNotFoundException( hotelId ) );

        hotel.setStatus( request.getStatus() );

        Hotel updatedHotel = hotelRepository.save( hotel );

        return toResponse( updatedHotel );
    }
}
