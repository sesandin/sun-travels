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

        when(hotelRepository.save(any(Hotel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

        ArgumentCaptor<Hotel> hotelCaptor =
                ArgumentCaptor.forClass(Hotel.class);

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
        when(hotelRepository.save(any(Hotel.class)))
                .thenReturn(savedHotel);

        // Act
        HotelResponse response = hotelService.createHotel(request);

        // Assert
        assertNotNull(response);
        assertEquals(25, response.getHotelId());

        verify(hotelRepository).save(any(Hotel.class));
    }

}