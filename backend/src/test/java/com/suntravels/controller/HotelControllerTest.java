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
import com.suntravels.dto.HotelUpdateRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;


import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.mockito.Mockito.verifyNoInteractions;
import com.suntravels.dto.HotelStatusUpdateRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@ExtendWith(MockitoExtension.class)
class HotelControllerTest {

    @Mock
    private HotelService hotelService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        HotelController hotelController =
                new HotelController(hotelService);

        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                          .standaloneSetup(hotelController)
                          .setControllerAdvice(new GlobalExceptionHandler())
                          .setValidator(validator)
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

    @Test
    void updateHotel_shouldReturn200WhenHotelExists() throws Exception {

        // Arrange
        Integer hotelId = 1;

        HotelResponse response = new HotelResponse();
        response.setHotelId(hotelId);
        response.setName("Hilton Colombo");
        response.setAddress("Colombo 02");
        response.setCity("Colombo");
        response.setCountry("Sri Lanka");
        response.setPhone("0119999999");
        response.setEmail("updated@example.com");
        response.setStarRating((byte) 5);
        response.setStatus(HotelStatus.INACTIVE);

        when(hotelService.updateHotel(
                eq(hotelId),
                any(HotelUpdateRequest.class)
        )).thenReturn(response);

        String requestJson = """
            {
                "name": "Hilton Colombo",
                "address": "Colombo 02",
                "city": "Colombo",
                "country": "Sri Lanka",
                "phone": "0119999999",
                "email": "updated@example.com",
                "starRating": 5
            }
            """;

        // Act and Assert
        mockMvc.perform(
                       put("/api/hotels/{id}", hotelId)
                               .contentType("application/json")
                               .content(requestJson)
               )
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.hotelId").value(1))
               .andExpect(jsonPath("$.name").value("Hilton Colombo"))
               .andExpect(jsonPath("$.phone").value("0119999999"))
               .andExpect(jsonPath("$.starRating").value(5))
               .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(hotelService).updateHotel(
                eq(hotelId),
                any(HotelUpdateRequest.class)
        );
    }

    @Test
    void updateHotel_shouldReturn404WhenHotelDoesNotExist() throws Exception {

        Integer hotelId = 99999;

        String requestJson = """
            {
                "name": "Hilton Colombo",
                "address": "Colombo 02",
                "city": "Colombo",
                "country": "Sri Lanka",
                "phone": "0119999999",
                "email": "updated@example.com",
                "starRating": 5
            }
            """;

        when(hotelService.updateHotel(
                eq(hotelId),
                any(HotelUpdateRequest.class)
        )).thenThrow(new HotelNotFoundException(hotelId));

        mockMvc.perform(
                       put("/api/hotels/{id}", hotelId)
                               .contentType("application/json")
                               .content(requestJson)
               )
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.message")
                                  .value("Hotel not found with ID: 99999"));
    }

    @Test
    void updateHotel_shouldReturn400WhenRequestIsInvalid() throws Exception {

        String invalidJson = """
            {
                "name": "",
                "address": "Colombo 02",
                "city": "Colombo",
                "country": "Sri Lanka",
                "phone": "0119999999",
                "email": "invalid-email",
                "starRating": 8
            }
            """;

        mockMvc.perform(
                       put("/api/hotels/{id}", 1)
                               .contentType("application/json")
                               .content(invalidJson)
               )
               .andExpect(status().isBadRequest());

        verifyNoInteractions(hotelService);
    }

    @Test
    void updateHotelStatus_shouldReturn200() throws Exception {
        HotelResponse response = new HotelResponse();
        response.setHotelId(1);
        response.setName("Hilton Colombo");
        response.setStatus(HotelStatus.INACTIVE);

        when(hotelService.updateHotelStatus(
                eq(1),
                any(HotelStatusUpdateRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                       patch("/api/hotels/{id}/status", 1)
                               .contentType("application/json")
                               .content("""
                            {"status":"INACTIVE"}
                            """)
               )
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.hotelId").value(1))
               .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void updateHotelStatus_shouldReturn400ForMissingStatus() throws Exception {
        mockMvc.perform(
                       patch("/api/hotels/{id}/status", 1)
                               .contentType("application/json")
                               .content("{}")
               )
               .andExpect(status().isBadRequest());

        verifyNoInteractions(hotelService);
    }


}