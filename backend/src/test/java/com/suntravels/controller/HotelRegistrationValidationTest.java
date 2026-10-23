package com.suntravels.controller;

import static org.mockito.Mockito.when;
import com.suntravels.service.HotelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HotelRegistrationValidationTest
{
    @org.junit.jupiter.api.Test
    void duplicatesAndConcurrentUniqueConflictsReturn409() throws Exception
    {
        when(service.createHotel(any())).thenThrow(new com.suntravels.exception.DuplicateHotelException());
        mvc.perform(post("/api/hotels").contentType("application/json").content(request("name","Hotel")))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value(com.suntravels.exception.DuplicateHotelException.MESSAGE));
        when(service.updateHotel(org.mockito.ArgumentMatchers.eq(1),any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("Duplicate entry for key 'hotel.uq_hotel_identity'"));
        mvc.perform(put("/api/hotels/1").contentType("application/json").content(request("name","Hotel")))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value(com.suntravels.exception.DuplicateHotelException.MESSAGE));
    }
    private HotelService service;
    private MockMvc mvc;

    // Updates the fixture’s up.
    @BeforeEach
    void setUp()
    {
        service = mock( HotelService.class );
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup( new HotelController( service ) )
                .setControllerAdvice( new com.suntravels.exception.HotelValidationExceptionHandler() )
                .setValidator( validator ).build();
    }

    // Verifies registration reject oversized field but accept boundary.
    @ParameterizedTest
    @CsvSource( { "name,150", "address,255", "city,100", "phone,20", "email,100" } )
    void registrationShouldRejectOversizedFieldButAcceptBoundary( String field, int max ) throws Exception
    {
        String boundary =
                field.equals( "email" ) ? "a@" + "b".repeat( 50 ) + "." + "c".repeat( max - 53 ) : "a".repeat( max );
        mvc.perform( post( "/api/hotels" ).contentType( "application/json" ).content(
                request( field, boundary + "a" ) ) ).andExpect( status().isBadRequest() )
                .andExpect( org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.fieldErrors." + field ).isString() );
        verifyNoInteractions( service );

        mvc.perform( post( "/api/hotels" ).contentType( "application/json" ).content(
                request( field, boundary ) ) ).andExpect( status().isCreated() );
        verify( service ).createHotel( any() );
        mvc.perform( put( "/api/hotels/1" ).contentType( "application/json" ).content(
                request( field, boundary + "a" ) ) ).andExpect( status().isBadRequest() );
        mvc.perform( put( "/api/hotels/1" ).contentType( "application/json" ).content(
                request( field, boundary ) ) ).andExpect( status().isOk() );
        verify( service ).updateHotel( org.mockito.ArgumentMatchers.eq( 1 ), any() );
    }

    @ParameterizedTest
    @CsvSource( { "Sri Lanka,true", "LK,true", "lk,true", "United States,true", "ZZ,false", "Atlantis,false", "'',false" } )
    void countryMustBeRecognizedForRegistrationAndEditing( String country, boolean valid ) throws Exception
    {
        String body = request( "country", country );
        mvc.perform( post( "/api/hotels" ).contentType( "application/json" ).content( body ) )
                .andExpect( valid ? status().isCreated() : status().isBadRequest() );
        mvc.perform( put( "/api/hotels/1" ).contentType( "application/json" ).content( body ) )
                .andExpect( valid ? status().isOk() : status().isBadRequest() );
        if( !valid ) verifyNoInteractions( service );
    }

    // Builds a valid request fixture for the tested operation.
    private String request( String field, String value )
    {
        String json = """
                {"name":"Hotel","address":"Beach Road","city":"Galle",
                 "country":"Sri Lanka","phone":"123","email":"a@example.com",
                 "starRating":4,"status":"ACTIVE"}
                """;
        return json.replaceFirst( "\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":\"" + value + "\"" );
    }
}
