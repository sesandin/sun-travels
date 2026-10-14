package com.suntravels.controller;

import com.suntravels.exception.GlobalExceptionHandler;
import com.suntravels.exception.HotelNotFoundException;
import com.suntravels.service.HotelService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.suntravels.dto.HotelResponse;
import com.suntravels.entity.HotelStatus;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HotelControllerTest {

    @Mock
    private HotelService hotelService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        HotelController hotelController =
                new HotelController(hotelService);

        mockMvc = MockMvcBuilders
                          .standaloneSetup(hotelController)
                          .setControllerAdvice(new GlobalExceptionHandler())
                          .build();
    }

    @Test
    void getHotelById_shouldReturn404WhenHotelNotFound() throws Exception {

        // Arrange
        Integer hotelId = 99999;

        when(hotelService.getHotelById(hotelId))
                .thenThrow(new HotelNotFoundException(hotelId));

        // Act and Assert
        mockMvc.perform(get("/api/hotels/{id}", hotelId))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.message")
                                  .value("Hotel not found with ID: 99999"));
    }

    @Test
    void getHotelById_shouldReturn200WhenHotelExists() throws Exception {

        // Arrange
        Integer hotelId = 1;

        HotelResponse response = new HotelResponse();
        response.setHotelId(hotelId);
        response.setName("Hilton Colombo");
        response.setAddress("Colombo 02");
        response.setCity("Colombo");
        response.setCountry("Sri Lanka");
        response.setPhone("0112492492");
        response.setEmail("hotel@example.com");
        response.setStarRating((byte) 5);
        response.setStatus(HotelStatus.ACTIVE);

        when(hotelService.getHotelById(hotelId))
                .thenReturn(response);

        // Act and Assert
        mockMvc.perform(get("/api/hotels/{id}", hotelId))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.hotelId").value(1))
               .andExpect(jsonPath("$.name").value("Hilton Colombo"))
               .andExpect(jsonPath("$.city").value("Colombo"))
               .andExpect(jsonPath("$.country").value("Sri Lanka"))
               .andExpect(jsonPath("$.starRating").value(5))
               .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(hotelService).getHotelById(hotelId);
    }
}