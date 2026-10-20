package com.suntravels.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HotelRegistrationValidationTest
{
    private HotelService service;
    private MockMvc mvc;

    // Updates the fixture’s up.
    @BeforeEach
    void setUp()
    {
        service = mock( HotelService.class );
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup( new HotelController( service ) ).setValidator( validator ).build();
    }

    // Verifies registration reject oversized field but accept boundary.
    @ParameterizedTest
    @CsvSource( { "name,150", "address,255", "city,100", "country,100", "phone,20", "email,100" } )
    void registrationShouldRejectOversizedFieldButAcceptBoundary( String field, int max ) throws Exception
    {
        String boundary =
                field.equals( "email" ) ? "a@" + "b".repeat( 50 ) + "." + "c".repeat( max - 53 ) : "a".repeat( max );
        mvc.perform( post( "/api/hotels" ).contentType( "application/json" ).content(
                request( field, boundary + "a" ) ) ).andExpect( status().isBadRequest() );
        verifyNoInteractions( service );

        mvc.perform( post( "/api/hotels" ).contentType( "application/json" ).content(
                request( field, boundary ) ) ).andExpect( status().isCreated() );
        verify( service ).createHotel( any() );
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
