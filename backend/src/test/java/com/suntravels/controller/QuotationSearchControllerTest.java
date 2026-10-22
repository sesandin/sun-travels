package com.suntravels.controller;

import com.suntravels.dto.EvaluatedHotelPage;
import com.suntravels.dto.OfferingOccupancy;
import com.suntravels.dto.QuotationSearchFilters;
import com.suntravels.dto.QuotationSearchRequest;
import com.suntravels.dto.QuotationSearchResponse;
import com.suntravels.dto.QuotationSelectionRequest;
import com.suntravels.dto.ReservationRequest;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.exception.QuotationSearchExceptionHandler;
import com.suntravels.service.QuotationSearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith( MockitoExtension.class )
class QuotationSearchControllerTest
{
    final String json = """
            {"checkIn":"2026-10-24","checkOut":"2026-10-27","rooms":[{"adults":2},{"adults":1}],"city":"Colombo","hotelId":null,"roomTypeId":null}
            """;
    @Mock
    QuotationSearchService service;
    MockMvc mvc;
    LocalValidatorFactoryBean validator;

    @Test
    void exposesCountryAwareCityAndHotelSuggestions() throws Exception
    {
        when( service.citySuggestions( "Sp", "US" ) ).thenReturn( List.of(
                new com.suntravels.dto.CitySuggestion( "Springfield", "United States" ) ) );
        mvc.perform( get( "/api/search/cities" ).param( "term", "Sp" ).param( "country", "US" ) )
                .andExpect( status().isOk() ).andExpect( jsonPath( "$[0].city" ).value( "Springfield" ) )
                .andExpect( jsonPath( "$[0].country" ).value( "United States" ) );
        when( service.hotelSuggestions( "Oc", "Springfield", "US" ) ).thenReturn( List.of(
                new com.suntravels.dto.HotelSuggestion( 7, "Ocean", "Springfield", "United States" ) ) );
        mvc.perform( get( "/api/search/hotels" ).param( "term", "Oc" ).param( "city", "Springfield" ).param( "country", "US" ) )
                .andExpect( status().isOk() ).andExpect( jsonPath( "$[0].country" ).value( "United States" ) );
    }

    @Test
    void exposesLightweightHotelLookupAndIndependentRoomTypes() throws Exception
    {
        var hotel = new com.suntravels.dto.HotelSuggestion( 7, "Ocean", "Colombo", "Sri Lanka" );
        when( service.hotelSuggestions( "Oc", null, null ) ).thenReturn( List.of( hotel ) );
        mvc.perform( get( "/api/search/hotels" ).param( "term", "Oc" ) ).andExpect( status().isOk() )
                .andExpect( jsonPath( "$[0].hotelId" ).value( 7 ) )
                .andExpect( jsonPath( "$[0].length()" ).value( 4 ) );
        when( service.hotelOption( 7 ) ).thenReturn( hotel );
        mvc.perform( get( "/api/search/hotels/7" ) ).andExpect( status().isOk() )
                .andExpect( jsonPath( "$.name" ).value( "Ocean" ) );
        when( service.roomTypeFilters() ).thenReturn( List.of( new QuotationSearchFilters.RoomTypeOption( 3, "Double" ) ) );
        mvc.perform( get( "/api/search/room-types" ) ).andExpect( status().isOk() )
                .andExpect( jsonPath( "$[0].name" ).value( "Double" ) );
        org.mockito.Mockito.verify( service, org.mockito.Mockito.never() ).filters();
    }

    @ParameterizedTest
    @CsvSource( value = { "Colombo,NULL", "NULL,7", "Colombo,7", "'   ',7" }, nullValues = "NULL" )
    void hotelPageAcceptsCityOrHotel( String city, Integer hotelId ) throws Exception
    {
        when( service.evaluatedPage( any(), org.mockito.ArgumentMatchers.isNull() ) ).thenReturn(
                new EvaluatedHotelPage( LocalDate.of( 2026, 10, 15 ), LocalDate.of( 2026, 10, 24 ),
                        LocalDate.of( 2026, 10, 27 ), 3, List.of(), null ) );
        String body = json.replace( "\"city\":\"Colombo\"", "\"city\":" + (city == null ? "null" : "\"" + city + "\"") )
                .replace( "\"hotelId\":null", "\"hotelId\":" + hotelId );
        mvc.perform( post( "/api/search/hotel-pages" ).contentType( "application/json" ).content( body ) )
                .andExpect( status().isOk() );
    }

    @ParameterizedTest
    @CsvSource( value = { "NULL", "''", "'   '" }, nullValues = "NULL" )
    void hotelPageRejectsMissingOrBlankDestinationBeforeSearch( String city ) throws Exception
    {
        String body = json.replace( "\"city\":\"Colombo\"", "\"city\":" + (city == null ? "null" : "\"" + city + "\"") );
        mvc.perform( post( "/api/search/hotel-pages" ).contentType( "application/json" ).content( body ) )
                .andExpect( status().isBadRequest() ).andExpect( jsonPath( "$.message" ).value( "Enter a city or select a hotel" ) );
        verifyNoInteractions( service );
    }

    @Test
    void legacySearchStillAcceptsNoDestination() throws Exception
    {
        when( service.search( any() ) ).thenReturn( new QuotationSearchResponse(
                LocalDate.of( 2026, 10, 15 ), LocalDate.of( 2026, 10, 24 ), LocalDate.of( 2026, 10, 27 ),
                3, List.of(), 0, false, "INDIVIDUAL_ROOM_TYPES", "Informational" ) );
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" )
                .content( json.replace( "\"city\":\"Colombo\"", "\"city\":null" ) ) ).andExpect( status().isOk() );
    }

    @Test
    void paginatedEndpointFiltersAfterEvaluationAndKeepsCandidateCursor() throws Exception
    {
        var available = new EvaluatedHotelPage.Offering( 1, 1, 3, null,
                QuotationSearchResponse.AvailabilityStatus.AVAILABLE, null, null );
        var unavailable = new EvaluatedHotelPage.Offering( 2, 2, 3, null,
                QuotationSearchResponse.AvailabilityStatus.NOT_AVAILABLE, null, null );
        when( service.evaluatedPage( any(), org.mockito.ArgumentMatchers.eq( 20 ) ) ).thenReturn(
                new EvaluatedHotelPage( LocalDate.of( 2026, 10, 15 ), LocalDate.of( 2026, 10, 24 ),
                        LocalDate.of( 2026, 10, 27 ), 3, List.of(
                        new EvaluatedHotelPage.Hotel( 21, "Available hotel", "Colombo", List.of( available, unavailable ) ),
                        new EvaluatedHotelPage.Hotel( 22, "Full hotel", "Colombo", List.of( unavailable ) ) ), 40 ) );
        mvc.perform( post( "/api/search/hotel-pages" ).param( "afterHotelId", "20" )
                                                      .contentType( "application/json" ).content( json ) ).andExpect( status().isOk() )
           .andExpect( jsonPath( "$.hotels.length()" ).value( 1 ) )
           .andExpect( jsonPath( "$.hotels[0].offerings.length()" ).value( 1 ) )
           .andExpect( jsonPath( "$.hotels[0].offerings[0].availabilityStatus" ).value( "AVAILABLE" ) )
           .andExpect( jsonPath( "$.nextAfterHotelId" ).value( 40 ) );
    }

    @Test
    void paginatedEndpointPreservesEmptyPageWithContinuation() throws Exception
    {
        when( service.evaluatedPage( any(), org.mockito.ArgumentMatchers.isNull() ) ).thenReturn(
                new EvaluatedHotelPage( LocalDate.of( 2026, 10, 15 ), LocalDate.of( 2026, 10, 24 ),
                        LocalDate.of( 2026, 10, 27 ), 3, List.of(), 20 ) );
        mvc.perform( post( "/api/search/hotel-pages" ).contentType( "application/json" ).content( json ) )
           .andExpect( status().isOk() ).andExpect( jsonPath( "$.hotels" ).isEmpty() )
           .andExpect( jsonPath( "$.nextAfterHotelId" ).value( 20 ) );
    }

    @Test
    void paginatedEndpointRejectsMalformedCursorAndInvalidRooms() throws Exception
    {
        mvc.perform( post( "/api/search/hotel-pages" ).param( "afterHotelId", "bad" )
                                                      .contentType( "application/json" ).content( json ) ).andExpect( status().isBadRequest() );
        mvc.perform( post( "/api/search/hotel-pages" ).contentType( "application/json" )
                                                      .content( json.replace( "\"adults\":2", "\"adults\":0" ) ) ).andExpect( status().isBadRequest() );
        verifyNoInteractions( service );
    }

    // Configures the component and its test dependencies.
    @BeforeEach
    void setup()
    {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup( new QuotationSearchController( service ) ).setControllerAdvice(
                new QuotationSearchExceptionHandler() ).setValidator( validator ).build();
    }

    // Releases resources created for the test.
    @AfterEach
    void close()
    {
        validator.close();
    }

    // Verifies accepts structured rooms and returns successful empty envelope.
    @Test
    void acceptsStructuredRoomsAndReturnsSuccessfulEmptyEnvelope() throws Exception
    {
        when( service.search( any() ) ).thenReturn(
                new QuotationSearchResponse( LocalDate.of( 2026, 10, 15 ), LocalDate.of( 2026, 10, 24 ),
                        LocalDate.of( 2026, 10, 27 ), 3, List.of(), 0, false, "INDIVIDUAL_ROOM_TYPES",
                        "Informational" ) );
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content( json ) ).andExpect(
                status().isOk() ).andExpect( jsonPath( "$.quotations" ).isArray() ).andExpect(
                jsonPath( "$.nights" ).value( 3 ) );
        var capture = ArgumentCaptor.forClass( QuotationSearchRequest.class );
        verify( service ).search( capture.capture() );
        Assertions.assertEquals( List.of( 2, 1 ),
                capture.getValue().rooms().stream().map( r -> r.adults() ).toList() );
    }

    // Verifies publishes server business date and limits.
    @Test
    void publishesServerBusinessDateAndLimits() throws Exception
    {
        when( service.limits() ).thenReturn(
                new QuotationSearchResponse.Limits( LocalDate.of( 2026, 10, 15 ), 8, 31, 730, 20,
                        "INDIVIDUAL_ROOM_TYPES" ) );
        mvc.perform( get( "/api/search/limits" ) ).andExpect( status().isOk() ).andExpect(
                jsonPath( "$.businessDate" ).value( "2026-10-15" ) ).andExpect( jsonPath( "$.maxRooms" ).value( 8 ) );
    }

    // Verifies occupancy exposes only safe identity and adult limits.
    @Test
    void occupancyExposesOnlySafeIdentityAndAdultLimits() throws Exception
    {
        when( service.occupancy( 7, 3, LocalDate.of( 2026, 10, 24 ), LocalDate.of( 2026, 10, 27 ),
                null ) ).thenReturn( List.of( new OfferingOccupancy( 8, 3, "Garden View", 2 ) ) );
        mvc.perform(
                get( "/api/search/occupancy" ).param( "hotelId", "7" ).param( "roomTypeId", "3" ).param( "checkIn",
                        "2026-10-24" ).param( "checkOut", "2026-10-27" ) ).andExpect( status().isOk() ).andExpect(
                jsonPath( "$[0].maxAdults" ).value( 2 ) ).andExpect(
                jsonPath( "$[0].roomTypeName" ).value( "Garden View" ) ).andExpect(
                jsonPath( "$[0].markup" ).doesNotExist() ).andExpect(
                jsonPath( "$[0].baseRate" ).doesNotExist() ).andExpect(
                jsonPath( "$[0].contractedRooms" ).doesNotExist() ).andExpect(
                jsonPath( "$[0].length()" ).value( 4 ) );
    }

    // Verifies occupancy requires specific filters and iso dates.
    @Test
    void occupancyRequiresSpecificFiltersAndIsoDates() throws Exception
    {
        mvc.perform( get( "/api/search/occupancy" ) ).andExpect( status().isBadRequest() );
        mvc.perform(
                get( "/api/search/occupancy" ).param( "hotelId", "7" ).param( "roomTypeId", "3" ).param( "checkIn",
                        "invalid" ).param( "checkOut", "2026-10-27" ) ).andExpect( status().isBadRequest() );
        verifyNoInteractions( service );
    }

    // Verifies publishes least privilege search filter options.
    @Test
    void publishesLeastPrivilegeSearchFilterOptions() throws Exception
    {
        when( service.filters() ).thenReturn( new QuotationSearchFilters(
                List.of( new QuotationSearchFilters.HotelOption( 7, "Ocean", "Colombo" ) ),
                List.of( new QuotationSearchFilters.RoomTypeOption( 3, "Double" ) ) ) );
        mvc.perform( get( "/api/search/filters" ) ).andExpect( status().isOk() ).andExpect(
                jsonPath( "$.hotels[0].name" ).value( "Ocean" ) ).andExpect(
                jsonPath( "$.roomTypes[0].roomTypeId" ).value( 3 ) ).andExpect(
                jsonPath( "$.hotels[0].phone" ).doesNotExist() );
    }

    // Verifies serializes explicit availability and selling prices without internal rates.
    @Test
    void serializesExplicitAvailabilityAndSellingPricesWithoutInternalRates() throws Exception
    {
        var in = LocalDate.of( 2026, 10, 24 );
        var out = in.plusDays( 1 );
        var night = new QuotationSearchResponse.NightlyPrice( in, "BASE", null,
                new java.math.BigDecimal( "230.00" ) );
        var room = new QuotationSearchResponse.RoomAllocation( 1, 2, 8, 3, "Garden View", 3, 3, 1, 2,
                new java.math.BigDecimal( "230.00" ), List.of( night ) );
        var quote = new QuotationSearchResponse.Quotation( 7, "Ocean", "Colombo", 4, "C004", "USD", in, out, 1,
                List.of( room ), room.totalPrice(), "INFORMATIONAL" );
        var unavailable = new QuotationSearchResponse.UnavailableOption( 9, "Full Hotel", "Colombo", 5,
                "C005", "USD", QuotationSearchResponse.AvailabilityStatus.NOT_AVAILABLE,
                List.of( new QuotationSearchResponse.UnavailableOffering( 10, 3, "Garden View", 2, 0 ) ),
                List.of( new QuotationSearchResponse.UnavailabilityReason(
                        QuotationSearchResponse.ReasonCode.INSUFFICIENT_INVENTORY,
                        "Only 0 rooms available; 1 requested." ) ), room.totalPrice() );
        when( service.search( any() ) ).thenReturn(
                new QuotationSearchResponse( in, in, out, 1, List.of( quote ), 1, false, "INDIVIDUAL_ROOM_TYPES",
                        "Informational", List.of( unavailable ), 1, false ) );
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content( json ) ).andExpect(
                status().isOk() ).andExpect(
                jsonPath( "$.quotations[0].availabilityStatus" ).value( "AVAILABLE" ) ).andExpect(
                jsonPath( "$.unavailableOptions[0].availabilityStatus" ).value( "NOT_AVAILABLE" ) ).andExpect(
                jsonPath( "$.unavailableOptions[0].reasons[0].code" ).value( "INSUFFICIENT_INVENTORY" ) ).andExpect(
                jsonPath( "$.quotations[0].rooms[0].nightlyPrices[0].sellingPrice" ).value( 230 ) ).andExpect(
                jsonPath( "$.quotations[0].rooms[0].nightlyPrices[0].hotelierRate" ).doesNotExist() ).andExpect(
                jsonPath( "$.quotations[0].rooms[0].nightlyPrices[0].markupPercentage" ).doesNotExist() );
    }

    // Verifies validates required and nested room fields.
    @Test
    void validatesRequiredAndNestedRoomFields() throws Exception
    {
        for( String body : List.of( "{}", json.replace( "2}", "0}" ), json.replace( "2}", "-1}" ),
                json.replace( "2}", "2147483648}" ), json.replace( "2}", "null}" ),
                json.replace( "[{\"adults\":2},{\"adults\":1}]", "[]" ),
                json.replace( "[{\"adults\":2},{\"adults\":1}]", "[null]" ) ) )
            mvc.perform(
                    post( "/api/search/quotations" ).contentType( "application/json" ).content( body ) ).andExpect(
                    status().isBadRequest() );
        verifyNoInteractions( service );
    }

    // Verifies rejects fractional and string adults and malformed calendar dates.
    @Test
    void rejectsFractionalAndStringAdultsAndMalformedCalendarDates() throws Exception
    {
        for( String body : List.of( json.replace( "2}", "2.5}" ), json.replace( "2}", "\"2\"}" ),
                json.replace( "2026-10-24", "2026-02-30" ), json.replace( "2026-10-24", "2026-10-24T00:00:00" ),
                json.replace( "\"hotelId\":null", "\"hotelId\":1.5" ), "{" ) )
            mvc.perform(
                    post( "/api/search/quotations" ).contentType( "application/json" ).content( body ) ).andExpect(
                    status().isBadRequest() );
        verifyNoInteractions( service );
    }

    // Verifies returns business validation not found and safe consistency messages.
    @Test
    void returnsBusinessValidationNotFoundAndSafeConsistencyMessages() throws Exception
    {
        when( service.search( any() ) ).thenThrow( QuotationSearchException.invalid( "Checkout must be later" ),
                new QuotationSearchException( 404, "Selected hotel was not found" ),
                QuotationSearchException.inconsistent() );
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content( json ) ).andExpect(
                status().isBadRequest() ).andExpect( jsonPath( "$.message" ).value( "Checkout must be later" ) );
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content( json ) ).andExpect(
                status().isNotFound() );
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content( json ) ).andExpect(
                status().isConflict() );
    }

    // Verifies database errors do not leak sql.
    @Test
    void databaseErrorsDoNotLeakSql() throws Exception
    {
        when( service.search( any() ) ).thenThrow( new DataAccessResourceFailureException( "secret SQL" ) );
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content( json ) ).andExpect(
                status().isServiceUnavailable() ).andExpect( jsonPath( "$.message" ).value(
                "Accommodation search is temporarily unavailable. Please retry." ) );
    }

    // Verifies does not expose booking or confirmation endpoint.
    @Test
    void doesNotExposeBookingOrConfirmationEndpoint() throws Exception
    {
        mvc.perform( post( "/api/search/confirm" ) ).andExpect( status().isNotFound() );
    }

    // Verifies preserves exact selection payload.
    @Test
    void preservesExactSelectionPayload() throws Exception
    {
        mvc.perform( post( "/api/search/selection" ).contentType( "application/json" ).content( """
                {"hotelId":7,"contractId":4,"checkIn":"2026-10-24","checkOut":"2026-10-27","rooms":[{"offeringId":8,"adults":1},{"offeringId":9,"adults":2}]}
                """ ) ).andExpect( status().isOk() );
        var capture = ArgumentCaptor.forClass( QuotationSelectionRequest.class );
        verify( service ).customize( capture.capture() );
        Assertions.assertEquals( List.of( 8, 9 ),
                capture.getValue().rooms().stream().map( ReservationRequest.Room::offeringId ).toList() );
        Assertions.assertEquals( List.of( 1, 2 ),
                capture.getValue().rooms().stream().map( ReservationRequest.Room::adults ).toList() );
    }

    // Verifies accepts above ten but rejects non integer and overflow adults across quotation apis.
    @Test
    void acceptsAboveTenButRejectsNonIntegerAndOverflowAdultsAcrossQuotationApis() throws Exception
    {
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content(
                json.replace( "2}", "12}" ) ) ).andExpect( status().isOk() );
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content(
                json.replace( "2}", "2147483647}" ) ) ).andExpect( status().isOk() );
        mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content(
                json.replace( "{\"adults\":2}", "{}" ) ) ).andExpect( status().isBadRequest() );
        String selection = """
                {"hotelId":7,"contractId":4,"checkIn":"2026-10-24","checkOut":"2026-10-27","rooms":[{"offeringId":8,"adults":12}]}
                """;
        mvc.perform(
                post( "/api/search/selection" ).contentType( "application/json" ).content( selection ) ).andExpect(
                status().isOk() );
        for( String value : List.of( "0", "-1", "1.5", "12.0", "null", "true", "\"12\"", "2147483648",
                "999999999999999999999999999999999" ) )
        {
            mvc.perform( post( "/api/search/quotations" ).contentType( "application/json" ).content(
                    json.replace( "2}", value + "}" ) ) ).andExpect( status().isBadRequest() );
            mvc.perform( post( "/api/search/selection" ).contentType( "application/json" ).content(
                    selection.replace( "12}", value + "}" ) ) ).andExpect( status().isBadRequest() );
        }
        mvc.perform( post( "/api/search/selection" ).contentType( "application/json" ).content(
                selection.replace( ",\"adults\":12", "" ) ) ).andExpect( status().isBadRequest() );
    }
}
