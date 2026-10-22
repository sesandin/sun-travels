package com.suntravels.controller;

import com.suntravels.config.SecurityConfig;
import com.suntravels.dto.ReservationResponse;
import com.suntravels.exception.ReservationException;
import com.suntravels.security.DatabaseUserDetailsService;
import com.suntravels.security.JsonAccessDeniedHandler;
import com.suntravels.security.JsonAuthenticationEntryPoint;
import com.suntravels.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest( ReservationController.class )
@Import( { SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class } )
class ReservationControllerTest
{
    final String request = """
            {"customerId":9,"hotelId":1,"contractId":4,"checkIn":"2026-10-25","checkOut":"2026-10-28","rooms":[{"offeringId":7,"adults":2}],"acceptedTotal":690,"acceptedCurrency":"USD"}
            """;
    @Autowired
    MockMvc mvc;
    @MockitoBean
    ReservationService reservations;
    @MockitoBean
    DatabaseUserDetailsService users;

    // Verifies admin and agent can use all reservation endpoints with csrf.
    @Test
    void staffDetailsExposeBookedNightlyRateAndMarkupSnapshots() throws Exception
    {
        var date = java.time.LocalDate.of( 2026, 11, 10 );
        var night = new ReservationResponse.BookedNight( date, new java.math.BigDecimal( "123.20" ),
                new java.math.BigDecimal( "55.00" ), new java.math.BigDecimal( "12.00" ) );
        var room = new ReservationResponse.Item( 5, 6, 5, "Coastal Bay Hotel", 6, "Standard", 2,
                new java.math.BigDecimal( "123.20" ), List.of( night ) );
        when( reservations.detail( 5 ) ).thenReturn( new ReservationResponse( 5, 6, "Customer", null, null,
                date, date.plusDays( 1 ), 1, "CONFIRMED", date.atStartOfDay(), "USD",
                new java.math.BigDecimal( "123.20" ), List.of( room ) ) );
        for( String role : List.of( "ADMIN", "AGENT" ) )
        {
            mvc.perform( get( "/api/reservations/5" ).with( user( "staff" ).roles( role ) ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.rooms[0].nightlyPrices[0].bookedRatePerAdult" ).value( 55.00 ) )
               .andExpect( jsonPath( "$.rooms[0].nightlyPrices[0].bookedMarkupPercentage" ).value( 12.00 ) )
               .andExpect( jsonPath( "$.rooms[0].nightlyPrices[0].sellingPrice" ).value( 123.20 ) );
        }
        mvc.perform( get( "/api/reservations/5" ) ).andExpect( status().isUnauthorized() );
    }

    @Test
    void adminAndAgentCanUseAllReservationEndpointsWithCsrf() throws Exception
    {
        when( reservations.list( "", "", 0, 10, null, null, "" ) ).thenReturn(
                new ReservationResponse.Page( List.of(), 0, 10, 0 ) );
        for( String role : List.of( "ADMIN", "AGENT" ) )
        {
            mvc.perform( get( "/api/reservations" ).with( user( "staff" ).roles( role ) ) ).andExpect( status().isOk() );
            mvc.perform( get( "/api/reservations/1" ).with( user( "staff" ).roles( role ) ) ).andExpect(
                    status().isOk() );
            mvc.perform( post( "/api/reservations" ).with( user( "staff" ).roles( role ) ).with( csrf() ).contentType(
                    "application/json" ).content( request ) ).andExpect( status().isCreated() );
            mvc.perform( post( "/api/reservations/1/cancel" ).with( user( "staff" ).roles( role ) ).with(
                    csrf() ) ).andExpect( status().isOk() );
        }
    }

    @Test
    void directoryReturnsOnlySummaryFieldsWithoutNightlySnapshots() throws Exception
    {
        var date = java.time.LocalDate.of(2026,10,25);
        var summary = new ReservationResponse.Summary(5,6,"Customer",date,date.plusDays(1),
                "CONFIRMED","USD",new java.math.BigDecimal("123.20"),
                List.of(new ReservationResponse.RoomSummary(1,"Hotel","Double",2)),true);
        when(reservations.list("","",0,10,null,null,"")).thenReturn(
                new ReservationResponse.Page(List.of(summary),0,10,1));
        mvc.perform(get("/api/reservations").with(user("staff").roles("AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations[0].totalPrice").value(123.20))
                .andExpect(jsonPath("$.reservations[0].hasOperationalWarnings").value(true))
                .andExpect(jsonPath("$.reservations[0].rooms[0].hotelName").value("Hotel"))
                .andExpect(jsonPath("$.reservations[0].rooms[0].nightlyPrices").doesNotExist())
                .andExpect(jsonPath("$.reservations[0].phone").doesNotExist());
        org.mockito.Mockito.verify(reservations,org.mockito.Mockito.never()).detail(org.mockito.ArgumentMatchers.anyInt());
    }

    // Verifies anonymous requests return401 and authenticated mutations require csrf.
    @Test
    void anonymousRequestsReturn401AndAuthenticatedMutationsRequireCsrf() throws Exception
    {
        mvc.perform( get( "/api/reservations" ) ).andExpect( status().isUnauthorized() );
        mvc.perform( get( "/api/reservations/1" ) ).andExpect( status().isUnauthorized() );
        mvc.perform( post( "/api/reservations" ).contentType( "application/json" ).content( request ) ).andExpect(
                status().isUnauthorized() );
        mvc.perform( post( "/api/reservations/1/cancel" ) ).andExpect( status().isUnauthorized() );
        mvc.perform( post( "/api/reservations" ).with( csrf() ).contentType( "application/json" ).content(
                request ) ).andExpect( status().isUnauthorized() );
        mvc.perform( post( "/api/reservations/1/cancel" ).with( csrf() ) ).andExpect( status().isUnauthorized() );
        mvc.perform( post( "/api/reservations" ).with( user( "agent" ).roles( "AGENT" ) ).contentType(
                "application/json" ).content( request ) ).andExpect( status().isForbidden() );
        mvc.perform( post( "/api/reservations/1/cancel" ).with( user( "admin" ).roles( "ADMIN" ) ) ).andExpect(
                status().isForbidden() );
        verifyNoInteractions( reservations );
    }

    // Verifies invalid payload and wrong role do not reach service.
    @Test
    void invalidPayloadAndWrongRoleDoNotReachService() throws Exception
    {
        mvc.perform( post( "/api/reservations" ).with( user( "staff" ).roles( "AGENT" ) ).with( csrf() ).contentType(
                "application/json" ).content( request.replace( "\"adults\":2", "\"adults\":2.5" ) ) ).andExpect(
                status().isBadRequest() );
        mvc.perform( get( "/api/reservations" ).with( user( "customer" ).roles( "CUSTOMER" ) ) ).andExpect(
                status().isForbidden() );
        verifyNoInteractions( reservations );
    }

    // Verifies price change is structured and does not report success.
    @Test
    void priceChangeIsStructuredAndDoesNotReportSuccess() throws Exception
    {
        var q = new ReservationResponse.Quote( 1, "Ocean", 4, java.time.LocalDate.of( 2026, 10, 25 ),
                java.time.LocalDate.of( 2026, 10, 28 ), 3, "USD", new java.math.BigDecimal( "700.00" ),
                List.of() );
        when( reservations.confirm( any() ) ).thenThrow(
                new ReservationException( 409, "PRICE_CHANGED", "Review updated price", q ) );
        mvc.perform( post( "/api/reservations" ).with( user( "staff" ).roles( "AGENT" ) ).with( csrf() ).contentType(
                "application/json" ).content( request ) ).andExpect( status().isConflict() ).andExpect(
                jsonPath( "$.code" ).value( "PRICE_CHANGED" ) ).andExpect(
                jsonPath( "$.quotation.totalPrice" ).value( 700 ) ).andExpect(
                jsonPath( "$.quotation.markupPercentage" ).doesNotExist() );
    }

    // Verifies adult integer validation has no fixed occupancy ceiling.
    @Test
    void adultIntegerValidationHasNoFixedOccupancyCeiling() throws Exception
    {
        mvc.perform( post( "/api/reservations" ).with( user( "agent" ).roles( "AGENT" ) ).with( csrf() ).contentType(
                "application/json" ).content( request.replace( "\"adults\":2", "\"adults\":12" ) ) ).andExpect(
                status().isCreated() );
        for( String value : List.of( "0", "-1", "2.5", "12.0", "null", "false", "\"12\"", "2147483648",
                "999999999999999999999999999999999" ) )
            mvc.perform( post( "/api/reservations" ).with( user( "agent" ).roles( "AGENT" ) ).with( csrf() ).contentType(
                    "application/json" ).content(
                    request.replace( "\"adults\":2", "\"adults\":" + value ) ) ).andExpect(
                    status().isBadRequest() );
        mvc.perform( post( "/api/reservations" ).with( user( "agent" ).roles( "AGENT" ) ).with( csrf() ).contentType(
                "application/json" ).content( request.replace( ",\"adults\":2", "" ) ) ).andExpect(
                status().isBadRequest() );
    }

    // Verifies both roles receive agent safe operational warnings without a status change.
    @Test
    void bothRolesReceiveAgentSafeOperationalWarningsWithoutAStatusChange() throws Exception
    {
        var date = java.time.LocalDate.of( 2026, 10, 25 );
        var warning = new ReservationResponse.OperationalWarning( 1, "Ocean",
                ReservationResponse.WarningCode.HOTEL_INACTIVE,
                "This hotel is currently inactive. Please verify whether this reservation can still be honored." );
        var response = new ReservationResponse( 101, 9, "Traveller", null, null, date, date.plusDays( 3 ), 3,
                "CONFIRMED", date.atStartOfDay(), "USD", new java.math.BigDecimal( "690.00" ), List.of(),
                List.of( warning ) );
        when( reservations.detail( 101 ) ).thenReturn( response );
        for( String role : List.of( "ADMIN", "AGENT" ) )
            mvc.perform( get( "/api/reservations/101" ).with( user( "staff" ).roles( role ) ) ).andExpect(
                    status().isOk() ).andExpect( jsonPath( "$.status" ).value( "CONFIRMED" ) ).andExpect(
                    jsonPath( "$.warnings[0].code" ).value( "HOTEL_INACTIVE" ) ).andExpect(
                    jsonPath( "$.warnings[0].hotelName" ).value( "Ocean" ) ).andExpect(
                    jsonPath( "$.warnings[0].message" ).value( warning.message() ) ).andExpect(
                    jsonPath( "$.warnings[0].markupPercentage" ).doesNotExist() );
    }

    // Verifies date filters are parsed and malformed dates are rejected.
    @Test
    void dateFiltersAreParsedAndMalformedDatesAreRejected() throws Exception
    {
        mvc.perform( get( "/api/reservations" ).with( user( "agent" ).roles( "AGENT" ) ).param( "stayFrom",
                "2026-10-25" ).param( "stayTo", "2026-10-27" ) ).andExpect( status().isOk() );
        verify( reservations ).list( "", "", 0, 10, java.time.LocalDate.of( 2026, 10, 25 ),
                java.time.LocalDate.of( 2026, 10, 27 ), "" );
        mvc.perform( get( "/api/reservations" ).with( user( "agent" ).roles( "AGENT" ) ).param( "stayFrom",
                "invalid" ) ).andExpect( status().isBadRequest() );
    }

    // Verifies stay period is passed with the ten record default.
    @Test
    void stayPeriodIsPassedWithTheTenRecordDefault() throws Exception
    {
        mvc.perform( get( "/api/reservations" ).with( user( "agent" ).roles( "AGENT" ) ).param( "status",
                "CONFIRMED" ).param( "stayPeriod", "ONGOING" ) ).andExpect( status().isOk() );
        verify( reservations ).list( "", "CONFIRMED", 0, 10, null, null, "ONGOING" );
    }

    // Verifies past and other periods reach the service alongside every other filter.
    @Test
    void pastAndOtherPeriodsReachTheServiceAlongsideEveryOtherFilter() throws Exception
    {
        for( String period : List.of( "PAST", "UPCOMING", "ONGOING", "ALL" ) )
        {
            when( reservations.list( "Traveller", "CONFIRMED", 0, 10, java.time.LocalDate.of( 2026, 10, 1 ),
                    java.time.LocalDate.of( 2026, 11, 18 ), period ) ).thenReturn(
                    new ReservationResponse.Page( List.of(), 0, 10, 0 ) );
            mvc.perform( get( "/api/reservations" ).with( user( "agent" ).roles( "AGENT" ) ).param( "query",
                    "Traveller" ).param( "status", "CONFIRMED" ).param( "stayPeriod", period ).param( "stayFrom",
                    "2026-10-01" ).param( "stayTo", "2026-11-18" ) ).andExpect( status().isOk() ).andExpect(
                    jsonPath( "$.totalElements" ).value( 0 ) ).andExpect( jsonPath( "$.size" ).value( 10 ) );
            verify( reservations ).list( "Traveller", "CONFIRMED", 0, 10, java.time.LocalDate.of( 2026, 10, 1 ),
                    java.time.LocalDate.of( 2026, 11, 18 ), period );
        }
    }
}
