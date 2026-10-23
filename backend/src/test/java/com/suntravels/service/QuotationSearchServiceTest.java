package com.suntravels.service;

import com.suntravels.dto.OfferingOccupancy;
import com.suntravels.dto.QuotationSearchFilters;
import com.suntravels.dto.QuotationSearchRequest;
import com.suntravels.dto.QuotationSearchRequest.RequestedRoom;
import com.suntravels.dto.QuotationSearchResponse.AvailabilityStatus;
import com.suntravels.dto.QuotationSearchResponse.Quotation;
import com.suntravels.dto.QuotationSearchResponse.ReasonCode;
import com.suntravels.dto.QuotationSearchResponse.RoomAllocation;
import com.suntravels.dto.QuotationSelectionRequest;
import com.suntravels.dto.ReservationRequest;
import com.suntravels.entity.Hotel;
import com.suntravels.entity.HotelStatus;
import com.suntravels.entity.RoomType;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.HotelRepository;
import com.suntravels.repository.QuotationSearchRepository;
import com.suntravels.repository.QuotationSearchRepository.BookedRoom;
import com.suntravels.repository.QuotationSearchRepository.Candidate;
import com.suntravels.repository.QuotationSearchRepository.Season;
import com.suntravels.repository.RoomTypeRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class QuotationSearchServiceTest
{
    final LocalDate today = LocalDate.of( 2026, 10, 15 ), in = LocalDate.of( 2026, 10, 24 ), out = in.plusDays( 3 );
    @Mock
    QuotationSearchRepository data;
    @Mock
    HotelRepository hotels;
    @Mock
    RoomTypeRepository rooms;
    ValidatorFactory factory;
    QuotationSearchService service;

    @Test
    void locationLookupsRequireTwoCharactersAndMapCountryCodes()
    {
        assertTrue( service.citySuggestions( " c ", null ).isEmpty() );
        assertTrue( service.hotelSuggestions( " h ", null, null ).isEmpty() );
        assertTrue( service.citySuggestions( "   ", null ).isEmpty() );
        verifyNoInteractions( data );
        when( data.citySuggestions( "Co", "Sri Lanka" ) ).thenReturn( List.of() );
        when( data.hotelSuggestions( "Oc", " Colombo ", "Sri Lanka" ) ).thenReturn( List.of() );
        service.citySuggestions( " Co ", "lk" );
        service.hotelSuggestions( " Oc ", " Colombo ", "LK" );
        verify( data ).citySuggestions( "Co", "Sri Lanka" );
        verify( data ).hotelSuggestions( "Oc", " Colombo ", "Sri Lanka" );
        String longCountry = "x".repeat(101);
        assertThrows( QuotationSearchException.class, () -> service.citySuggestions( "Co", longCountry ) );
    }

    @Test
    void autocompleteRejectsLongTermsAndDoesNotQueryForBlankText()
    {
        assertTrue( service.hotelSuggestions( "   " ).isEmpty() );
        assertTrue( service.hotelSuggestions( "" ).isEmpty() );
        String longTerm = "x".repeat(101);
        assertThrows( QuotationSearchException.class, () -> service.hotelSuggestions( longTerm ) );
        verifyNoInteractions( data, hotels );
        when( data.hotelSuggestions( "Ocean", null, null ) ).thenReturn( List.of() );
        assertTrue( service.hotelSuggestions( " Ocean " ).isEmpty() );
        verify( data ).hotelSuggestions( "Ocean", null, null );
    }

    @Test
    void selectedHotelResolvesWithoutSearchingTheSuggestionPage()
    {
        var option = new com.suntravels.dto.HotelSuggestion( 42, "Ocean", "Colombo", "Sri Lanka" );
        when( data.activeHotelOption( 42 ) ).thenReturn( java.util.Optional.of( option ) );
        assertEquals( option, service.hotelOption( 42 ) );
        when( data.activeHotelOption( 43 ) ).thenReturn( java.util.Optional.empty() );
        assertEquals( 404, assertThrows( QuotationSearchException.class, () -> service.hotelOption( 43 ) ).getStatus() );
        assertThrows( QuotationSearchException.class, () -> service.hotelOption( 0 ) );
        verifyNoInteractions( hotels );
    }

    @Test
    void evaluatedEmptyPageSkipsInventoryAndPrices()
    {
        var request = request( 2 );
        when( data.candidatePage( request, null ) ).thenReturn(
                new QuotationSearchRepository.CandidatePage( List.of(), List.of(), null ) );
        var result = service.evaluatedPage( request, null );
        assertTrue( result.hotels().isEmpty() );
        assertNull( result.nextAfterHotelId() );
        verify( data ).candidatePage( request, null );
        org.mockito.Mockito.verifyNoMoreInteractions( data );
    }

    @Test
    void evaluatedPagePreservesUnavailableOfferingsAndSharedRoomDemand()
    {
        var request = request( 2, 1 );
        var doubleRoom = candidate();
        var singleRoom = candidate( 8, 4, 1, 1, 3, "50", "USD" );
        when( data.candidatePage( request, 10 ) ).thenReturn(
                new QuotationSearchRepository.CandidatePage( List.of( 1 ), List.of( doubleRoom, singleRoom ), 20 ) );
        when( data.bookedRooms( Set.of( 7, 8 ), in, out ) ).thenReturn(
                List.of( new BookedRoom( 1, 7, in, out, 2 ),
                        new BookedRoom( 2, 7, in.plusDays( 1 ), out, 1 ) ) );
        var result = service.evaluatedPage( request, 10 );
        assertEquals( 20, result.nextAfterHotelId() );
        var offerings = result.hotels().get( 0 ).offerings();
        assertEquals( 2, offerings.size() );
        assertEquals( AvailabilityStatus.NOT_AVAILABLE, offerings.get( 0 ).availabilityStatus() );
        assertEquals( 1, offerings.get( 0 ).roomChoices().availableRoomsForStay() );
        assertEquals( ReasonCode.INSUFFICIENT_INVENTORY,
                offerings.get( 0 ).unavailableOption().reasons().get( 0 ).code() );
        assertEquals( new BigDecimal( "1035.00" ), offerings.get( 0 ).unavailableOption().indicativeTotalPrice() );
        assertEquals( ReasonCode.MAX_ADULT_OCCUPANCY,
                offerings.get( 1 ).unavailableOption().reasons().get( 0 ).code() );
        assertNull( offerings.get( 1 ).roomChoices().rooms().get( 0 ).totalPrice() );
        assertNull( offerings.get( 1 ).quotation() );
        verify( data ).seasons( Set.of( 7, 8 ) );
        verify( data ).bookedRooms( Set.of( 7, 8 ), in, out );
        verify( data, never() ).candidates( any() );
    }

    @Test
    void evaluatedPageBatchesMoreThanTwoHundredOfferingsWithoutTruncatingHotel()
    {
        var request = request( 2, 1 );
        var offerings = java.util.stream.IntStream.rangeClosed( 1, 201 )
                                                  .mapToObj( id -> candidate( id, 4, 1, 2, 3, "100", "USD" ) ).toList();
        when( data.candidatePage( request, null ) ).thenReturn(
                new QuotationSearchRepository.CandidatePage( List.of( 1 ), offerings, null ) );
        var result = service.evaluatedPage( request, null );
        assertEquals( 201, result.hotels().get( 0 ).offerings().size() );
        assertTrue( result.hotels().get( 0 ).offerings().stream().allMatch(
                o -> o.availabilityStatus() == AvailabilityStatus.AVAILABLE &&
                             o.quotation().totalPrice().compareTo( new BigDecimal( "1035.00" ) ) == 0 &&
                             o.quotation().rooms().get( 0 ).allocatedRoomsForOffering() == 2 ) );
        var batches = ArgumentCaptor.forClass( Set.class );
        verify( data, times( 2 ) ).bookedRooms( batches.capture(), org.mockito.ArgumentMatchers.eq( in ),
                org.mockito.ArgumentMatchers.eq( out ) );
        assertEquals( 200, batches.getAllValues().get( 0 ).size() );
        assertEquals( 1, batches.getAllValues().get( 1 ).size() );
        verify( data, times( 2 ) ).seasons( any() );
    }

    @Test
    void candidateDiscoveryValidatesBeforeQuerying()
    {
        var invalidRequest = request( 0 );
        var validRequest = request( 2 );
        assertThrows( QuotationSearchException.class, () -> service.candidatePage( invalidRequest, null ) );
        assertThrows( QuotationSearchException.class, () -> service.candidatePage( validRequest, 0 ) );
        assertThrows( QuotationSearchException.class, () -> service.candidatePage( validRequest, -1 ) );
        verifyNoInteractions( data );
    }

    @Test
    void candidateDiscoveryPassesContinuationWithoutPricing()
    {
        var request = request( 2 );
        var page = new QuotationSearchRepository.CandidatePage( List.of(), List.of(), null );
        when( data.candidatePage( request, 42 ) ).thenReturn( page );
        assertEquals( page, service.candidatePage( request, 42 ) );
        verify( data ).candidatePage( request, 42 );
        org.mockito.Mockito.verifyNoMoreInteractions( data );
    }

    // Configures the component and its test dependencies.
    @BeforeEach
    void setup()
    {
        factory = Validation.buildDefaultValidatorFactory();
        service = new QuotationSearchService( data, hotels, rooms, factory.getValidator(),
                Clock.fixed( Instant.parse( "2026-10-14T20:00:00Z" ), ZoneId.of( "Asia/Colombo" ) ),
                new NightlyQuotationPricing(), new StayAvailability() );
    }

    // Releases resources created for the test.
    @AfterEach
    void close()
    {
        factory.close();
    }

    // Builds a valid request fixture for the tested operation.
    QuotationSearchRequest request( int... adults )
    {
        return new QuotationSearchRequest( in, out,
                Arrays.stream( adults ).mapToObj( RequestedRoom::new ).toList(), null, null, null );
    }

    // Creates an eligible offering fixture for quotation tests.
    Candidate candidate(
            int id, int contract, int hotel, int capacity, int rooms, String rate,
            String currency )
    {
        return new Candidate( id, contract, hotel, "Hotel " + hotel, "Colombo", "C" + contract, currency,
                new BigDecimal( "15.00" ), in, in.plusDays( 40 ), id, "Type " + id, new BigDecimal( rate ), rooms,
                capacity );
    }

    // Creates an eligible offering fixture for quotation tests.
    Candidate candidate()
    {
        return candidate( 7, 4, 1, 2, 3, "100", "USD" );
    }

    // Configures candidate offerings returned by the mocked search repository.
    void candidates( Candidate... values )
    {
        when( data.candidates( any() ) ).thenReturn( Arrays.asList( values ) );
    }

    // Asserts that the operation returns the expected business error.
    void error( int status, QuotationSearchRequest r )
    {
        assertEquals( status,
                assertThrows( QuotationSearchException.class, () -> service.search( r ) ).getStatus() );
    }

    // Verifies business clock and limits come from colombo.
    @Test
    void businessClockAndLimitsComeFromColombo()
    {
        assertEquals( today, service.limits().businessDate() );
        assertEquals( 8, service.limits().maxRooms() );
    }

    // Verifies occupancy returns every eligible limit without loading prices or inventory.
    @Test
    void occupancyReturnsEveryEligibleLimitWithoutLoadingPricesOrInventory()
    {
        when( hotels.existsById( 1 ) ).thenReturn( true );
        when( rooms.existsById( 7 ) ).thenReturn( true );
        candidates( candidate(), candidate( 8, 4, 1, 4, 3, "200", "USD" ) );
        var result = service.occupancy( 1, 7, in, out, "Colombo" );
        assertEquals( List.of( 2, 4 ), result.stream().map( OfferingOccupancy::maxAdults ).toList() );
        var captured = ArgumentCaptor.forClass( QuotationSearchRequest.class );
        verify( data ).candidates( captured.capture() );
        assertEquals( 1, captured.getValue().hotelId() );
        assertEquals( 7, captured.getValue().roomTypeId() );
        assertEquals( in, captured.getValue().checkIn() );
        assertEquals( out, captured.getValue().checkOut() );
        assertEquals( "Colombo", captured.getValue().city() );
        verify( data, never() ).seasons( any() );
        verify( data, never() ).bookedRooms( any(), any(), any() );
    }

    // Verifies occupancy validates dates and references before querying and allows no matches.
    @Test
    void occupancyValidatesDatesAndReferencesBeforeQueryingAndAllowsNoMatches()
    {
        assertEquals( 400, assertThrows( QuotationSearchException.class,
                () -> service.occupancy( 1, 7, in, in, null ) ).getStatus() );
        assertEquals( 400, assertThrows( QuotationSearchException.class,
                () -> service.occupancy( null, 7, in, out, null ) ).getStatus() );
        assertEquals( 400, assertThrows( QuotationSearchException.class,
                () -> service.occupancy( 1, 7, null, out, null ) ).getStatus() );
        assertEquals( 404, assertThrows( QuotationSearchException.class,
                () -> service.occupancy( 99, 7, in, out, null ) ).getStatus() );
        verifyNoInteractions( data );
        when( hotels.existsById( 1 ) ).thenReturn( true );
        when( rooms.existsById( 7 ) ).thenReturn( true );
        assertTrue( service.occupancy( 1, 7, in, out, null ).isEmpty() );
    }

    // Verifies search filters expose only active hotel identity and generic room names.
    @Test
    void searchFiltersExposeOnlyActiveHotelIdentityAndGenericRoomNames()
    {
        Hotel active = new Hotel();
        active.setName( "Ocean" );
        active.setCity( "Colombo" );
        active.setStatus( HotelStatus.ACTIVE );
        ReflectionTestUtils.setField( active, "hotelId", 7 );
        Hotel inactive = new Hotel();
        inactive.setName( "Closed" );
        inactive.setStatus( HotelStatus.INACTIVE );
        ReflectionTestUtils.setField( inactive, "hotelId", 8 );
        RoomType room = new RoomType();
        room.setName( "Double" );
        ReflectionTestUtils.setField( room, "roomTypeId", 3 );
        when( hotels.findAll( any( Sort.class ) ) ).thenReturn( List.of( active, inactive ) );
        when( rooms.findAll( any( Sort.class ) ) ).thenReturn( List.of( room ) );
        var filters = service.filters();
        assertEquals( List.of( "Ocean" ),
                filters.hotels().stream().map( QuotationSearchFilters.HotelOption::name ).toList() );
        assertEquals( "Double", filters.roomTypes().get( 0 ).name() );
    }

    // Verifies rejects past or same day checkout.
    @ParameterizedTest
    @ValueSource( ints = { -1, 0 } )
    void rejectsPastOrSameDayCheckout( int offset )
    {
        error( 400, new QuotationSearchRequest( today.plusDays( offset ), today.plusDays( offset ),
                List.of( new RequestedRoom( 1 ) ), null, null, null ) );
        verifyNoInteractions( data );
    }

    // Verifies past check in is rejected even with valid checkout.
    @Test
    void pastCheckInIsRejectedEvenWithValidCheckout()
    {
        error( 400,
                new QuotationSearchRequest( today.minusDays( 1 ), out, List.of( new RequestedRoom( 1 ) ), null, null,
                        null ) );
    }

    // Verifies rejects reversed and excessive date ranges.
    @Test
    void rejectsReversedAndExcessiveDateRanges()
    {
        error( 400, new QuotationSearchRequest( in, in.minusDays( 1 ), List.of( new RequestedRoom( 1 ) ), null, null,
                null ) );
        error( 400, new QuotationSearchRequest( in, in.plusDays( 32 ), List.of( new RequestedRoom( 1 ) ), null, null,
                null ) );
        error( 400, new QuotationSearchRequest( today.plusDays( 731 ), today.plusDays( 732 ),
                List.of( new RequestedRoom( 1 ) ), null, null, null ) );
    }

    // Verifies rejects invalid adult counts.
    @ParameterizedTest
    @ValueSource( ints = { 0, -1 } )
    void rejectsInvalidAdultCounts( int count )
    {
        error( 400, request( count ) );
        verifyNoInteractions( data );
    }

    // Verifies rejects null and empty room structures.
    @Test
    void rejectsNullAndEmptyRoomStructures()
    {
        error( 400, null );
        error( 400, request() );
        error( 400, new QuotationSearchRequest( in, out, null, null, null, null ) );
        error( 400,
                new QuotationSearchRequest( in, out, Arrays.asList( ( RequestedRoom ) null ), null, null, null ) );
        error( 400, new QuotationSearchRequest( in, out, List.of( new RequestedRoom( null ) ), null, null, null ) );
        error( 400, request( 1, 1, 1, 1, 1, 1, 1, 1, 1 ) );
    }

    // Verifies selected reference must exist and bad ids are invalid.
    @Test
    void selectedReferenceMustExistAndBadIdsAreInvalid()
    {
        error( 404, new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 1 ) ), null, 99, null ) );
        error( 404, new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 1 ) ), null, null, 99 ) );
        error( 400, new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 1 ) ), null, 0, null ) );
        error( 400, new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 1 ) ), "x".repeat( 101 ), null,
                null ) );
        verifyNoInteractions( data );
    }

    // Verifies no matches is successful empty search.
    @Test
    void noMatchesIsSuccessfulEmptySearch()
    {
        assertTrue( service.search( request( 2 ) ).quotations().isEmpty() );
        verify( data, never() ).seasons( any() );
    }

    // Verifies mixed adults share inventory and displayed nights sum exactly.
    @Test
    void mixedAdultsShareInventoryAndDisplayedNightsSumExactly()
    {
        candidates( candidate() );
        var result = service.search( request( 2, 1 ) );
        var q = result.quotations().get( 0 );
        assertEquals( new BigDecimal( "1035.00" ), q.totalPrice() );
        assertEquals( 2, q.rooms().get( 0 ).adults() );
        assertEquals( 1, q.rooms().get( 1 ).adults() );
        assertEquals( 3, q.rooms().get( 0 ).availableRoomsForStay() );
        assertEquals( 2, q.rooms().get( 0 ).allocatedRoomsForOffering() );
        assertEquals( 1, q.rooms().get( 0 ).remainingRoomsAfterAllocation() );
        assertEquals( q.totalPrice(),
                q.rooms().stream().map( r -> r.totalPrice() ).reduce( new BigDecimal( "0.00" ), BigDecimal::add ) );
        q.rooms().forEach( r -> assertEquals( r.totalPrice(),
                r.nightlyPrices().stream().map( n -> n.sellingPrice() ).reduce( new BigDecimal( "0.00" ),
                        BigDecimal::add ) ) );
        assertEquals( AvailabilityStatus.AVAILABLE, q.availabilityStatus() );
        assertTrue( result.unavailableOptions().isEmpty() );
    }

    // Verifies unavailable inventory counts all requested rooms against the same pool.
    @Test
    void unavailableInventoryCountsAllRequestedRoomsAgainstTheSamePool()
    {
        candidates( candidate( 7, 4, 1, 2, 2, "100", "USD" ) );
        when( data.bookedRooms( Set.of( 7 ), in, out ) ).thenReturn(
                List.of( new BookedRoom( 1, 7, in.plusDays( 1 ), in.plusDays( 2 ), 1 ) ) );
        var result = service.search( request( 2, 1 ) );
        assertTrue( result.quotations().isEmpty() );
        var option = result.unavailableOptions().get( 0 );
        assertEquals( AvailabilityStatus.NOT_AVAILABLE, option.availabilityStatus() );
        assertEquals( 1, option.offerings().get( 0 ).availableRoomsForStay() );
        assertEquals( ReasonCode.INSUFFICIENT_INVENTORY, option.reasons().get( 0 ).code() );
        assertEquals( "Only 1 room available; 2 requested.", option.reasons().get( 0 ).message() );
        assertEquals( new BigDecimal( "1035.00" ), option.indicativeTotalPrice() );
    }

    // Verifies occupancy failure has no misleading selling price.
    @Test
    void occupancyFailureHasNoMisleadingSellingPrice()
    {
        candidates( candidate() );
        var result = service.search( request( 4 ) );
        assertTrue( result.quotations().isEmpty() );
        var option = result.unavailableOptions().get( 0 );
        assertEquals( ReasonCode.MAX_ADULT_OCCUPANCY, option.reasons().get( 0 ).code() );
        assertTrue( option.reasons().get( 0 ).message().contains( "Maximum 2 adults per room" ) );
        assertNull( option.indicativeTotalPrice() );
    }

    // Verifies available options remain separate and sorted and cannot be displaced by unavailable results.
    @Test
    void availableOptionsRemainSeparateAndSortedAndCannotBeDisplacedByUnavailableResults()
    {
        List<Candidate> all = new ArrayList<>();
        for( int i = 1; i <= 25; i++ ) all.add( candidate( i, i, i, 1, 1, "50", "USD" ) );
        all.add( candidate( 30, 30, 30, 2, 2, "200", "USD" ) );
        all.add( candidate( 31, 31, 31, 2, 2, "100", "USD" ) );
        when( data.candidates( any() ) ).thenReturn( all );
        var result = service.search( request( 2 ) );
        assertEquals( List.of( 31, 30 ), result.quotations().stream().map( Quotation::hotelId ).toList() );
        assertEquals( 20, result.unavailableOptions().size() );
        assertEquals( 25, result.matchingUnavailableOptions() );
        assertTrue( result.unavailableTruncated() );
        assertFalse( result.truncated() );
    }

    // Verifies mixed types satisfy different room specific occupancy.
    @Test
    void mixedTypesSatisfyDifferentRoomSpecificOccupancy()
    {
        when( hotels.existsById( 1 ) ).thenReturn( true );
        candidates( candidate( 7, 4, 1, 2, 1, "100", "USD" ), candidate( 8, 4, 1, 1, 1, "50", "USD" ) );
        var q = service.customize( new QuotationSelectionRequest( 1, 4, in, out,
                List.of( new ReservationRequest.Room( 7, 2 ), new ReservationRequest.Room( 8, 1 ) ) ) );
        assertEquals( 7, q.rooms().get( 0 ).offeringId() );
        assertEquals( 8, q.rooms().get( 1 ).offeringId() );
        assertEquals( new BigDecimal( "862.50" ), q.totalPrice() );
    }

    // Verifies full combination is required not partial per offering matches.
    @Test
    void fullCombinationIsRequiredNotPartialPerOfferingMatches()
    {
        candidates( candidate( 7, 4, 1, 2, 1, "100", "USD" ) );
        assertTrue( service.search( request( 2, 1 ) ).quotations().isEmpty() );
        assertTrue( service.search( request( 3 ) ).quotations().isEmpty() );
    }

    // Verifies cannot combine contracts or hotels.
    @Test
    void cannotCombineContractsOrHotels()
    {
        candidates( candidate( 7, 4, 1, 2, 1, "100", "USD" ), candidate( 8, 5, 2, 1, 1, "50", "USD" ) );
        assertTrue( service.search( request( 2, 1 ) ).quotations().isEmpty() );
    }

    // Verifies peak night availability allows turnover not false sold out.
    @Test
    void peakNightAvailabilityAllowsTurnoverNotFalseSoldOut()
    {
        candidates( candidate( 7, 4, 1, 2, 2, "100", "USD" ) );
        when( data.bookedRooms( Set.of( 7 ), in, out ) ).thenReturn(
                List.of( new BookedRoom( 1, 7, in, in.plusDays( 1 ), 2 ),
                        new BookedRoom( 2, 7, in.plusDays( 1 ), out, 2 ) ) );
        assertEquals( 1,
                service.search( request( 2 ) ).quotations().get( 0 ).rooms().get( 0 ).availableRoomsForStay() );
    }

    // Verifies fully booked offering does not produce a quote.
    @Test
    void fullyBookedOfferingDoesNotProduceAQuote()
    {
        candidates( candidate( 7, 4, 1, 2, 1, "100", "USD" ) );
        when( data.bookedRooms( Set.of( 7 ), in, out ) ).thenReturn( List.of( new BookedRoom( 1, 7, in, out, 1 ) ) );
        assertTrue( service.search( request( 1 ) ).quotations().isEmpty() );
    }

    // Verifies seasonal rates are bulk loaded and override only applicable night.
    @Test
    void seasonalRatesAreBulkLoadedAndOverrideOnlyApplicableNight()
    {
        candidates( candidate() );
        when( data.seasons( Set.of( 7 ) ) ).thenReturn(
                List.of( new Season( 1, 7, in.plusDays( 1 ), in.plusDays( 1 ), new BigDecimal( "150" ) ) ) );
        var q = service.search( request( 2 ) ).quotations().get( 0 );
        assertEquals( new BigDecimal( "805.00" ), q.totalPrice() );
        assertEquals( List.of( "BASE", "SEASONAL", "BASE" ),
                q.rooms().get( 0 ).nightlyPrices().stream().map( n -> n.source() ).toList() );
        verify( data, times( 1 ) ).seasons( Set.of( 7 ) );
    }

    // Verifies unsafe replacement or approval state fails closed.
    @Test
    void unsafeReplacementOrApprovalStateFailsClosed()
    {
        candidates( candidate() );
        when( data.inconsistent( Set.of( 7 ), Set.of( 4 ), today ) ).thenReturn( true );
        error( 409, request( 1 ) );
        verify( data, never() ).bookedRooms( any(), any(), any() );
    }

    // Verifies excessive processing limits never return an incomplete search.
    @Test
    void excessiveProcessingLimitsNeverReturnAnIncompleteSearch()
    {
        when( data.candidates( any() ) ).thenReturn( Collections.nCopies( 501, candidate() ) );
        error( 409, request( 1 ) );
    }

    // Verifies invalid stored rate and currency are not quoted.
    @Test
    void invalidStoredRateAndCurrencyAreNotQuoted()
    {
        candidates( candidate( 7, 4, 1, 2, 2, "0", "USD" ) );
        error( 409, request( 1 ) );
        candidates( candidate( 7, 4, 1, 2, 2, "100", "usd" ) );
        error( 409, request( 1 ) );
    }

    // Verifies currency groups and totals are deterministic and do not convert.
    @Test
    void currencyGroupsAndTotalsAreDeterministicAndDoNotConvert()
    {
        candidates( candidate( 7, 4, 1, 2, 3, "100", "USD" ), candidate( 8, 5, 2, 2, 3, "200", "EUR" ),
                candidate( 9, 6, 3, 2, 3, "50", "USD" ) );
        var q = service.search( request( 1 ) ).quotations();
        assertEquals( List.of( "EUR", "USD", "USD" ), q.stream().map( v -> v.currencyCode() ).toList() );
        assertEquals( new BigDecimal( "172.50" ), q.get( 1 ).totalPrice() );
    }

    // Verifies results are capped explicitly without losing allocation correctness.
    @Test
    void resultsAreCappedExplicitlyWithoutLosingAllocationCorrectness()
    {
        List<Candidate> all = new ArrayList<>();
        for( int i = 1; i <= 25; i++ ) all.add( candidate( i, i, i, 2, 1, "100", "USD" ) );
        when( data.candidates( any() ) ).thenReturn( all );
        var result = service.search( request( 1 ) );
        assertTrue( result.truncated() );
        assertEquals( 25, result.matchingOptions() );
        assertEquals( 20, result.quotations().size() );
        assertEquals( "INDIVIDUAL_ROOM_TYPES", result.allocationPolicy() );
    }

    // Verifies individual types use complete request with assignment markup.
    @Test
    void individualTypesUseCompleteRequestWithAssignmentMarkup()
    {
        candidates( candidate( 7, 4, 1, 2, 15, "35", "USD" ), candidate( 8, 4, 1, 3, 8, "55", "USD" ) );
        var result = service.search( request( 2, 1 ) );
        assertEquals( List.of( new BigDecimal( "362.25" ), new BigDecimal( "569.25" ) ),
                result.quotations().stream().map( Quotation::totalPrice ).toList() );
        assertEquals( List.of( 7, 8 ),
                result.quotations().stream().map( q -> q.rooms().get( 0 ).offeringId() ).toList() );
        assertEquals( 2, result.selectionOfferings().size() );
    }

    // Verifies individual failures still allow explicit mixed selection.
    @Test
    void individualFailuresStillAllowExplicitMixedSelection()
    {
        candidates( candidate( 7, 4, 1, 2, 1, "35", "USD" ), candidate( 8, 4, 1, 3, 1, "55", "USD" ) );
        var result = service.search( request( 2, 1 ) );
        assertTrue( result.quotations().isEmpty() );
        assertEquals( 2, result.unavailableOptions().size() );
        when( hotels.existsById( 1 ) ).thenReturn( true );
        var q = service.customize( new QuotationSelectionRequest( 1, 4, in, out,
                List.of( new ReservationRequest.Room( 7, 2 ), new ReservationRequest.Room( 8, 1 ) ) ) );
        assertEquals( List.of( 7, 8 ), q.rooms().stream().map( RoomAllocation::offeringId ).toList() );
        assertEquals( new BigDecimal( "431.25" ), q.totalPrice() );
    }

    // Builds an exact room-selection fixture for quotation tests.
    QuotationSelectionRequest selection( int contract, int... ids )
    {
        return new QuotationSelectionRequest( 1, contract, in, out,
                Arrays.stream( ids ).mapToObj( id -> new ReservationRequest.Room( id, 1 ) ).toList() );
    }

    // Verifies customized selection preserves more expensive choice and adult order.
    @Test
    void customizedSelectionPreservesMoreExpensiveChoiceAndAdultOrder()
    {
        when( hotels.existsById( 1 ) ).thenReturn( true );
        candidates( candidate( 7, 4, 1, 2, 15, "35", "USD" ), candidate( 8, 4, 1, 3, 8, "55", "USD" ) );
        var q = service.customize( new QuotationSelectionRequest( 1, 4, in, out,
                List.of( new ReservationRequest.Room( 8, 1 ), new ReservationRequest.Room( 7, 2 ) ) ) );
        assertEquals( List.of( 8, 7 ), q.rooms().stream().map( RoomAllocation::offeringId ).toList() );
        assertEquals( List.of( 1, 2 ), q.rooms().stream().map( RoomAllocation::adults ).toList() );
        assertEquals( new BigDecimal( "431.25" ), q.totalPrice() );
    }

    // Verifies customized repeated offerings share inventory and reject foreign references.
    @Test
    void customizedRepeatedOfferingsShareInventoryAndRejectForeignReferences()
    {
        when( hotels.existsById( 1 ) ).thenReturn( true );
        candidates( candidate( 7, 4, 1, 2, 1, "35", "USD" ), candidate( 8, 5, 2, 3, 8, "55", "USD" ) );
        var selectedRoomsForAssertion1 = selection( 4, 7, 7 );
        assertThrows( QuotationSearchException.class, () -> service.customize( selectedRoomsForAssertion1 ) );
        var selectedRoomsForAssertion2 = selection( 4, 7, 8 );
        assertThrows( QuotationSearchException.class, () -> service.customize( selectedRoomsForAssertion2 ) );
        var selectedRoomsForAssertion3 = selection( 99, 7 );
        assertThrows( QuotationSearchException.class, () -> service.customize( selectedRoomsForAssertion3 ) );
    }

    // Verifies customized cross contract and cross hotel references are rejected independently.
    @Test
    void customizedCrossContractAndCrossHotelReferencesAreRejectedIndependently()
    {
        when( hotels.existsById( 1 ) ).thenReturn( true );
        candidates( candidate( 7, 4, 1, 2, 3, "35", "USD" ), candidate( 8, 5, 1, 3, 8, "55", "USD" ) );
        var selectedRoomsForAssertion4 = selection( 4, 7, 8 );
        assertThrows( QuotationSearchException.class, () -> service.customize( selectedRoomsForAssertion4 ) );
        candidates( candidate( 7, 4, 1, 2, 3, "35", "USD" ), candidate( 8, 4, 2, 3, 8, "55", "USD" ) );
        var selectedRoomsForAssertion5 = selection( 4, 7, 8 );
        assertThrows( QuotationSearchException.class, () -> service.customize( selectedRoomsForAssertion5 ) );
    }

    // Verifies contractual capacity determines availability without room type filter.
    @ParameterizedTest
    @CsvSource( { "1,2,true", "2,2,true", "3,2,false", "3,3,true", "5,6,true", "7,6,false", "12,12,true",
            "13,12,false" } )
    void contractualCapacityDeterminesAvailabilityWithoutRoomTypeFilter(
            int adults, int maximum,
            boolean available )
    {
        candidates( candidate( 7, 4, 1, maximum, 8, "100", "USD" ) );
        var result = service.search( request( adults ) );
        assertEquals( available, !result.quotations().isEmpty() );
        if( available ) assertEquals( new BigDecimal( "345.00" ).multiply( BigDecimal.valueOf( adults ) ),
                result.quotations().get( 0 ).totalPrice() );
        else assertEquals( ReasonCode.MAX_ADULT_OCCUPANCY,
                result.unavailableOptions().get( 0 ).reasons().get( 0 ).code() );
    }

    // Verifies different capacities are evaluated independently for the complete request.
    @Test
    void differentCapacitiesAreEvaluatedIndependentlyForTheCompleteRequest()
    {
        candidates( candidate( 7, 4, 1, 2, 8, "35", "USD" ), candidate( 8, 4, 1, 3, 8, "55", "USD" ) );
        var result = service.search( request( 3, 1 ) );
        assertEquals( 8, result.quotations().get( 0 ).rooms().get( 0 ).offeringId() );
        assertEquals( 7, result.unavailableOptions().get( 0 ).offerings().get( 0 ).offeringId() );
    }

    // Verifies large representable adults fail by offering capacity without overflow.
    @Test
    void largeRepresentableAdultsFailByOfferingCapacityWithoutOverflow()
    {
        candidates( candidate() );
        var result = service.search( request( Integer.MAX_VALUE ) );
        assertTrue( result.quotations().isEmpty() );
        assertNull( result.unavailableOptions().get( 0 ).indicativeTotalPrice() );
        assertEquals( ReasonCode.MAX_ADULT_OCCUPANCY,
                result.unavailableOptions().get( 0 ).reasons().get( 0 ).code() );
    }

    // Verifies customized rooms use their own limits including above ten.
    @Test
    void customizedRoomsUseTheirOwnLimitsIncludingAboveTen()
    {
        when( hotels.existsById( 1 ) ).thenReturn( true );
        candidates( candidate( 7, 4, 1, 12, 8, "100", "USD" ), candidate( 8, 4, 1, 6, 8, "100", "USD" ) );
        var q = service.customize( new QuotationSelectionRequest( 1, 4, in, out,
                List.of( new ReservationRequest.Room( 7, 12 ), new ReservationRequest.Room( 8, 5 ) ) ) );
        assertEquals( List.of( 12, 5 ), q.rooms().stream().map( RoomAllocation::adults ).toList() );
        assertEquals( new BigDecimal( "5865.00" ), q.totalPrice() );
        var requestForAssertion1 = new QuotationSelectionRequest( 1, 4, in, out,
                List.of( new ReservationRequest.Room( 8, 12 ) ) );

        assertThrows( QuotationSearchException.class, () -> service.customize( requestForAssertion1 ) );
    }

    // Verifies eight rooms remain allowed without a total adult ceiling.
    @Test
    void eightRoomsRemainAllowedWithoutATotalAdultCeiling()
    {
        candidates( candidate( 7, 4, 1, 12, 8, "100", "USD" ) );
        assertEquals( 8,
                service.search( request( 12, 12, 12, 12, 12, 12, 12, 12 ) ).quotations().get( 0 ).rooms().size() );
        error( 400, request( 12, 12, 12, 12, 12, 12, 12, 12, 12 ) );
    }
}
