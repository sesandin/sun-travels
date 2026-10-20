package com.suntravels.controller;

import com.suntravels.dto.OfferingDirectoryResponse;
import com.suntravels.dto.OfferingRequest;
import com.suntravels.dto.OfferingResponse;
import com.suntravels.dto.SeasonalRateResponse;
import com.suntravels.entity.OfferingStatus;
import com.suntravels.exception.OfferingException;
import com.suntravels.exception.OfferingExceptionHandler;
import com.suntravels.service.OfferingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith( MockitoExtension.class )
class OfferingControllerTest
{
    final String offering = """
            {"roomTypeId":2,"ratePerPersonPerNight":100.25,"contractedRoomCount":10,"maxAdults":2,"status":"ENABLED"}
            """;
    final String season = """
            {"validFrom":"2026-06-01","validTo":"2026-06-30","ratePerPersonPerNight":150.00}
            """;
    @Mock
    OfferingService service;
    MockMvc mvc;
    LocalValidatorFactoryBean validator;

    // Builds a response fixture for the tested operation.
    OfferingResponse response()
    {
        return new OfferingResponse( 3, 1, 2, "Double", BigDecimal.TEN, 10, 2, OfferingStatus.ENABLED, "USD" );
    }

    // Builds a seasonal-rate response fixture.
    SeasonalRateResponse seasonal()
    {
        return new SeasonalRateResponse( 4, 3, LocalDate.of( 2026, 6, 1 ), LocalDate.of( 2026, 6, 30 ),
                BigDecimal.TEN );
    }

    // Configures the component and its test dependencies.
    @BeforeEach
    void setup()
    {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup( new OfferingController( service ) ).setControllerAdvice(
                new OfferingExceptionHandler() ).setValidator( validator ).build();
    }

    // Releases resources created for the test.
    @AfterEach
    void close()
    {
        validator.close();
    }

    // Verifies lists global directory dto without entities.
    @Test
    void listsGlobalDirectoryDtoWithoutEntities() throws Exception
    {
        var row = new OfferingDirectoryResponse( 3, 7, "Ocean Hotel",
                com.suntravels.entity.HotelStatus.INACTIVE, 1, "C001",
                com.suntravels.entity.ContractStatus.TERMINATED, LocalDate.of( 2026, 1, 1 ),
                LocalDate.of( 2026, 12, 31 ), 2, "Double", new BigDecimal( "100.25" ), "USD", 10, 2,
                OfferingStatus.SUSPENDED, 2L );
        when( service.directory() ).thenReturn( List.of( row ) );
        mvc.perform( get( "/api/offerings" ) ).andExpect( status().isOk() ).andExpect(
                jsonPath( "$[0].hotelName" ).value( "Ocean Hotel" ) ).andExpect(
                jsonPath( "$[0].contractCode" ).value( "C001" ) ).andExpect(
                jsonPath( "$[0].contractStatus" ).value( "TERMINATED" ) ).andExpect(
                jsonPath( "$[0].ratePerPersonPerNight" ).value( 100.25 ) ).andExpect(
                jsonPath( "$[0].currencyCode" ).value( "USD" ) ).andExpect(
                jsonPath( "$[0].contractedRoomCount" ).value( 10 ) ).andExpect(
                jsonPath( "$[0].seasonalRateCount" ).value( 2 ) ).andExpect(
                jsonPath( "$[0].contract" ).doesNotExist() ).andExpect( jsonPath( "$[0].hotel" ).doesNotExist() );
        verify( service ).directory();
    }

    // Verifies global directory empty result is an array.
    @Test
    void globalDirectoryEmptyResultIsAnArray() throws Exception
    {
        when( service.directory() ).thenReturn( List.of() );
        mvc.perform( get( "/api/offerings" ) ).andExpect( status().isOk() ).andExpect( content().json( "[]" ) );
    }

    // Verifies reads dtos.
    @Test
    void readsDtos() throws Exception
    {
        when( service.list( 1 ) ).thenReturn( List.of( response() ) );
        when( service.get( 3 ) ).thenReturn( response() );
        when( service.seasonalRates( 3 ) ).thenReturn( List.of( seasonal() ) );
        mvc.perform( get( "/api/contracts/1/offerings" ) ).andExpect( status().isOk() ).andExpect(
                jsonPath( "$[0].roomTypeName" ).value( "Double" ) );
        mvc.perform( get( "/api/offerings/3" ) ).andExpect( status().isOk() ).andExpect(
                jsonPath( "$.currencyCode" ).value( "USD" ) );
        mvc.perform( get( "/api/offerings/3/seasonal-rates" ) ).andExpect( status().isOk() ).andExpect(
                jsonPath( "$[0].seasonalRateId" ).value( 4 ) );
    }

    // Verifies creates and updates offering payloads.
    @Test
    void createsAndUpdatesOfferingPayloads() throws Exception
    {
        when( service.create( eq( 1 ), any() ) ).thenReturn( response() );
        when( service.update( eq( 3 ), any() ) ).thenReturn( response() );
        mvc.perform( post( "/api/contracts/1/offerings" ).contentType( "application/json" ).content(
                offering ) ).andExpect( status().isCreated() );
        mvc.perform( put( "/api/offerings/3" ).contentType( "application/json" ).content( offering ) ).andExpect(
                status().isOk() );
        verify( service ).create( 1,
                new OfferingRequest( 2, new BigDecimal( "100.25" ), 10, 2, OfferingStatus.ENABLED ) );
    }

    // Verifies controlled patch payloads.
    @Test
    void controlledPatchPayloads() throws Exception
    {
        when( service.rate( eq( 3 ), any() ) ).thenReturn( response() );
        when( service.status( eq( 3 ), any() ) ).thenReturn( response() );
        mvc.perform( patch( "/api/offerings/3/rate" ).contentType( "application/json" ).content(
                "{\"ratePerPersonPerNight\":1}" ) ).andExpect( status().isOk() );
        mvc.perform( patch( "/api/offerings/3/status" ).contentType( "application/json" ).content(
                "{\"status\":\"SUSPENDED\"}" ) ).andExpect( status().isOk() );
    }

    // Verifies creates and updates seasonal dtos.
    @Test
    void createsAndUpdatesSeasonalDtos() throws Exception
    {
        when( service.createSeason( eq( 3 ), any() ) ).thenReturn( seasonal() );
        when( service.updateSeason( eq( 3 ), eq( 4 ), any() ) ).thenReturn( seasonal() );
        mvc.perform( post( "/api/offerings/3/seasonal-rates" ).contentType( "application/json" ).content(
                season ) ).andExpect( status().isCreated() );
        mvc.perform( put( "/api/offerings/3/seasonal-rates/4" ).contentType( "application/json" ).content(
                season ) ).andExpect( status().isOk() );
    }

    // Verifies invalid offering fields do not call service.
    @Test
    void invalidOfferingFieldsDoNotCallService() throws Exception
    {
        for( String body : List.of( "{}", offering.replace( "100.25", "0" ),
                offering.replace( "100.25", "100000000" ), offering.replace( "100.25", "1.001" ),
                offering.replace( "10,", "0," ), offering.replace( "\"maxAdults\":2", "\"maxAdults\":-1" ) ) )
        {
            mvc.perform( post( "/api/contracts/1/offerings" ).contentType( "application/json" ).content(
                    body ) ).andExpect( status().isBadRequest() ).andExpect( jsonPath( "$.fieldErrors" ).exists() );
            mvc.perform( put( "/api/offerings/3" ).contentType( "application/json" ).content( body ) ).andExpect(
                    status().isBadRequest() );
        }
        verifyNoInteractions( service );
    }

    // Verifies validates seasonal writes.
    @Test
    void validatesSeasonalWrites() throws Exception
    {
        for( String body : List.of( "{}", season.replace( "150.00", "0" ), season.replace( "150.00", "1.001" ),
                season.replace( "2026-06-01", "2026-07-01" ) ) )
        {
            mvc.perform( post( "/api/offerings/3/seasonal-rates" ).contentType( "application/json" ).content(
                    body ) ).andExpect( status().isBadRequest() );
            mvc.perform( put( "/api/offerings/3/seasonal-rates/4" ).contentType( "application/json" ).content(
                    body ) ).andExpect( status().isBadRequest() );
        }
        verifyNoInteractions( service );
    }

    // Verifies malformed dates ids and statuses return400.
    @Test
    void malformedDatesIdsAndStatusesReturn400() throws Exception
    {
        mvc.perform( get( "/api/offerings/abc" ) ).andExpect( status().isBadRequest() );
        mvc.perform( post( "/api/contracts/1/offerings" ).contentType( "application/json" ).content(
                offering.replace( "ENABLED", "SOLD_OUT" ) ) ).andExpect( status().isBadRequest() );
        mvc.perform( post( "/api/offerings/3/seasonal-rates" ).contentType( "application/json" ).content(
                season.replace( "2026-06-01", "bad" ) ) ).andExpect( status().isBadRequest() );
        mvc.perform( patch( "/api/offerings/3/status" ).contentType( "application/json" ).content( "{}" ) ).andExpect(
                status().isBadRequest() );
        verifyNoInteractions( service );
    }

    // Verifies safe404 and409 errors.
    @Test
    void safe404And409Errors() throws Exception
    {
        when( service.get( 9 ) ).thenThrow( OfferingException.missing( "Offering", 9 ) );
        when( service.createSeason( eq( 3 ), any() ) ).thenThrow(
                OfferingException.conflict( "Seasonal period overlaps" ) );
        mvc.perform( get( "/api/offerings/9" ) ).andExpect( status().isNotFound() );
        mvc.perform( post( "/api/offerings/3/seasonal-rates" ).contentType( "application/json" ).content(
                season ) ).andExpect( status().isConflict() ).andExpect(
                jsonPath( "$.message" ).value( "Seasonal period overlaps" ) );
    }

    // Verifies database and lock failures do not leak details.
    @Test
    void databaseAndLockFailuresDoNotLeakDetails() throws Exception
    {
        when( service.create( eq( 1 ), any() ) ).thenThrow( new DataIntegrityViolationException( "secret SQL" ) );
        when( service.status( eq( 3 ), any() ) ).thenThrow(
                new PessimisticLockingFailureException( "secret lock SQL" ) );
        mvc.perform( post( "/api/contracts/1/offerings" ).contentType( "application/json" ).content(
                offering ) ).andExpect( status().isConflict() ).andExpect(
                jsonPath( "$.message" ).value( "Conflicting or concurrent update. Refresh and try again." ) );
        mvc.perform( patch( "/api/offerings/3/status" ).contentType( "application/json" ).content(
                "{\"status\":\"ENABLED\"}" ) ).andExpect( status().isConflict() ).andExpect(
                jsonPath( "$.message" ).value( "Conflicting or concurrent update. Refresh and try again." ) );
    }

    // Verifies no deletion endpoint.
    @Test
    void noDeletionEndpoint() throws Exception
    {
        mvc.perform( delete( "/api/offerings/3" ) ).andExpect( status().isMethodNotAllowed() );
    }

    // Verifies seasonal rate deletion is not supported.
    @Test
    void seasonalRateDeletionIsNotSupported() throws Exception
    {
        mvc.perform( delete( "/api/offerings/3/seasonal-rates/4" ) )
           .andExpect( status().isMethodNotAllowed() );
        verifyNoInteractions( service );
    }
}
