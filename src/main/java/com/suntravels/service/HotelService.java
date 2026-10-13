package com.suntravels.service;

import com.suntravels.repository.HotelRepository;
import org.springframework.stereotype.Service;
import com.suntravels.dto.HotelRequest;
import com.suntravels.dto.HotelResponse;
import com.suntravels.entity.Hotel;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;

    public HotelService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

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

        HotelResponse response = new HotelResponse();

        response.setHotelId(savedHotel.getHotelId());
        response.setName(savedHotel.getName());
        response.setAddress(savedHotel.getAddress());
        response.setCity(savedHotel.getCity());
        response.setCountry(savedHotel.getCountry());
        response.setPhone(savedHotel.getPhone());
        response.setEmail(savedHotel.getEmail());
        response.setStarRating(savedHotel.getStarRating());
        response.setStatus(savedHotel.getStatus());

        return response;

    }
}