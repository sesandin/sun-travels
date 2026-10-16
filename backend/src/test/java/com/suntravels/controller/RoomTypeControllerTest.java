package com.suntravels.controller;

import com.suntravels.dto.*;
import com.suntravels.exception.*;
import com.suntravels.service.RoomTypeService;

import java.util.List;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RoomTypeControllerTest {
    @Mock
    RoomTypeService service;
    MockMvc mvc;
    LocalValidatorFactoryBean validator;

    @BeforeEach
    void setup() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new RoomTypeController(service)).setControllerAdvice(
                new RoomTypeExceptionHandler()).setValidator(validator).build();
    }

    @AfterEach
    void close() {
        validator.close();
    }

    @Test
    void listsAndReadsDtos() throws Exception {
        var response = new RoomTypeResponse(1, "Deluxe", null);
        when(service.getAllRoomTypes()).thenReturn(List.of(response));
        when(service.getRoomTypeById(1)).thenReturn(response);
        mvc.perform(get("/api/room-types")).andExpect(status().isOk()).andExpect(
                jsonPath("$[0].roomTypeId").value(1));
        mvc.perform(get("/api/room-types/1")).andExpect(status().isOk()).andExpect(
                jsonPath("$.name").value("Deluxe"));
    }

    @Test
    void createsAndUpdates() throws Exception {
        var response = new RoomTypeResponse(1, "Deluxe", null);
        when(service.createRoomType(any())).thenReturn(response);
        when(service.updateRoomType(eq(1), any())).thenReturn(response);
        mvc.perform(post("/api/room-types").contentType("application/json").content(
                "{\"name\":\"Deluxe\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/room-types/1").contentType("application/json").content(
                "{\"name\":\"Deluxe\"}")).andExpect(status().isOk());
    }

    @Test
    void validatesBothWriteOperations() throws Exception {
        for (String json : List.of("{}", "{\"name\":\"   \"}", "{\"name\":\"" + "a".repeat(101) + "\"}",
                "{\"name\":\"Valid\",\"description\":\"" + "a".repeat(256) + "\"}")) {
            mvc.perform(post("/api/room-types").contentType("application/json").content(json)).andExpect(
                    status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").exists());
            mvc.perform(put("/api/room-types/1").contentType("application/json").content(json)).andExpect(
                    status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void acceptsMaximumLengths() throws Exception {
        mvc.perform(post("/api/room-types").contentType("application/json").content(
                "{\"name\":\"" + "a".repeat(100) + "\",\"description\":\"" + "a".repeat(255) +
                        "\"}")).andExpect(status().isCreated());
        verify(service).createRoomType(any());
    }

    @Test
    void returnsMeaningfulMissingAndConflictResponses() throws Exception {
        when(service.getRoomTypeById(9)).thenThrow(new RoomTypeNotFoundException(9));
        when(service.updateRoomType(eq(9), any())).thenThrow(new RoomTypeNotFoundException(9));
        when(service.createRoomType(any())).thenThrow(new DuplicateRoomTypeException("Deluxe"));
        when(service.updateRoomType(eq(1), any())).thenThrow(new DuplicateRoomTypeException("Deluxe"));
        mvc.perform(get("/api/room-types/9")).andExpect(status().isNotFound()).andExpect(
                jsonPath("$.message").value("Room type not found with ID: 9"));
        mvc.perform(put("/api/room-types/9").contentType("application/json").content(
                "{\"name\":\"Deluxe\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/room-types").contentType("application/json").content(
                "{\"name\":\"Deluxe\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/room-types/1").contentType("application/json").content(
                "{\"name\":\"Deluxe\"}")).andExpect(status().isConflict());
    }

    @Test
    void rejectsMalformedRequestsAndDoesNotExposeDeletion() throws Exception {
        mvc.perform(get("/api/room-types/abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/room-types").contentType("application/json").content("{")).andExpect(
                status().isBadRequest());
        mvc.perform(delete("/api/room-types/1")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
