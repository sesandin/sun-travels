package com.suntravels.service;

import java.util.List;

import com.suntravels.dto.HotelStatusUpdateRequest;
import com.suntravels.repository.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.suntravels.dto.HotelRequest;
import com.suntravels.dto.HotelResponse;
import com.suntravels.entity.Hotel;
import com.suntravels.exception.HotelNotFoundException;
import com.suntravels.dto.HotelUpdateRequest;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;

    // Initializes the hotel service and its required dependencies.
    public HotelService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    // Creates the hotel.
    public HotelResponse createHotel(HotelRequest request) {

        Hotel hotel = new Hotel();

        hotel.setName(request.getName());
        hotel.setAddress(request.getAddress());
        hotel.setCity(request.getCity());
        hotel.setCountry(request.getCountry());
        hotel.setPhone(request.getPhone());
        hotel.setEmail(request.getEmail());
        hotel.setStarRating(request.getStarRating());
        hotel.setStatus(request.getStatus());

        Hotel savedHotel = hotelRepository.save(hotel);

        return toResponse(savedHotel);
    }

    // Returns the all hotels.
    public List<HotelResponse> getAllHotels() {

        List<Hotel> hotels = hotelRepository.findAll();

        return hotels.stream().map(this::toResponse).toList();
    }

    // Returns the hotel by id.
    public HotelResponse getHotelById(Integer hotelId) {

        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(
                () -> new HotelNotFoundException(hotelId));

        return toResponse(hotel);
    }

    // Updates the hotel.
    public HotelResponse updateHotel(Integer hotelId, HotelUpdateRequest request) {

        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(
                () -> new HotelNotFoundException(hotelId));

        hotel.setName(request.getName());
        hotel.setAddress(request.getAddress());
        hotel.setCity(request.getCity());
        hotel.setCountry(request.getCountry());
        hotel.setPhone(request.getPhone());
        hotel.setEmail(request.getEmail());
        hotel.setStarRating(request.getStarRating());

        Hotel updatedHotel = hotelRepository.save(hotel);

        return toResponse(updatedHotel);
    }

    // Maps the stored record to its API response fields.
    HotelResponse toResponse(Hotel hotel) {

        HotelResponse response = new HotelResponse();

        response.setHotelId(hotel.getHotelId());
        response.setName(hotel.getName());
        response.setAddress(hotel.getAddress());
        response.setCity(hotel.getCity());
        response.setCountry(hotel.getCountry());
        response.setPhone(hotel.getPhone());
        response.setEmail(hotel.getEmail());
        response.setStarRating(hotel.getStarRating());
        response.setStatus(hotel.getStatus());

        return response;
    }

    /**
     * Coordinates sales eligibility with confirmation through the shared hotel lock.
     * Inactivation changes only the hotel; existing confirmed bookings and snapshots
     * remain historical commitments and receive derived operational warnings.
     */
    @Transactional(timeout = 20)
    public HotelResponse updateHotelStatus(Integer hotelId, HotelStatusUpdateRequest request) {

        Hotel hotel = hotelRepository.findForUpdate(hotelId).orElseThrow(
                () -> new HotelNotFoundException(hotelId));

        hotel.setStatus(request.getStatus());

        Hotel updatedHotel = hotelRepository.save(hotel);

        return toResponse(updatedHotel);
    }
}
