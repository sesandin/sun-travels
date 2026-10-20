package com.suntravels.integration;

import com.suntravels.dto.OfferingRateRequest;
import com.suntravels.dto.OfferingRequest;
import com.suntravels.dto.OfferingStatusRequest;
import com.suntravels.dto.SeasonalRateRequest;
import com.suntravels.entity.Contract;
import com.suntravels.entity.ContractStatus;
import com.suntravels.entity.Hotel;
import com.suntravels.entity.HotelStatus;
import com.suntravels.entity.OfferingStatus;
import com.suntravels.entity.RoomType;
import com.suntravels.exception.OfferingException;
import com.suntravels.repository.ContractRepository;
import com.suntravels.repository.HotelRepository;
import com.suntravels.repository.OfferingRepository;
import com.suntravels.repository.RoomTypeRepository;
import com.suntravels.service.OfferingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt in only against a disposable MySQL test database with the existing schema.
 * Creates isolated fixture rows and removes only those rows; never runs DDL.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable( named = "SUNTRAVELS_MYSQL_TESTS", matches = "true" )
class OfferingMySqlIntegrationTest
{
    final LocalDate from = LocalDate.of( 2026, 1, 1 ), to = LocalDate.of( 2026, 12, 31 );
    @Autowired
    OfferingService service;
    @Autowired
    com.suntravels.service.DirectoryService directories;
    @Autowired
    ContractRepository contracts;
    @Autowired
    OfferingRepository offerings;
    @Autowired
    HotelRepository hotels;
    @Autowired
    RoomTypeRepository rooms;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager manager;
    Integer hotelId, roomId, contractId, offeringId, customerId, reservationId, itemId;

    // Creates the transaction helper used by integration tests.
    TransactionTemplate tx()
    {
        return new TransactionTemplate( manager );
    }

    // Creates isolated database fixtures for the test.
    @BeforeEach
    void seed()
    {
        assertTrue( jdbc.queryForObject( "select version()", String.class ).startsWith( "8." ),
                "Requires MySQL 8" );
        tx().executeWithoutResult( t ->
        {
            Hotel h = new Hotel();
            h.setName( "Seasonal concurrency test " + UUID.randomUUID() );
            h.setStatus( HotelStatus.ACTIVE );
            hotelId = hotels.saveAndFlush( h ).getHotelId();
            RoomType room = new RoomType();
            room.setName( "Seasonal test " + UUID.randomUUID() );
            roomId = rooms.saveAndFlush( room ).getRoomTypeId();
            Contract c = new Contract();
            c.setContractCode( "IT-" + UUID.randomUUID() );
            c.setHotel( h );
            c.setStatus( ContractStatus.DRAFT );
            c.setValidFrom( from );
            c.setValidTo( to );
            c.setCurrencyCode( "USD" );
            c.setMarkupPercentage( BigDecimal.TEN );
            contractId = contracts.saveAndFlush( c ).getContractId();
            offeringId = service.create( contractId,
                    new OfferingRequest( roomId, BigDecimal.TEN, 10, 2, OfferingStatus.ENABLED ) ).offeringId();
        } );
    }

    // Removes the database records created by the test.
    @AfterEach
    void cleanup()
    {
        tx().executeWithoutResult( t ->
        {
            if( itemId != null )
            {
                jdbc.update( "delete from reservation_item_nightly_rate where reservation_item_id = ?",
                        itemId );
                jdbc.update( "delete from reservation_item where reservation_item_id = ?", itemId );
            }
            if( reservationId != null )
                jdbc.update( "delete from reservation where reservation_id = ?", reservationId );
            if( customerId != null ) jdbc.update( "delete from customer where customer_id = ?", customerId );
            if( offeringId != null )
                jdbc.update( "delete from offering_seasonal_rate where offering_id = ?", offeringId );
            if( contractId != null )
            {
                jdbc.update( "delete from contract_room_offering where contract_id = ?", contractId );
                jdbc.update( "delete from contract where contract_id = ?", contractId );
            }
            if( roomId != null ) jdbc.update( "delete from room_type where room_type_id = ?", roomId );
            if( hotelId != null ) jdbc.update( "delete from hotel where hotel_id = ?", hotelId );
        } );
    }

    // Verifies directory executes fetch and aggregate queries for historical offerings.
    @Test
    void directoryExecutesFetchAndAggregateQueriesForHistoricalOfferings()
    {
        service.createSeason( offeringId, new SeasonalRateRequest( from, to, BigDecimal.TEN ) );
        tx().executeWithoutResult( t ->
        {
            Contract c = contracts.findForUpdate( contractId ).orElseThrow();
            c.setStatus( ContractStatus.TERMINATED );
            contracts.saveAndFlush( c );
            Hotel h = hotels.findById( hotelId ).orElseThrow();
            h.setStatus( HotelStatus.INACTIVE );
            hotels.saveAndFlush( h );
        } );
        var row = service.directory().stream().filter(
                r -> offeringId.equals( r.offeringId() ) ).findFirst().orElseThrow();
        assertEquals( ContractStatus.TERMINATED, row.contractStatus() );
        assertEquals( HotelStatus.INACTIVE, row.hotelStatus() );
        assertEquals( 1L, row.seasonalRateCount() );
        assertEquals( 10, row.contractedRoomCount() );
        assertEquals( "USD", row.currencyCode() );
    }

    // Verifies concurrent inclusive overlap has one committed winner.
    @Test
    void concurrentInclusiveOverlapHasOneCommittedWinner() throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool( 2 );
        CountDownLatch firstWritten = new CountDownLatch( 1 ), release = new CountDownLatch(
                1 ), secondStarted = new CountDownLatch( 1 );
        try
        {
            Future<?> first = pool.submit( () -> tx().executeWithoutResult( t ->
            {
                service.createSeason( offeringId,
                        new SeasonalRateRequest( from, from.plusDays( 10 ), BigDecimal.TEN ) );
                firstWritten.countDown();
                try
                {
                    if( !release.await( 10, TimeUnit.SECONDS ) )
                        throw new IllegalStateException( "Release timeout" );
                }
                catch( InterruptedException ex )
                {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException( ex );
                }
            } ) );
            assertTrue( firstWritten.await( 10, TimeUnit.SECONDS ) );
            Future<Integer> second = pool.submit( () ->
            {
                secondStarted.countDown();
                try
                {
                    tx().executeWithoutResult( t -> service.createSeason( offeringId,
                            new SeasonalRateRequest( from.plusDays( 10 ), to, BigDecimal.ONE ) ) );
                    return 201;
                }
                catch( OfferingException ex )
                {
                    return ex.getStatus();
                }
            } );
            assertTrue( secondStarted.await( 10, TimeUnit.SECONDS ) );
            assertThrows( TimeoutException.class, () -> second.get( 200, TimeUnit.MILLISECONDS ) );
            release.countDown();
            first.get( 10, TimeUnit.SECONDS );
            assertEquals( 409, second.get( 10, TimeUnit.SECONDS ) );
            assertEquals( 1, service.seasonalRates( offeringId ).size() );
        }
        finally
        {
            release.countDown();
            pool.shutdownNow();
            assertTrue( pool.awaitTermination( 15, TimeUnit.SECONDS ) );
        }
    }

    // Verifies approved maintenance preserves confirmed snapshots.
    @Test
    void approvedMaintenancePreservesConfirmedSnapshots()
    {
        // APPROVED is fixture state only; production approval remains unavailable.
        tx().executeWithoutResult( t ->
        {
            Contract c = contracts.findForUpdate( contractId ).orElseThrow();
            c.setStatus( ContractStatus.APPROVED );
            contracts.saveAndFlush( c );
            jdbc.update( "insert into customer(name) values (?)", "Snapshot test " + UUID.randomUUID() );
            customerId = jdbc.queryForObject( "select last_insert_id()", Integer.class );
            jdbc.update(
                    "insert into reservation(check_in_date,check_out_date,status,customer_id,currency_code) values (?,?,?,?,?)",
                    from, from.plusDays( 1 ), "CONFIRMED", customerId, "USD" );
            reservationId = jdbc.queryForObject( "select last_insert_id()", Integer.class );
            jdbc.update(
                    "insert into reservation_item(number_of_adults,offering_id,reservation_id,booked_rate_per_person_per_night,booked_markup_percentage,booked_selling_price) values (?,?,?,?,?,?)",
                    2, offeringId, reservationId, new BigDecimal( "10" ), new BigDecimal( "15" ),
                    new BigDecimal( "23" ) );
            itemId = jdbc.queryForObject( "select last_insert_id()", Integer.class );
            jdbc.update(
                    "insert into reservation_item_nightly_rate(reservation_item_id,occupied_date,hotelier_rate,markup_percentage,selling_price) values (?,?,?,?,?)",
                    itemId, from, new BigDecimal( "10" ), new BigDecimal( "15" ), new BigDecimal( "23" ) );
        } );
        var reservation = jdbc.queryForMap( "select * from reservation where reservation_id = ?",
                reservationId );
        var item = jdbc.queryForMap( "select * from reservation_item where reservation_item_id = ?", itemId );
        var nightly = jdbc.queryForMap(
                "select * from reservation_item_nightly_rate where reservation_item_id = ?", itemId );
        service.rate( offeringId, new OfferingRateRequest( new BigDecimal( "30" ) ) );
        var seasonal = service.createSeason( offeringId,
                new SeasonalRateRequest( from, to, new BigDecimal( "40" ) ) );
        service.updateSeason( offeringId, seasonal.seasonalRateId(),
                new SeasonalRateRequest( from, to, new BigDecimal( "50" ) ) );
        service.status( offeringId, new OfferingStatusRequest( OfferingStatus.SUSPENDED ) );
        assertEquals( reservation,
                jdbc.queryForMap( "select * from reservation where reservation_id = ?", reservationId ) );
        assertEquals( item,
                jdbc.queryForMap( "select * from reservation_item where reservation_item_id = ?", itemId ) );
        assertEquals( nightly,
                jdbc.queryForMap( "select * from reservation_item_nightly_rate where reservation_item_id = ?",
                        itemId ) );
    }

    // Verifies real query permits adjacent periods excludes self and scopes offering.
    @Test
    void realQueryPermitsAdjacentPeriodsExcludesSelfAndScopesOffering()
    {
        var first = service.createSeason( offeringId,
                new SeasonalRateRequest( from, from.plusDays( 10 ), BigDecimal.TEN ) );
        service.createSeason( offeringId, new SeasonalRateRequest( from.plusDays( 11 ), to, BigDecimal.ONE ) );
        service.updateSeason( offeringId, first.seasonalRateId(),
                new SeasonalRateRequest( from, from.plusDays( 10 ), BigDecimal.ONE ) );
        var requestForAssertion1 = new SeasonalRateRequest( from.plusDays( 10 ), from.plusDays( 10 ),
                BigDecimal.ONE );

        assertEquals( 409, assertThrows( OfferingException.class,
                () -> service.createSeason( offeringId, requestForAssertion1 ) ).getStatus() );
        Integer otherRoom = tx().execute( t ->
        {
            RoomType r = new RoomType();
            r.setName( "Other test " + UUID.randomUUID() );
            return rooms.saveAndFlush( r ).getRoomTypeId();
        } );
        Integer otherOffering = null;
        try
        {
            otherOffering = service.create( contractId, new OfferingRequest( otherRoom, BigDecimal.TEN, 1, 1,
                    OfferingStatus.ENABLED ) ).offeringId();
            service.createSeason( otherOffering, new SeasonalRateRequest( from, to, BigDecimal.TEN ) );
            assertEquals( 2, service.seasonalRates( offeringId ).size() );
            assertEquals( 1, service.seasonalRates( otherOffering ).size() );
        }
        finally
        {
            final Integer otherId = otherOffering;
            tx().executeWithoutResult( t ->
            {
                if( otherId != null )
                {
                    jdbc.update( "delete from offering_seasonal_rate where offering_id = ?", otherId );
                    jdbc.update( "delete from contract_room_offering where offering_id = ?", otherId );
                }
                jdbc.update( "delete from room_type where room_type_id = ?", otherRoom );
            } );
        }
    }

    // Verifies directory filters and counts are applied before ten row pages in my sql.
    @Test
    void directoryFiltersAndCountsAreAppliedBeforeTenRowPagesInMySql()
    {
        String prefix = "Page_%" + UUID.randomUUID();
        java.util.List<Integer> extraRooms = new java.util.ArrayList<>();
        java.util.List<Integer> extraOfferings = new java.util.ArrayList<>();
        try
        {
            for( int index = 1; index <= 24; index++ )
            {
                final int number = index;
                Integer id = tx().execute( t ->
                {
                    RoomType room = new RoomType();
                    room.setName( prefix + String.format( "%02d", number ) );
                    return rooms.saveAndFlush( room ).getRoomTypeId();
                } );
                extraRooms.add( id );
                extraOfferings.add( service.create( contractId,
                        new OfferingRequest( id, BigDecimal.TEN, index, 2, OfferingStatus.ENABLED ) ).offeringId() );
            }
            var filters = java.util.Map.of( "hotelId", hotelId.toString(), "contractStatus", "DRAFT",
                    "offeringStatus", "ENABLED", "sortBy", "rooms", "direction", "desc" );
            var first = directories.offerings( filters, 0 );
            var second = directories.offerings( filters, 1 );
            var last = directories.offerings( filters, 2 );
            assertEquals( 10, first.items().size() );
            assertEquals( 10, second.items().size() );
            assertEquals( 5, last.items().size() );
            assertEquals( 25, first.totalElements() );
            assertEquals( 3, first.totalPages() );
            assertEquals( 24, first.items().get( 0 ).contractedRoomCount() );
            assertTrue( first.items().stream().noneMatch( second.items()::contains ) );
            assertEquals( 2, directories.offerings( filters, 99 ).page() );
            var roomPage = directories.rooms( prefix, 2 );
            assertEquals( 24, roomPage.totalElements() );
            assertEquals( 4, roomPage.items().size() );
            assertEquals( 3, roomPage.totalPages() );
            assertTrue( roomPage.items().stream().allMatch( room -> room.name().startsWith( prefix ) ) );
            String code = tx().execute( t -> contracts.findById( contractId ).orElseThrow().getContractCode() );
            assertEquals( 1, directories.contracts( code, "DRAFT", 0 ).totalElements() );
            assertEquals( 0, directories.contracts( code, "APPROVED", 0 ).totalElements() );
            String hotelName = tx().execute( t -> hotels.findById( hotelId ).orElseThrow().getName() );
            assertEquals( 1, directories.hotels( hotelName, "ACTIVE", 0 ).totalElements() );
            assertEquals( 0, directories.hotels( hotelName, "INACTIVE", 0 ).totalElements() );
        }
        finally
        {
            tx().executeWithoutResult( t ->
            {
                for( Integer id : extraOfferings )
                    jdbc.update( "delete from contract_room_offering where offering_id = ?", id );
                for( Integer id : extraRooms ) jdbc.update( "delete from room_type where room_type_id = ?", id );
            } );
        }
    }
}
