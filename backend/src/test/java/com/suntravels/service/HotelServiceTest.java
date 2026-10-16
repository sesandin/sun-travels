package com.suntravels.service;

import com.suntravels.repository.HotelRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.suntravels.dto.HotelRequest;
import com.suntravels.dto.HotelResponse;
import com.suntravels.entity.Hotel;
import com.suntravels.entity.HotelStatus;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.suntravels.exception.HotelNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.suntravels.dto.HotelUpdateRequest;


import com.suntravels.dto.HotelStatusUpdateRequest;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private HotelService hotelService;

    @Test
    void createHotel_shouldReturnSavedHotel() {

        // ARRANGE
        HotelRequest request = new HotelRequest();

        request.setName("Hilton Colombo");
        request.setAddress("Colombo 02");
        request.setCity("Colombo");
        request.setCountry("Sri Lanka");
        request.setPhone("0112492492");
        request.setEmail("hotel@example.com");
        request.setStarRating((byte) 5);
        request.setStatus(HotelStatus.ACTIVE);

        when(hotelRepository.save(any(Hotel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        HotelResponse response = hotelService.createHotel(request);

        // ASSERT
        assertNotNull(response);
        assertEquals("Hilton Colombo", response.getName());
        assertEquals("Colombo 02", response.getAddress());
        assertEquals("Colombo", response.getCity());
        assertEquals("Sri Lanka", response.getCountry());
        assertEquals("0112492492", response.getPhone());
        assertEquals("hotel@example.com", response.getEmail());
        assertEquals(Byte.valueOf((byte) 5), response.getStarRating());
        assertEquals(HotelStatus.ACTIVE, response.getStatus());

        ArgumentCaptor<Hotel> hotelCaptor = ArgumentCaptor.forClass(Hotel.class);

        verify(hotelRepository).save(hotelCaptor.capture());

        Hotel capturedHotel = hotelCaptor.getValue();

        assertEquals("Hilton Colombo", capturedHotel.getName());
        assertEquals("Colombo", capturedHotel.getCity());
        assertEquals(Byte.valueOf((byte) 5), capturedHotel.getStarRating());
        assertEquals(HotelStatus.ACTIVE, capturedHotel.getStatus());
    }

    @Test
    void createHotel_shouldReturnGeneratedHotelId() {

        // Arrange
        HotelRequest request = new HotelRequest();
        request.setName("Hilton Colombo");
        request.setStarRating((byte) 5);
        request.setStatus(HotelStatus.ACTIVE);

        Hotel savedHotel = mock(Hotel.class);

        when(savedHotel.getHotelId()).thenReturn(25);
        when(hotelRepository.save(any(Hotel.class))).thenReturn(savedHotel);

        // Act
        HotelResponse response = hotelService.createHotel(request);

        // Assert
        assertNotNull(response);
        assertEquals(25, response.getHotelId());

        verify(hotelRepository).save(any(Hotel.class));
    }

    @Test
    void getAllHotels_shouldReturnHotelResponses() {

        // Arrange
        Hotel hotel1 = new Hotel();
        hotel1.setName("Hilton Colombo");
        hotel1.setCity("Colombo");
        hotel1.setCountry("Sri Lanka");
        hotel1.setStarRating((byte) 5);
        hotel1.setStatus(HotelStatus.ACTIVE);

        Hotel hotel2 = new Hotel();
        hotel2.setName("Grand Hotel");
        hotel2.setCity("Nuwara Eliya");
        hotel2.setCountry("Sri Lanka");
        hotel2.setStarRating((byte) 4);
        hotel2.setStatus(HotelStatus.INACTIVE);

        when(hotelRepository.findAll()).thenReturn(List.of(hotel1, hotel2));

        // Act
        List<HotelResponse> responses = hotelService.getAllHotels();

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());

        assertEquals("Hilton Colombo", responses.get(0).getName());
        assertEquals("Colombo", responses.get(0).getCity());
        assertEquals(Byte.valueOf((byte) 5), responses.get(0).getStarRating());
        assertEquals(HotelStatus.ACTIVE, responses.get(0).getStatus());

        assertEquals("Grand Hotel", responses.get(1).getName());
        assertEquals("Nuwara Eliya", responses.get(1).getCity());
        assertEquals(Byte.valueOf((byte) 4), responses.get(1).getStarRating());
        assertEquals(HotelStatus.INACTIVE, responses.get(1).getStatus());

        verify(hotelRepository).findAll();
    }

    @Test
    void getAllHotels_shouldReturnEmptyListWhenNoHotelsExist() {

        // Arrange
        when(hotelRepository.findAll()).thenReturn(List.of());

        // Act
        List<HotelResponse> responses = hotelService.getAllHotels();

        // Assert
        assertNotNull(responses);
        assertEquals(0, responses.size());

        verify(hotelRepository).findAll();
    }

    @Test
    void getHotelById_shouldReturnHotelWhenFound() {

        // Arrange
        Integer hotelId = 1;

        Hotel hotel = new Hotel();
        hotel.setName("Hilton Colombo");
        hotel.setAddress("Colombo 02");
        hotel.setCity("Colombo");
        hotel.setCountry("Sri Lanka");
        hotel.setPhone("0112492492");
        hotel.setEmail("hotel@example.com");
        hotel.setStarRating((byte) 5);
        hotel.setStatus(HotelStatus.ACTIVE);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        // Act
        HotelResponse response = hotelService.getHotelById(hotelId);

        // Assert
        assertNotNull(response);
        assertEquals("Hilton Colombo", response.getName());
        assertEquals("Colombo 02", response.getAddress());
        assertEquals("Colombo", response.getCity());
        assertEquals("Sri Lanka", response.getCountry());
        assertEquals("0112492492", response.getPhone());
        assertEquals("hotel@example.com", response.getEmail());
        assertEquals(Byte.valueOf((byte) 5), response.getStarRating());
        assertEquals(HotelStatus.ACTIVE, response.getStatus());

        verify(hotelRepository).findById(hotelId);
    }

    @Test
    void getHotelById_shouldThrowExceptionWhenNotFound() {

        // Arrange
        Integer hotelId = 99999;

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        // Act and Assert
        HotelNotFoundException exception = assertThrows(HotelNotFoundException.class,
                () -> hotelService.getHotelById(hotelId));

        assertEquals("Hotel not found with ID: 99999", exception.getMessage());

        verify(hotelRepository).findById(hotelId);
    }

    @Test
    void updateHotel_shouldUpdateDetailsButPreserveIdAndStatus() {

        // Arrange
        Integer hotelId = 1;

        Hotel existingHotel = new Hotel();
        existingHotel.setName("Old Hotel Name");
        existingHotel.setAddress("Old Address");
        existingHotel.setCity("Colombo");
        existingHotel.setCountry("Sri Lanka");
        existingHotel.setPhone("0111111111");
        existingHotel.setEmail("old@example.com");
        existingHotel.setStarRating((byte) 3);
        existingHotel.setStatus(HotelStatus.INACTIVE);

        HotelUpdateRequest request = new HotelUpdateRequest();
        request.setName("Hilton Colombo");
        request.setAddress("2 Sir Chittampalam A Gardiner Mawatha");
        request.setCity("Colombo");
        request.setCountry("Sri Lanka");
        request.setPhone("0119999999");
        request.setEmail("updated@example.com");
        request.setStarRating((byte) 5);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(existingHotel));

        when(hotelRepository.save(existingHotel)).thenReturn(existingHotel);

        // Act
        HotelResponse result = hotelService.updateHotel(hotelId, request);

        // Assert
        assertEquals("Hilton Colombo", result.getName());
        assertEquals("2 Sir Chittampalam A Gardiner Mawatha", result.getAddress());
        assertEquals("Colombo", result.getCity());
        assertEquals("Sri Lanka", result.getCountry());
        assertEquals("0119999999", result.getPhone());
        assertEquals("updated@example.com", result.getEmail());
        assertEquals((byte) 5, result.getStarRating());

        // Status must remain unchanged
        assertEquals(HotelStatus.INACTIVE, result.getStatus());

        // The service must update the same existing entity
        verify(hotelRepository).findById(hotelId);
        verify(hotelRepository).save(same(existingHotel));
        verify(hotelRepository, times(1)).save(any(Hotel.class));
    }

    @Test
    void updateHotel_shouldThrowExceptionWhenHotelDoesNotExist() {

        // Arrange
        Integer hotelId = 99999;

        HotelUpdateRequest request = new HotelUpdateRequest();

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        // Act
        HotelNotFoundException exception = assertThrows(HotelNotFoundException.class,
                () -> hotelService.updateHotel(hotelId, request));

        // Assert
        assertEquals("Hotel not found with ID: 99999", exception.getMessage());

        verify(hotelRepository).findById(hotelId);
        verify(hotelRepository, never()).save(any(Hotel.class));
    }

    @Test
    void updateHotelStatus_shouldActivateInactiveHotel() {
        Hotel hotel = new Hotel();
        hotel.setStatus(HotelStatus.INACTIVE);

        HotelStatusUpdateRequest request = new HotelStatusUpdateRequest();
        request.setStatus(HotelStatus.ACTIVE);

        when(hotelRepository.findForUpdate(1)).thenReturn(Optional.of(hotel));
        when(hotelRepository.save(hotel)).thenReturn(hotel);

        HotelResponse response = hotelService.updateHotelStatus(1, request);

        assertEquals(HotelStatus.ACTIVE, response.getStatus());
        verify(hotelRepository).save(same(hotel));
    }

    @Test
    void updateHotelStatus_shouldDeactivateActiveHotel() {
        Hotel hotel = new Hotel();
        hotel.setName("Hilton Colombo");
        hotel.setStatus(HotelStatus.ACTIVE);

        HotelStatusUpdateRequest request = new HotelStatusUpdateRequest();
        request.setStatus(HotelStatus.INACTIVE);

        when(hotelRepository.findForUpdate(1)).thenReturn(Optional.of(hotel));
        when(hotelRepository.save(hotel)).thenReturn(hotel);

        HotelResponse response = hotelService.updateHotelStatus(1, request);

        var order = inOrder(hotelRepository);
        order.verify(hotelRepository).findForUpdate(1);
        order.verify(hotelRepository).save(same(hotel));
        assertEquals(HotelStatus.INACTIVE, response.getStatus());
        assertEquals("Hilton Colombo", response.getName());
        verify(hotelRepository).save(same(hotel));
    }

    @Test
    void updateHotelStatus_shouldThrowWhenHotelNotFound() {
        HotelStatusUpdateRequest request = new HotelStatusUpdateRequest();
        request.setStatus(HotelStatus.INACTIVE);

        when(hotelRepository.findForUpdate(99999)).thenReturn(Optional.empty());

        assertThrows(HotelNotFoundException.class, () -> hotelService.updateHotelStatus(99999, request));

        verify(hotelRepository, never()).save(any(Hotel.class));
    }
}