package com.suntravels.integration;

import com.suntravels.dto.HotelStatusUpdateRequest;
import com.suntravels.dto.OfferingStatusRequest;
import com.suntravels.dto.QuotationSearchRequest;
import com.suntravels.dto.QuotationSearchResponse;
import com.suntravels.dto.QuotationSelectionRequest;
import com.suntravels.dto.ReservationRequest;
import com.suntravels.dto.ReservationResponse;
import com.suntravels.entity.Contract;
import com.suntravels.entity.ContractRoomOffering;
import com.suntravels.entity.ContractStatus;
import com.suntravels.entity.Hotel;
import com.suntravels.entity.HotelStatus;
import com.suntravels.entity.OfferingStatus;
import com.suntravels.entity.RoomType;
import com.suntravels.exception.ReservationException;
import com.suntravels.repository.ContractRepository;
import com.suntravels.repository.HotelRepository;
import com.suntravels.repository.OfferingRepository;
import com.suntravels.repository.ReservationRepository;
import com.suntravels.repository.RoomTypeRepository;
import com.suntravels.repository.SeasonalRateRepository;
import com.suntravels.service.ContractService;
import com.suntravels.service.HotelService;
import com.suntravels.service.OfferingService;
import com.suntravels.service.QuotationSearchService;
import com.suntravels.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * Opt in with SUNTRAVELS_RESERVATION_MYSQL_TESTS=true and an existing disposable MySQL 8 *_test schema.
 * No schema provisioning, dumps, Testcontainers or operational records are used.
 * Tests commit their own fixtures to exercise real transactions; cleanup deletes only those fixtures.
 */
@SpringBootTest( properties = "suntravels.auth.bootstrap.enabled=false" )
@EnabledIfEnvironmentVariable( named = "SUNTRAVELS_RESERVATION_MYSQL_TESTS", matches = "true" )
class ReservationMySqlIntegrationTest
{
    @Autowired
    ContractService contractService;
    @Autowired
    OfferingService offeringService;
    @Autowired
    HotelService hotelService;
    @Autowired
    ReservationService service;
    @Autowired
    QuotationSearchService search;
    @Autowired
    HotelRepository hotels;
    @Autowired
    ContractRepository contracts;
    @Autowired
    RoomTypeRepository types;
    @Autowired
    OfferingRepository offerings;
    @Autowired
    SeasonalRateRepository seasons;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    Clock clock;
    @MockitoSpyBean
    ReservationRepository repository;
    int hotel, type, contract, offering, customer, otherType, otherOffering;
    LocalDate in, out;
    TransactionTemplate tx;

    // Creates isolated reservation fixtures for the test.
    @BeforeEach
    void fixtures()
    {
        assertTrue( Objects.requireNonNull( jdbc.queryForObject( "select database()", String.class ) ).endsWith(
                        "_test" ),
                "Refusing operational database: use a disposable *_test database with the existing schema" );
        assertTrue( Objects.requireNonNull( jdbc.queryForObject( "select version()", String.class ) ).startsWith(
                "8." ) );
        tx = new TransactionTemplate( transactions );
        in = LocalDate.now( clock ).plusDays( 20 );
        out = in.plusDays( 3 );
        tx.executeWithoutResult( s ->
        {
            var h = new Hotel();
            h.setName( "Reservation fixture " + UUID.randomUUID() );
            h.setCity( "ReservationFixture" );
            h.setStatus( HotelStatus.ACTIVE );
            h = hotels.saveAndFlush( h );
            hotel = h.getHotelId();
            var t = new RoomType();
            t.setName( "Reservation room " + UUID.randomUUID() );
            t = types.saveAndFlush( t );
            type = t.getRoomTypeId();
            var c = new Contract();
            c.setHotel( h );
            c.setContractCode( "R-" + UUID.randomUUID() );
            c.setValidFrom( in.minusDays( 2 ) );
            c.setValidTo( out.plusDays( 10 ) );
            c.setMarkupPercentage( new BigDecimal( "15.00" ) );
            c.setCurrencyCode( "USD" );
            c.setStatus( ContractStatus.APPROVED );
            c = contracts.saveAndFlush( c );
            contract = c.getContractId();
            var o = new ContractRoomOffering();
            o.setContract( c );
            o.setRoomType( t );
            o.setRatePerPersonPerNight( new BigDecimal( "100.00" ) );
            o.setContractedRoomCount( 3 );
            o.setMaxAdults( 2 );
            o.setStatus( OfferingStatus.ENABLED );
            o = offerings.saveAndFlush( o );
            offering = o.getOfferingId();
            customer = repository.insert( "insert into customer(name,email) values (?,?)",
                    "Reservation fixture " + UUID.randomUUID(), UUID.randomUUID() + "@example.test" );
        } );
    }

    // Removes the database records created by the test.
    @AfterEach
    void cleanup()
    {
        if( customer == 0 ) return;
        tx.executeWithoutResult( s ->
        {
            jdbc.update(
                    "delete n from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id join reservation r on r.reservation_id=i.reservation_id where r.customer_id=?",
                    customer );
            jdbc.update(
                    "delete i from reservation_item i join reservation r on r.reservation_id=i.reservation_id where r.customer_id=?",
                    customer );
            jdbc.update( "delete from reservation where customer_id=?", customer );
            jdbc.update( "delete from customer where customer_id=?", customer );
            if( otherOffering != 0 )
                jdbc.update( "delete from contract_room_offering where offering_id=?", otherOffering );
            if( otherType != 0 ) jdbc.update( "delete from room_type where room_type_id=?", otherType );
            jdbc.update( "delete from offering_seasonal_rate where offering_id=?", offering );
            jdbc.update( "delete from contract_room_offering where offering_id=?", offering );
            jdbc.update( "delete from contract where contract_id=?", contract );
            jdbc.update( "delete from hotel where hotel_id=?", hotel );
            jdbc.update( "delete from room_type where room_type_id=?", type );
        } );
    }

    // Builds a valid request fixture for the tested operation.
    ReservationRequest request( String accepted, int... adults )
    {
        return request( in, out, accepted, adults );
    }

    // Builds a valid request fixture for the tested operation.
    ReservationRequest request( LocalDate start, LocalDate end, String accepted, int... adults )
    {
        return new ReservationRequest( customer, hotel, contract, start, end,
                Arrays.stream( adults ).mapToObj( a -> new ReservationRequest.Room( offering, a ) ).toList(),
                new BigDecimal( accepted ), "USD" );
    }

    // Builds a quotation search request using the fixture stay.
    QuotationSearchRequest searchRequest( int... adults )
    {
        return new QuotationSearchRequest( in, out,
                Arrays.stream( adults ).mapToObj( QuotationSearchRequest.RequestedRoom::new ).toList(), null,
                hotel, null );
    }

    // Counts the reservation records created by the fixture.
    long bookingCount()
    {
        return jdbc.queryForObject( "select count(*) from reservation where customer_id=?", Long.class,
                customer );
    }

    // Verifies successful single room uses only confirmed status and historical nightly total.
    @Test
    void successfulSingleRoomUsesOnlyConfirmedStatusAndHistoricalNightlyTotal()
    {
        var booked = service.confirm( request( "690", 2 ) );
        assertEquals( "CONFIRMED", booked.status() );
        assertEquals( 1, booked.rooms().size() );
        assertEquals( new BigDecimal( "690.00" ), booked.totalPrice() );
        assertEquals( 3, booked.rooms().get( 0 ).nightlyPrices().size() );
        assertEquals( 1, bookingCount() );
    }

    // Verifies unavailable search diagnostics cannot bypass direct confirmation revalidation.
    @Test
    void unavailableSearchDiagnosticsCannotBypassDirectConfirmationRevalidation()
    {
        var occupancy = search.search( searchRequest( 3 ) );
        assertTrue( occupancy.quotations().isEmpty() );
        assertEquals( QuotationSearchResponse.ReasonCode.MAX_ADULT_OCCUPANCY,
                occupancy.unavailableOptions().get( 0 ).reasons().get( 0 ).code() );
        var requestForAssertion1 = request( "1035", 3 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion1 ) );
        jdbc.update( "update contract_room_offering set contracted_room_count=1 where offering_id=?",
                offering );
        var inventory = search.search( searchRequest( 2, 1 ) );
        assertTrue( inventory.quotations().isEmpty() );
        assertEquals( QuotationSearchResponse.ReasonCode.INSUFFICIENT_INVENTORY,
                inventory.unavailableOptions().get( 0 ).reasons().get( 0 ).code() );
        var requestForAssertion2 = request( "1035", 2, 1 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion2 ) );
        assertEquals( 0, bookingCount() );
    }

    // Verifies one and multiple rooms persist nightly snapshots and directory totals.
    @Test
    void oneAndMultipleRoomsPersistNightlySnapshotsAndDirectoryTotals()
    {
        var r = service.confirm( request( "1035", 2, 1 ) );
        assertEquals( "CONFIRMED", r.status() );
        assertEquals( 2, r.rooms().size() );
        assertEquals( new BigDecimal( "1035.00" ), r.totalPrice() );
        assertEquals( 6, jdbc.queryForObject(
                "select count(*) from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id where i.reservation_id=?",
                Integer.class, r.reservationId() ) );
        var page = service.list( Integer.toString( r.reservationId() ), "CONFIRMED", 0, 20 );
        assertEquals( 1, page.totalElements() );
        assertEquals( r.totalPrice(), page.reservations().get( 0 ).totalPrice() );
        assertEquals( r, service.detail( r.reservationId() ) );
    }

    // Verifies fresh eligibility rejects inactive hotel unapproved contract and disabled offering.
    @ParameterizedTest
    @CsvSource( { "INACTIVE,APPROVED,ENABLED", "ACTIVE,DRAFT,ENABLED", "ACTIVE,SUSPENDED,ENABLED",
            "ACTIVE,TERMINATED,ENABLED", "ACTIVE,APPROVED,SUSPENDED" } )
    void freshEligibilityRejectsInactiveHotelUnapprovedContractAndDisabledOffering(
            String h, String c,
            String o )
    {
        jdbc.update( "update hotel set status=? where hotel_id=?", h, hotel );
        jdbc.update( "update contract set status=? where contract_id=?", c, contract );
        jdbc.update( "update contract_room_offering set status=? where offering_id=?", o, offering );
        var requestForAssertion4 = request( "690", 2 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion4 ) );
        assertEquals( 0, bookingCount() );
    }

    // Verifies validity capacity and customer are validated.
    @Test
    void validityCapacityAndCustomerAreValidated()
    {
        var requestForAssertion5 = request( "1035", 3 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion5 ) );
        var r = request( "690", 2 );
        var invalidCustomer = new ReservationRequest( Integer.MAX_VALUE, hotel, contract, in, out, r.rooms(),
                r.acceptedTotal(), "USD" );
        assertEquals( "CUSTOMER_NOT_FOUND",
                assertThrows( ReservationException.class, () -> service.confirm( invalidCustomer ) ).getCode() );
        jdbc.update( "update contract set valid_to=? where contract_id=?", out.minusDays( 2 ), contract );
        assertThrows( ReservationException.class, () -> service.confirm( r ) );
        assertEquals( 0, bookingCount() );
    }

    // Verifies seasonal change requires acknowledgment and snapshots survive maintenance and cancellation.
    @Test
    void seasonalChangeRequiresAcknowledgmentAndSnapshotsSurviveMaintenanceAndCancellation()
    {
        var initial = search.search( searchRequest( 2 ) ).quotations().get( 0 );
        assertEquals( new BigDecimal( "690.00" ), initial.totalPrice() );
        jdbc.update(
                "insert into offering_seasonal_rate(offering_id,valid_from,valid_to,rate_per_person_per_night) values (?,?,?,?)",
                offering, in.plusDays( 1 ), in.plusDays( 1 ), new BigDecimal( "150" ) );
        var requestForAssertion6 = request( "690", 2 );

        var ex = assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion6 ) );
        assertEquals( "PRICE_CHANGED", ex.getCode() );
        assertEquals( new BigDecimal( "805.00" ), ex.getQuotation().totalPrice() );
        assertEquals( 0, bookingCount() );
        jdbc.update( "update contract set markup_percentage=20 where contract_id=?", contract );
        var requestForAssertion7 = request( "805", 2 );

        assertEquals( "PRICE_CHANGED", assertThrows( ReservationException.class,
                () -> service.confirm( requestForAssertion7 ) ).getCode() );
        assertEquals( 0, bookingCount() );
        var booked = service.confirm( request( "840", 2 ) );
        assertNull( jdbc.queryForMap(
                "select booked_rate_per_person_per_night from reservation_item where reservation_id=?",
                booked.reservationId() ).get( "booked_rate_per_person_per_night" ) );
        var before = jdbc.queryForList(
                "select n.* from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id where i.reservation_id=? order by occupied_date",
                booked.reservationId() );
        jdbc.update( "update contract set markup_percentage=25,status='SUSPENDED' where contract_id=?",
                contract );
        jdbc.update( "update contract_room_offering set rate_per_person_per_night=200 where offering_id=?",
                offering );
        jdbc.update( "update offering_seasonal_rate set rate_per_person_per_night=220 where offering_id=?",
                offering );
        jdbc.update( "update hotel set status='INACTIVE' where hotel_id=?", hotel );
        assertEquals( booked.rooms(), service.detail( booked.reservationId() ).rooms() );
        assertEquals( booked.totalPrice(), service.detail( booked.reservationId() ).totalPrice() );
        assertEquals( "CONFIRMED", service.detail( booked.reservationId() ).status() );
        assertEquals( ReservationResponse.WarningCode.HOTEL_INACTIVE,
                service.detail( booked.reservationId() ).warnings().get( 0 ).code() );
        service.cancel( booked.reservationId() );
        assertEquals( before, jdbc.queryForList(
                "select n.* from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id where i.reservation_id=? order by occupied_date",
                booked.reservationId() ) );
        assertEquals( new BigDecimal( "840.00" ), service.detail( booked.reservationId() ).totalPrice() );
    }

    // Verifies partial stay overbooking is rejected and cancellation immediately releases inventory.
    @Test
    void partialStayOverbookingIsRejectedAndCancellationImmediatelyReleasesInventory()
    {
        jdbc.update( "update contract_room_offering set contracted_room_count=1 where offering_id=?",
                offering );
        var booked = service.confirm( request( in.plusDays( 1 ), in.plusDays( 2 ), "230", 2 ) );
        assertTrue( search.search( searchRequest( 2 ) ).quotations().isEmpty() );
        var requestForAssertion8 = request( "690", 2 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion8 ) );
        var cancelled = service.cancel( booked.reservationId() );
        assertEquals( "CANCELLED", cancelled.status() );
        assertEquals( cancelled, service.cancel( booked.reservationId() ) );
        assertEquals( 1, search.search( searchRequest( 2 ) ).quotations().size() );
        assertEquals( "CONFIRMED", service.confirm( request( "690", 2 ) ).status() );
    }

    // Verifies identical rapid retries return one stored reservation.
    @Test
    void identicalRapidRetriesReturnOneStoredReservation()
    {
        var first = service.confirm( request( "690", 2 ) );
        var retry = service.confirm( request( "690", 2 ) );
        assertEquals( first.reservationId(), retry.reservationId() );
        assertEquals( 1, bookingCount() );
    }

    // Verifies failure after item insertion rolls back header items and snapshots.
    @Test
    void failureAfterItemInsertionRollsBackHeaderItemsAndSnapshots()
    {
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer( call ->
        {
            if( writes.incrementAndGet() == 2 )
                throw new IllegalStateException( "Injected second-night failure" );
            return call.callRealMethod();
        } ).when( repository ).night( anyInt(), any(), any(), any(), any() );
        var requestForAssertion9 = request( "690", 2 );

        assertThrows( IllegalStateException.class, () -> service.confirm( requestForAssertion9 ) );
        assertEquals( 0, bookingCount() );
        assertEquals( 2, writes.get() );
        assertEquals( 0, jdbc.queryForObject( "select count(*) from reservation_item where offering_id=?",
                Integer.class, offering ) );
    }

    // Verifies checkout exclusive allows adjacent reservations on the same offering.
    @Test
    void checkoutExclusiveAllowsAdjacentReservationsOnTheSameOffering()
    {
        jdbc.update( "update contract_room_offering set contracted_room_count=1 where offering_id=?",
                offering );
        service.confirm( request( in.minusDays( 1 ), in, "230", 2 ) );
        assertEquals( 1, search.search( searchRequest( 2 ) ).quotations().size() );
        assertEquals( "CONFIRMED", service.confirm( request( "690", 2 ) ).status() );
        assertEquals( 2, bookingCount() );
    }

    // Verifies old incomplete snapshots remain viewable and cancellable without inventing prices.
    @Test
    void oldIncompleteSnapshotsRemainViewableAndCancellableWithoutInventingPrices()
    {
        var booked = service.confirm( request( "690", 2 ) );
        jdbc.update( "delete from reservation_item_nightly_rate where reservation_item_id=?",
                booked.rooms().get( 0 ).reservationItemId() );
        assertNull( service.detail( booked.reservationId() ).totalPrice() );
        assertEquals( 1, service.list( Integer.toString( booked.reservationId() ), "", 0, 20 ).totalElements() );
        assertEquals( "CANCELLED", service.cancel( booked.reservationId() ).status() );
    }

    // Verifies confirmation waiting for hotel mutex rechecks the committed markup.
    @Test
    void confirmationWaitingForHotelMutexRechecksTheCommittedMarkup() throws Exception
    {
        var locked = new CountDownLatch( 1 );
        var release = new CountDownLatch( 1 );
        var pool = Executors.newFixedThreadPool( 2 );
        try
        {
            var maintenance = pool.submit( () -> tx.executeWithoutResult( s ->
            {
                hotels.findForUpdate( hotel ).orElseThrow();
                locked.countDown();
                try
                {
                    assertTrue( release.await( 5, TimeUnit.SECONDS ) );
                }
                catch( InterruptedException ex )
                {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException( ex );
                }
                jdbc.update( "update contract set markup_percentage=20 where contract_id=?", contract );
            } ) );
            assertTrue( locked.await( 5, TimeUnit.SECONDS ) );
            var confirmation = pool.submit( () ->
            {
                var selectedRequest = request( "690", 2 );
                return assertThrows( ReservationException.class, () -> service.confirm( selectedRequest ) );
            } );
            release.countDown();
            maintenance.get( 30, TimeUnit.SECONDS );
            assertEquals( "PRICE_CHANGED", confirmation.get( 30, TimeUnit.SECONDS ).getCode() );
            assertEquals( 0, bookingCount() );
        }
        finally
        {
            release.countDown();
            pool.shutdownNow();
            assertTrue( pool.awaitTermination( 30, TimeUnit.SECONDS ) );
        }
    }

    // Verifies two real competing transactions cannot consume one room twice.
    @Test
    void twoRealCompetingTransactionsCannotConsumeOneRoomTwice() throws Exception
    {
        jdbc.update( "update contract_room_offering set contracted_room_count=1 where offering_id=?",
                offering );
        var ready = new CountDownLatch( 2 );
        var start = new CountDownLatch( 1 );
        var pool = Executors.newFixedThreadPool( 2 );
        try
        {
            List<Future<String>> futures = new ArrayList<>();
            for( int adults : new int[] { 1, 2 } )
                futures.add( pool.submit( () ->
                {
                    ready.countDown();
                    assertTrue( start.await( 5, TimeUnit.SECONDS ) );
                    try
                    {
                        service.confirm( request( adults == 1 ? "345" : "690", adults ) );
                        return "CONFIRMED";
                    }
                    catch( ReservationException ex )
                    {
                        return ex.getCode();
                    }
                } ) );
            assertTrue( ready.await( 5, TimeUnit.SECONDS ) );
            start.countDown();
            var outcomes = List.of( futures.get( 0 ).get( 30, TimeUnit.SECONDS ),
                    futures.get( 1 ).get( 30, TimeUnit.SECONDS ) );
            assertEquals( 1, outcomes.stream().filter( "CONFIRMED"::equals ).count() );
            assertEquals( 1, outcomes.stream().filter( "UNAVAILABLE"::equals ).count() );
            assertEquals( 1, bookingCount() );
            assertEquals( 3, jdbc.queryForObject(
                    "select count(*) from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id where i.offering_id=?",
                    Integer.class, offering ) );
        }
        finally
        {
            start.countDown();
            pool.shutdownNow();
            assertTrue( pool.awaitTermination( 30, TimeUnit.SECONDS ) );
        }
    }

    // Verifies customized mixed quotation confirms exact types and cancellation restores both pools.
    @Test
    void customizedMixedQuotationConfirmsExactTypesAndCancellationRestoresBothPools()
    {
        tx.executeWithoutResult( s ->
        {
            otherType = repository.insert( "insert into room_type(name) values (?)",
                    "Sea View " + UUID.randomUUID() );
            otherOffering = repository.insert(
                    "insert into contract_room_offering(contract_id,room_type_id,rate_per_person_per_night,contracted_room_count,max_adults,status) values (?,?,?,1,3,'ENABLED')",
                    contract, otherType, new BigDecimal( "150.00" ) );
        } );
        var selection = new QuotationSelectionRequest( hotel, contract, in, out,
                List.of( new ReservationRequest.Room( otherOffering, 1 ),
                        new ReservationRequest.Room( offering, 2 ) ) );
        var quote = search.customize( selection );
        assertEquals( new BigDecimal( "1207.50" ), quote.totalPrice() );
        var booked = service.confirm(
                new ReservationRequest( customer, hotel, contract, in, out, selection.rooms(),
                        quote.totalPrice(), quote.currencyCode() ) );
        assertEquals( List.of( otherOffering, offering ),
                booked.rooms().stream().map( ReservationResponse.Item::offeringId ).toList() );
        assertThrows( com.suntravels.exception.QuotationSearchException.class,
                () -> search.customize( selection ) );
        service.cancel( booked.reservationId() );
        assertEquals( quote.totalPrice(), search.customize( selection ).totalPrice() );
        assertEquals( 6, jdbc.queryForObject(
                "select count(*) from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id where i.reservation_id=?",
                Integer.class, booked.reservationId() ) );
    }

    // Verifies occupancy above ten uses contractual limit and rechecks changed capacity.
    @Test
    void occupancyAboveTenUsesContractualLimitAndRechecksChangedCapacity()
    {
        // Only this test's disposable fixture is changed; the application needs no schema/data migration.
        jdbc.update( "update contract_room_offering set max_adults=12 where offering_id=?", offering );
        var q = search.search( searchRequest( 12 ) ).quotations().get( 0 );
        assertEquals( new BigDecimal( "4140.00" ), q.totalPrice() );
        jdbc.update( "update contract_room_offering set max_adults=6 where offering_id=?", offering );
        var requestForAssertion10 = request( "4140", 12 );

        assertEquals( "UNAVAILABLE", assertThrows( ReservationException.class,
                () -> service.confirm( requestForAssertion10 ) ).getCode() );
        assertEquals( 0, bookingCount() );
        jdbc.update( "update contract_room_offering set max_adults=12 where offering_id=?", offering );
        var booked = service.confirm( request( "4140", 12 ) );
        assertEquals( "CONFIRMED", booked.status() );
        assertEquals( 12, booked.rooms().get( 0 ).adults() );
        assertEquals( 3, booked.rooms().get( 0 ).nightlyPrices().size() );
        assertEquals( new BigDecimal( "4140.00" ), booked.totalPrice() );
    }

    // Changes the fixture hotel’s status through the hotel service.
    void hotelStatus( HotelStatus status )
    {
        var request = new HotelStatusUpdateRequest();
        request.setStatus( status );
        hotelService.updateHotelStatus( hotel, request );
    }

    // Extracts operational warning codes for assertions.
    List<ReservationResponse.WarningCode> warningCodes( ReservationResponse r )
    {
        return r.warnings().stream().map( ReservationResponse.OperationalWarning::code ).toList();
    }

    // Reads stored reservation items to verify history is unchanged.
    List<Map<String,Object>> storedItems( int id )
    {
        return jdbc.queryForList(
                "select * from reservation_item where reservation_id=? order by reservation_item_id", id );
    }

    // Reads stored nightly prices to verify history is unchanged.
    List<Map<String,Object>> storedNights( int id )
    {
        return jdbc.queryForList(
                "select n.* from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id where i.reservation_id=? order by n.reservation_item_id,n.occupied_date",
                id );
    }

    // Verifies inactive hotel preserves confirmed booking and snapshots but blocks new bookings.
    @Test
    void inactiveHotelPreservesConfirmedBookingAndSnapshotsButBlocksNewBookings()
    {
        var booked = service.confirm( request( "690", 2 ) );
        assertTrue( booked.warnings().isEmpty() );
        var itemsBefore = storedItems( booked.reservationId() );
        var nightsBefore = storedNights( booked.reservationId() );
        hotelStatus( HotelStatus.INACTIVE );
        var current = service.detail( booked.reservationId() );
        assertEquals( "CONFIRMED", current.status() );
        assertEquals( List.of( ReservationResponse.WarningCode.HOTEL_INACTIVE ), warningCodes( current ) );
        assertEquals( booked.rooms(), current.rooms() );
        assertEquals( booked.totalPrice(), current.totalPrice() );
        assertEquals( itemsBefore, storedItems( booked.reservationId() ) );
        assertEquals( nightsBefore, storedNights( booked.reservationId() ) );
        assertTrue( search.search( searchRequest( 1 ) ).quotations().isEmpty() );
        var requestForAssertion11 = request( "345", 1 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion11 ) );
        assertEquals( 1, bookingCount() );
        assertEquals( current.warnings(),
                service.list( Integer.toString( booked.reservationId() ), "CONFIRMED", 0, 20 ).reservations().get(
                        0 ).warnings() );
        hotelStatus( HotelStatus.ACTIVE );
        assertTrue( service.detail( booked.reservationId() ).warnings().isEmpty() );
    }

    // Verifies independent warnings disappear only when their own condition clears and cancellation is explicit.
    @Test
    void independentWarningsDisappearOnlyWhenTheirOwnConditionClearsAndCancellationIsExplicit()
    {
        var booked = service.confirm( request( "690", 2 ) );
        var itemsBefore = storedItems( booked.reservationId() );
        var nightsBefore = storedNights( booked.reservationId() );
        hotelStatus( HotelStatus.INACTIVE );
        var affected = service.detail( booked.reservationId() );
        assertEquals( "CONFIRMED", affected.status() );
        assertEquals( List.of( ReservationResponse.WarningCode.HOTEL_INACTIVE ), warningCodes( affected ) );
        hotelStatus( HotelStatus.ACTIVE );
        assertTrue( service.detail( booked.reservationId() ).warnings().isEmpty() );
        assertEquals( 1, search.search( searchRequest( 1 ) ).quotations().size() );
        assertEquals( itemsBefore, storedItems( booked.reservationId() ) );
        assertEquals( nightsBefore, storedNights( booked.reservationId() ) );
        var cancelled = service.cancel( booked.reservationId() );
        assertEquals( "CANCELLED", cancelled.status() );
        assertTrue( cancelled.warnings().isEmpty() );
        assertEquals( itemsBefore, storedItems( booked.reservationId() ) );
        assertEquals( nightsBefore, storedNights( booked.reservationId() ) );
        hotelStatus( HotelStatus.INACTIVE );
        assertTrue( service.detail( booked.reservationId() ).warnings().isEmpty() );
        hotelStatus( HotelStatus.ACTIVE );
        assertEquals( 3,
                search.search( searchRequest( 1 ) ).quotations().get( 0 ).rooms().get( 0 ).availableRoomsForStay() );
    }

    // Verifies stay date filters match occupied dates and preserve paging counts.
    @Test
    void stayDateFiltersMatchOccupiedDatesAndPreservePagingCounts()
    {
        var booked = service.confirm( request( "690", 2 ) );
        String reference = Integer.toString( booked.reservationId() );
        assertEquals( 1, service.list( reference, "CONFIRMED", 0, 20, in, in ).totalElements() );
        assertEquals( 1, service.list( reference, "CONFIRMED", 0, 20, out.minusDays( 1 ),
                out.minusDays( 1 ) ).totalElements() );
        assertEquals( 0, service.list( reference, "CONFIRMED", 0, 20, out, out ).totalElements() );
        assertEquals( 0, service.list( reference, "CONFIRMED", 0, 20, null, in.minusDays( 1 ) ).totalElements() );
        assertEquals( 1, service.list( reference, "CONFIRMED", 0, 20, null, in ).totalElements() );
        assertEquals( 1, service.list( reference, "CONFIRMED", 0, 20, in, null ).totalElements() );
        assertEquals( 0, service.list( reference, "CANCELLED", 0, 20, in, out ).totalElements() );
        var next = service.list( reference, "CONFIRMED", 1, 1, in, out );
        assertEquals( 1, next.totalElements() );
        assertTrue( next.reservations().isEmpty() );
        assertEquals( 400, assertThrows( ReservationException.class,
                () -> service.list( reference, "", 0, 20, out, in ) ).getStatus() );
    }

    // Verifies suspended contract preserves commitments and reports distinct termination blockers.
    @Test
    void suspendedContractPreservesCommitmentsAndReportsDistinctTerminationBlockers()
    {
        var first = service.confirm( request( "1035", 2, 1 ) );
        var second = service.confirm( request( "345", 1 ) );
        var itemsBefore = storedItems( first.reservationId() );
        var nightsBefore = storedNights( first.reservationId() );
        contractService.suspendContract( contract );
        assertEquals( "CONFIRMED", service.detail( first.reservationId() ).status() );
        assertEquals( List.of( ReservationResponse.WarningCode.CONTRACT_SUSPENDED ),
                warningCodes( service.detail( first.reservationId() ) ) );
        assertTrue( search.search( searchRequest( 1 ) ).quotations().isEmpty() );
        var requestForAssertion13 = request( "690", 2 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion13 ) );
        var ex = assertThrows( com.suntravels.exception.ContractException.class,
                () -> contractService.terminateContract( contract ) );
        assertTrue( ex.getMessage().contains( "2 ongoing or upcoming confirmed reservations" ) );
        assertEquals( ContractStatus.SUSPENDED, contractService.getContractById( contract ).status() );
        assertEquals( itemsBefore, storedItems( first.reservationId() ) );
        assertEquals( nightsBefore, storedNights( first.reservationId() ) );
        contractService.reapproveContract( contract );
        assertTrue( service.detail( first.reservationId() ).warnings().isEmpty() );
        assertTrue( search.search(
                searchRequest( 1 ) ).quotations().isEmpty() ); // All three rooms are still committed.
        assertEquals( "CONFIRMED", service.detail( second.reservationId() ).status() );
    }

    // Creates an additional offering for mixed-room reservation tests.
    void additionalOffering()
    {
        tx.executeWithoutResult( s ->
        {
            otherType = repository.insert( "insert into room_type(name) values (?)",
                    "Alternative " + UUID.randomUUID() );
            otherOffering = repository.insert(
                    "insert into contract_room_offering(contract_id,room_type_id,rate_per_person_per_night,contracted_room_count,max_adults,status) values (?,?,?,2,3,'ENABLED')",
                    contract, otherType, new BigDecimal( "150.00" ) );
        } );
    }

    // Verifies suspended offering preserves mixed booking and leaves other offerings bookable.
    @Test
    void suspendedOfferingPreservesMixedBookingAndLeavesOtherOfferingsBookable()
    {
        additionalOffering();
        var selection = new QuotationSelectionRequest( hotel, contract, in, out,
                List.of( new ReservationRequest.Room( otherOffering, 1 ),
                        new ReservationRequest.Room( offering, 2 ) ) );
        var q = search.customize( selection );
        var booked = service.confirm(
                new ReservationRequest( customer, hotel, contract, in, out, selection.rooms(), q.totalPrice(),
                        q.currencyCode() ) );
        var itemsBefore = storedItems( booked.reservationId() );
        var nightsBefore = storedNights( booked.reservationId() );
        offeringService.status( offering, new OfferingStatusRequest( OfferingStatus.SUSPENDED ) );
        var detail = service.detail( booked.reservationId() );
        assertEquals( "CONFIRMED", detail.status() );
        assertEquals( List.of( ReservationResponse.WarningCode.OFFERING_SUSPENDED ), warningCodes( detail ) );
        assertEquals( offering, detail.warnings().get( 0 ).offeringId() );
        assertNotNull( detail.warnings().get( 0 ).roomTypeName() );
        assertThrows( com.suntravels.exception.QuotationSearchException.class,
                () -> search.customize( selection ) );
        var requestForAssertion14 = request( "345", 1 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion14 ) );
        assertEquals( List.of( otherOffering ), search.search( searchRequest( 1 ) ).quotations().stream().map(
                r -> r.rooms().get( 0 ).offeringId() ).toList() );
        offeringService.status( offering, new OfferingStatusRequest( OfferingStatus.ENABLED ) );
        assertTrue( service.detail( booked.reservationId() ).warnings().isEmpty() );
        assertEquals( 2, search.search( searchRequest( 1 ) ).quotations().size() );
        contractService.suspendContract( contract );
        offeringService.status( offering, new OfferingStatusRequest( OfferingStatus.SUSPENDED ) );
        offeringService.status( offering, new OfferingStatusRequest( OfferingStatus.ENABLED ) );
        assertTrue( search.search( searchRequest( 1 ) ).quotations().isEmpty() );
        var requestForAssertion15 = request( "345", 1 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion15 ) );
        assertEquals( itemsBefore, storedItems( booked.reservationId() ) );
        assertEquals( nightsBefore, storedNights( booked.reservationId() ) );
        contractService.reapproveContract( contract );
        hotelStatus( HotelStatus.INACTIVE );
        assertTrue( search.search( searchRequest( 1 ) ).quotations().isEmpty() );
        var requestForAssertion16 = request( "345", 1 );

        assertThrows( ReservationException.class, () -> service.confirm( requestForAssertion16 ) );
        hotelStatus( HotelStatus.ACTIVE );
        assertEquals( 2, search.search( searchRequest( 1 ) ).quotations().size() );
    }

    // Verifies remaining warnings are independent deduplicated and disappear when conditions clear.
    @Test
    void remainingWarningsAreIndependentDeduplicatedAndDisappearWhenConditionsClear()
    {
        var booked = service.confirm( request( "1035", 2, 1 ) );
        var nightsBefore = storedNights( booked.reservationId() );
        contractService.suspendContract( contract );
        offeringService.status( offering, new OfferingStatusRequest( OfferingStatus.SUSPENDED ) );
        hotelStatus( HotelStatus.INACTIVE );
        var warnings = service.detail( booked.reservationId() ).warnings();
        assertEquals( 3, warnings.size() );
        assertEquals( Set.of( ReservationResponse.WarningCode.HOTEL_INACTIVE,
                        ReservationResponse.WarningCode.CONTRACT_SUSPENDED,
                        ReservationResponse.WarningCode.OFFERING_SUSPENDED ),
                new HashSet<>( warningCodes( service.detail( booked.reservationId() ) ) ) );
        offeringService.status( offering, new OfferingStatusRequest( OfferingStatus.ENABLED ) );
        assertEquals( 2, service.detail( booked.reservationId() ).warnings().size() );
        hotelStatus( HotelStatus.ACTIVE );
        assertEquals( 1, service.detail( booked.reservationId() ).warnings().size() );
        assertEquals( List.of( ReservationResponse.WarningCode.CONTRACT_SUSPENDED ),
                warningCodes( service.detail( booked.reservationId() ) ) );
        contractService.reapproveContract( contract );
        assertTrue( service.detail( booked.reservationId() ).warnings().isEmpty() );
        assertEquals( nightsBefore, storedNights( booked.reservationId() ) );
        contractService.suspendContract( contract );
        var cancelled = service.cancel( booked.reservationId() );
        assertTrue( cancelled.warnings().isEmpty() );
        assertEquals( "CANCELLED", cancelled.status() );
    }

    // Verifies completed confirmed stay has no operational warnings even when every current status is suspended.
    @Test
    void completedConfirmedStayHasNoOperationalWarningsEvenWhenEveryCurrentStatusIsSuspended()
    {
        var booked = service.confirm( request( "690", 2 ) );
        // Move the isolated fixture's stay and snapshots together to a completed business date.
        var today = LocalDate.now( clock );
        long shift = java.time.temporal.ChronoUnit.DAYS.between( in, today.minusDays( 3 ) );
        jdbc.update(
                "update reservation_item_nightly_rate set occupied_date=date_add(occupied_date,interval ? day) where reservation_item_id=?",
                shift, booked.rooms().get( 0 ).reservationItemId() );
        jdbc.update( "update reservation set check_in_date=?,check_out_date=? where reservation_id=?",
                today.minusDays( 3 ), today, booked.reservationId() );
        contractService.suspendContract( contract );
        offeringService.status( offering, new OfferingStatusRequest( OfferingStatus.SUSPENDED ) );
        hotelStatus( HotelStatus.INACTIVE );
        assertEquals( "CONFIRMED", service.detail( booked.reservationId() ).status() );
        assertTrue( service.detail( booked.reservationId() ).warnings().isEmpty() );
    }

    // Verifies actual confirmation and termination serialize on the hotel mutex.
    @Test
    void actualConfirmationAndTerminationSerializeOnTheHotelMutex() throws Exception
    {
        var written = new CountDownLatch( 1 );
        var release = new CountDownLatch( 1 );
        var attempting = new CountDownLatch( 1 );
        var pool = Executors.newFixedThreadPool( 2 );
        try
        {
            var booking = pool.submit( () -> tx.execute( s ->
            {
                var booked = service.confirm( request( "690", 2 ) );
                written.countDown();
                try
                {
                    assertTrue( release.await( 10, TimeUnit.SECONDS ) );
                }
                catch( InterruptedException ex )
                {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException( ex );
                }
                return booked;
            } ) );
            assertTrue( written.await( 10, TimeUnit.SECONDS ) );
            var terminating = pool.submit( () ->
            {
                attempting.countDown();
                return assertThrows( com.suntravels.exception.ContractException.class,
                        () -> contractService.terminateContract( contract ) );
            } );
            assertTrue( attempting.await( 10, TimeUnit.SECONDS ) );
            assertThrows( TimeoutException.class, () -> terminating.get( 200, TimeUnit.MILLISECONDS ) );
            release.countDown();
            assertEquals( "CONFIRMED", booking.get( 20, TimeUnit.SECONDS ).status() );
            assertTrue( terminating.get( 20, TimeUnit.SECONDS ).getMessage().contains(
                    "1 ongoing or upcoming confirmed reservation" ) );
            assertEquals( ContractStatus.APPROVED, contractService.getContractById( contract ).status() );
            assertEquals( 1, bookingCount() );
        }
        finally
        {
            release.countDown();
            pool.shutdownNow();
            assertTrue( pool.awaitTermination( 20, TimeUnit.SECONDS ) );
        }
    }

    // Verifies termination winning the mutex prevents a new confirmation.
    @Test
    void terminationWinningTheMutexPreventsANewConfirmation() throws Exception
    {
        var terminated = new CountDownLatch( 1 );
        var release = new CountDownLatch( 1 );
        var pool = Executors.newFixedThreadPool( 2 );
        try
        {
            var change = pool.submit( () -> tx.executeWithoutResult( s ->
            {
                contractService.terminateContract( contract );
                terminated.countDown();
                try
                {
                    assertTrue( release.await( 10, TimeUnit.SECONDS ) );
                }
                catch( InterruptedException ex )
                {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException( ex );
                }
            } ) );
            assertTrue( terminated.await( 10, TimeUnit.SECONDS ) );
            var booking = pool.submit( () ->
            {
                var selectedRequest = request( "690", 2 );
                return assertThrows( ReservationException.class, () -> service.confirm( selectedRequest ) );
            } );
            release.countDown();
            change.get( 20, TimeUnit.SECONDS );
            assertEquals( "UNAVAILABLE", booking.get( 20, TimeUnit.SECONDS ).getCode() );
            assertEquals( 0, bookingCount() );
            assertEquals( ContractStatus.TERMINATED, contractService.getContractById( contract ).status() );
        }
        finally
        {
            release.countDown();
            pool.shutdownNow();
            assertTrue( pool.awaitTermination( 20, TimeUnit.SECONDS ) );
        }
    }

    // Verifies stay periods compose with status search and ten record paging before pagination.
    @Test
    void stayPeriodsComposeWithStatusSearchAndTenRecordPagingBeforePagination()
    {
        var today = LocalDate.now( clock );
        List<Integer> ids = new ArrayList<>();
        for( int i = 0; i < 25; i++ )
        {
            LocalDate start = i < 8 ? today.minusDays( 3 ) : i < 16 ? today : today.plusDays( 1 );
            LocalDate end = i < 8 ? today : today.plusDays( 3 );
            ids.add( repository.insert(
                    "insert into reservation(customer_id,check_in_date,check_out_date,status,currency_code) values (?,?,?,?,?)",
                    customer, start, end, i % 2 == 0 ? "CONFIRMED" : "CANCELLED", "USD" ) );
        }
        String name = jdbc.queryForObject( "select name from customer where customer_id=?", String.class,
                customer );
        var first = service.list( name, "", 0, 10, null, null, "" );
        var second = service.list( name, "", 1, 10, null, null, "" );
        var last = service.list( name, "", 2, 10, null, null, "" );
        assertEquals( 25, first.totalElements() );
        assertEquals( 10, first.size() );
        assertEquals( 10, first.reservations().size() );
        assertEquals( 10, second.reservations().size() );
        assertEquals( 5, last.reservations().size() );
        var expected = new ArrayList<>( ids );
        Collections.reverse( expected );
        var actual = new ArrayList<Integer>();
        for( var page : List.of( first, second, last ) )
            actual.addAll( page.reservations().stream().map( ReservationResponse::reservationId ).toList() );
        assertEquals( expected, actual );
        assertEquals( 9, service.list( name, "", 0, 10, null, null, "UPCOMING" ).totalElements() );
        assertEquals( 8, service.list( name, "", 0, 10, null, null, "ONGOING" ).totalElements() );
        assertEquals( 8, service.list( name, "", 0, 10, null, null, "PAST" ).totalElements() );
        var upcoming = service.list( name, "CONFIRMED", 0, 10, null, null, "UPCOMING" );
        assertEquals( 5, upcoming.totalElements() );
        assertTrue( upcoming.reservations().stream().allMatch(
                r -> r.status().equals( "CONFIRMED" ) && r.checkIn().isAfter( today ) ) );
        var ongoing = service.list( name, "CONFIRMED", 0, 10, null, null, "ONGOING" );
        assertEquals( 4, ongoing.totalElements() );
        assertTrue( ongoing.reservations().stream().allMatch(
                r -> !r.checkIn().isAfter( today ) && r.checkOut().isAfter( today ) ) );
        var past = service.list( name, "CANCELLED", 0, 10, null, null, "PAST" );
        assertEquals( 4, past.totalElements() );
        assertTrue( past.reservations().stream().allMatch(
                r -> r.status().equals( "CANCELLED" ) && r.checkOut().equals( today ) ) );
        assertEquals( 0, service.list( name, "", 0, 10, today.plusDays( 4 ), null, "ONGOING" ).totalElements() );
        assertEquals( 0, service.list( name + " missing", "", 0, 10, null, null, "UPCOMING" ).totalElements() );
        assertEquals( 400, assertThrows( ReservationException.class,
                () -> service.list( name, "", 0, 10, null, null, "COMPLETED" ) ).getStatus() );
    }

    // Verifies stay period filtering does not change operational warnings.
    @Test
    void stayPeriodFilteringDoesNotChangeOperationalWarnings()
    {
        var booked = service.confirm( request( "690", 2 ) );
        hotelStatus( HotelStatus.INACTIVE );
        var before = service.detail( booked.reservationId() );
        var filtered = service.list( Integer.toString( booked.reservationId() ), "CONFIRMED", 0, 10, null, null,
                "UPCOMING" );
        assertEquals( 1, filtered.totalElements() );
        assertEquals( before.warnings(), filtered.reservations().get( 0 ).warnings() );
        assertEquals( before.rooms(), filtered.reservations().get( 0 ).rooms() );
        assertEquals( "CONFIRMED", filtered.reservations().get( 0 ).status() );
    }
}
