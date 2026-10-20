package com.suntravels.service;

import com.suntravels.dto.OfferingRateRequest;
import com.suntravels.dto.OfferingRequest;
import com.suntravels.dto.OfferingStatusRequest;
import com.suntravels.dto.SeasonalRateRequest;
import com.suntravels.entity.Contract;
import com.suntravels.entity.ContractRoomOffering;
import com.suntravels.entity.ContractStatus;
import com.suntravels.entity.Hotel;
import com.suntravels.entity.HotelStatus;
import com.suntravels.entity.OfferingSeasonalRate;
import com.suntravels.entity.OfferingStatus;
import com.suntravels.entity.RoomType;
import com.suntravels.exception.OfferingException;
import com.suntravels.repository.ContractRepository;
import com.suntravels.repository.OfferingRepository;
import com.suntravels.repository.RoomTypeRepository;
import com.suntravels.repository.SeasonalRateRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class OfferingServiceTest
{
    final LocalDate from = LocalDate.of( 2026, 1, 1 ), to = LocalDate.of( 2026, 12, 31 );
    @Mock
    ContractRepository contracts;
    @Mock
    OfferingRepository offerings;
    @Mock
    RoomTypeRepository rooms;
    @Mock
    SeasonalRateRepository seasons;
    ValidatorFactory factory;
    OfferingService service;
    Contract c;
    ContractRoomOffering o;
    RoomType room;

    // Configures the component and its test dependencies.
    @BeforeEach
    void setup()
    {
        factory = Validation.buildDefaultValidatorFactory();
        service = new OfferingService( contracts, offerings, rooms, seasons, factory.getValidator() );
        c = new Contract();
        ReflectionTestUtils.setField( c, "contractId", 1 );
        c.setStatus( ContractStatus.DRAFT );
        c.setValidFrom( from );
        c.setValidTo( to );
        c.setCurrencyCode( "USD" );
        room = new RoomType();
        ReflectionTestUtils.setField( room, "roomTypeId", 2 );
        room.setName( "Double" );
        o = new ContractRoomOffering();
        ReflectionTestUtils.setField( o, "offeringId", 3 );
        o.setContract( c );
        o.setRoomType( room );
        o.setRatePerPersonPerNight( new BigDecimal( "100" ) );
        o.setContractedRoomCount( 10 );
        o.setMaxAdults( 2 );
        o.setStatus( OfferingStatus.ENABLED );
    }

    // Releases resources created for the test.
    @AfterEach
    void close()
    {
        factory.close();
    }

    // Builds a valid request fixture for the tested operation.
    OfferingRequest request()
    {
        return new OfferingRequest( 2, new BigDecimal( "120.50" ), 20, 3, OfferingStatus.SUSPENDED );
    }

    // Creates a seasonal rate fixture for the selected offering.
    SeasonalRateRequest season( LocalDate a, LocalDate b )
    {
        return new SeasonalRateRequest( a, b, new BigDecimal( "150" ) );
    }

    // Configures mocks for the locked records needed by the test.
    void locked()
    {
        when( offerings.findContractId( 3 ) ).thenReturn( Optional.of( 1 ) );
        when( contracts.findForUpdate( 1 ) ).thenReturn( Optional.of( c ) );
        when( offerings.findForUpdate( 3 ) ).thenReturn( Optional.of( o ) );
    }

    // Configures the offering repository mock to return the saved entity.
    void saveOffering()
    {
        when( offerings.saveAndFlush( any() ) ).thenAnswer( i -> i.getArgument( 0 ) );
    }

    // Configures the seasonal-rate repository mock to return the saved entity.
    void saveSeason()
    {
        when( seasons.saveAndFlush( any() ) ).thenAnswer( i -> i.getArgument( 0 ) );
    }

    // Builds an existing seasonal-rate fixture for update tests.
    OfferingSeasonalRate existing()
    {
        var s = new OfferingSeasonalRate();
        ReflectionTestUtils.setField( s, "seasonalRateId", 4 );
        s.setOffering( o );
        s.setValidFrom( from );
        s.setValidTo( from.plusDays( 5 ) );
        s.setRatePerPersonPerNight( new BigDecimal( "150" ) );
        return s;
    }

    // Asserts that the operation returns the expected business error.
    void error( int status, Runnable work )
    {
        assertEquals( status, assertThrows( OfferingException.class, work::run ).getStatus() );
    }

    // Verifies directory includes every contract status and inactive hotels.
    @ParameterizedTest
    @EnumSource( ContractStatus.class )
    void directoryIncludesEveryContractStatusAndInactiveHotels( ContractStatus status )
    {
        Hotel h = new Hotel();
        ReflectionTestUtils.setField( h, "hotelId", 7 );
        h.setName( "Ocean Hotel" );
        h.setStatus( HotelStatus.INACTIVE );
        c.setHotel( h );
        c.setContractCode( "C001" );
        c.setStatus( status );
        o.setStatus( OfferingStatus.SUSPENDED );
        when( offerings.findDirectoryOfferings() ).thenReturn( List.of( o ) );
        when( seasons.countPeriodsByOffering() ).thenReturn( List.of( new SeasonalRateRepository.PeriodCount()
        {

            // Returns the fixture’s offering id.
            public Integer getOfferingId()
            {
                return 3;
            }

            // Returns the fixture’s period count.
            public long getPeriodCount()
            {
                return 2L;
            }
        } ) );
        var row = service.directory().get( 0 );
        assertEquals( "Ocean Hotel", row.hotelName() );
        assertEquals( HotelStatus.INACTIVE, row.hotelStatus() );
        assertEquals( "C001", row.contractCode() );
        assertEquals( status, row.contractStatus() );
        assertEquals( from, row.validFrom() );
        assertEquals( to, row.validTo() );
        assertEquals( "Double", row.roomTypeName() );
        assertEquals( new BigDecimal( "100" ), row.ratePerPersonPerNight() );
        assertEquals( "USD", row.currencyCode() );
        assertEquals( 10, row.contractedRoomCount() );
        assertEquals( 2, row.maxAdults() );
        assertEquals( 2L, row.seasonalRateCount() );
        assertEquals( OfferingStatus.SUSPENDED, row.status() );
        verify( offerings ).findDirectoryOfferings();
        verify( seasons ).countPeriodsByOffering();
        verifyNoInteractions( contracts, rooms );
        verify( offerings, never() ).saveAndFlush( any() );
        verify( seasons, never() ).findByOfferingOfferingIdOrderByValidFrom( any() );
    }

    // Verifies directory returns empty without counting seasons.
    @Test
    void directoryReturnsEmptyWithoutCountingSeasons()
    {
        when( offerings.findDirectoryOfferings() ).thenReturn( List.of() );
        assertTrue( service.directory().isEmpty() );
        verifyNoInteractions( seasons, contracts, rooms );
    }

    // Verifies directory defaults to zero periods.
    @Test
    void directoryDefaultsToZeroPeriods()
    {
        Hotel h = new Hotel();
        h.setName( "Hotel" );
        c.setHotel( h );
        when( offerings.findDirectoryOfferings() ).thenReturn( List.of( o ) );
        assertEquals( 0L, service.directory().get( 0 ).seasonalRateCount() );
    }

    // Verifies creates draft offering with real room type.
    @Test
    void createsDraftOfferingWithRealRoomType()
    {
        when( contracts.findForUpdate( 1 ) ).thenReturn( Optional.of( c ) );
        when( rooms.findById( 2 ) ).thenReturn( Optional.of( room ) );
        saveOffering();
        var r = service.create( 1, request() );
        assertEquals( 20, r.contractedRoomCount() );
        assertEquals( OfferingStatus.SUSPENDED, r.status() );
        assertEquals( 1, r.contractId() );
    }

    // Verifies duplicate create does not write.
    @Test
    void duplicateCreateDoesNotWrite()
    {
        when( contracts.findForUpdate( 1 ) ).thenReturn( Optional.of( c ) );
        when( offerings.existsByContractContractIdAndRoomTypeRoomTypeIdAndOfferingIdNot( 1, 2, 0 ) ).thenReturn(
                true );
        error( 409, () -> service.create( 1, request() ) );
        verify( offerings, never() ).saveAndFlush( any() );
    }

    // Verifies missing contract.
    @Test
    void missingContract()
    {
        error( 404, () -> service.create( 1, request() ) );
    }

    // Verifies missing room type.
    @Test
    void missingRoomType()
    {
        when( contracts.findForUpdate( 1 ) ).thenReturn( Optional.of( c ) );
        error( 404, () -> service.create( 1, request() ) );
        verify( offerings, never() ).saveAndFlush( any() );
    }

    // Verifies missing offering.
    @Test
    void missingOffering()
    {
        error( 404, () -> service.get( 3 ) );
        error( 404, () -> service.rate( 3, new OfferingRateRequest( BigDecimal.ONE ) ) );
        error( 404, () -> service.seasonalRates( 3 ) );
    }

    // Verifies invalid base rates.
    @ParameterizedTest
    @ValueSource( strings = { "0", "-0.01", "100000000", "1.001" } )
    void invalidBaseRates( String value )
    {
        when( contracts.findForUpdate( 1 ) ).thenReturn( Optional.of( c ) );
        error( 400, () -> service.create( 1,
                new OfferingRequest( 2, new BigDecimal( value ), 1, 1, OfferingStatus.ENABLED ) ) );
        verifyNoInteractions( rooms );
        verify( offerings, never() ).saveAndFlush( any() );
    }

    // Verifies invalid counts and null fields.
    @Test
    void invalidCountsAndNullFields()
    {
        when( contracts.findForUpdate( 1 ) ).thenReturn( Optional.of( c ) );
        for( var r : List.of( new OfferingRequest( 2, BigDecimal.ONE, 0, 1, OfferingStatus.ENABLED ),
                new OfferingRequest( 2, BigDecimal.ONE, 1, -1, OfferingStatus.ENABLED ),
                new OfferingRequest( null, null, null, null, null ) ) )
            error( 400, () -> service.create( 1, r ) );
    }

    // Verifies updates draft without changing contract.
    @Test
    void updatesDraftWithoutChangingContract()
    {
        locked();
        when( rooms.findById( 2 ) ).thenReturn( Optional.of( room ) );
        saveOffering();
        service.update( 3, request() );
        assertSame( c, o.getContract() );
        assertEquals( 20, o.getContractedRoomCount() );
        assertEquals( 3, o.getMaxAdults() );
    }

    // Verifies duplicate update preserves all existing values.
    @Test
    void duplicateUpdatePreservesAllExistingValues()
    {
        locked();
        when( offerings.existsByContractContractIdAndRoomTypeRoomTypeIdAndOfferingIdNot( 1, 2, 3 ) ).thenReturn(
                true );
        error( 409, () -> service.update( 3, request() ) );
        assertEquals( 10, o.getContractedRoomCount() );
        assertEquals( new BigDecimal( "100" ), o.getRatePerPersonPerNight() );
        verify( offerings, never() ).delete( any( ContractRoomOffering.class ) );
    }

    // Verifies structural edits and creation rejected.
    @ParameterizedTest
    @EnumSource( value = ContractStatus.class, names = { "APPROVED", "SUSPENDED", "TERMINATED" } )
    void structuralEditsAndCreationRejected( ContractStatus status )
    {
        c.setStatus( status );
        locked();
        error( 409, () -> service.update( 3, request() ) );
        error( 409, () -> service.create( 1, request() ) );
        assertEquals( 10, o.getContractedRoomCount() );
        verify( offerings, never() ).saveAndFlush( any() );
    }

    // Verifies approved rate changes only rate.
    @Test
    void approvedRateChangesOnlyRate()
    {
        c.setStatus( ContractStatus.APPROVED );
        locked();
        saveOffering();
        service.rate( 3, new OfferingRateRequest( new BigDecimal( "99999999.99" ) ) );
        assertSame( c, o.getContract() );
        assertSame( room, o.getRoomType() );
        assertEquals( 10, o.getContractedRoomCount() );
        assertEquals( 2, o.getMaxAdults() );
        assertEquals( OfferingStatus.ENABLED, o.getStatus() );
        verifyNoInteractions( rooms, seasons );
        verify( contracts, never() ).saveAndFlush( any() );
    }

    // Verifies rate maintenance requires approved.
    @ParameterizedTest
    @EnumSource( value = ContractStatus.class, names = { "DRAFT", "SUSPENDED", "TERMINATED" } )
    void rateMaintenanceRequiresApproved( ContractStatus status )
    {
        c.setStatus( status );
        locked();
        error( 409, () -> service.rate( 3, new OfferingRateRequest( BigDecimal.ONE ) ) );
    }

    // Verifies enables and suspends only status.
    @ParameterizedTest
    @EnumSource( value = ContractStatus.class, names = { "DRAFT", "APPROVED", "SUSPENDED" } )
    void enablesAndSuspendsOnlyStatus( ContractStatus status )
    {
        c.setStatus( status );
        locked();
        saveOffering();
        service.status( 3, new OfferingStatusRequest( OfferingStatus.SUSPENDED ) );
        assertEquals( OfferingStatus.SUSPENDED, o.getStatus() );
        service.status( 3, new OfferingStatusRequest( OfferingStatus.ENABLED ) );
        assertEquals( OfferingStatus.ENABLED, o.getStatus() );
        assertEquals( 10, o.getContractedRoomCount() );
    }

    // Verifies historical contracts still reject seasonal maintenance and terminated rejects status changes.
    @ParameterizedTest
    @EnumSource( value = ContractStatus.class, names = { "SUSPENDED", "TERMINATED" } )
    void historicalContractsStillRejectSeasonalMaintenanceAndTerminatedRejectsStatusChanges(
            ContractStatus status )
    {
        c.setStatus( status );
        locked();
        if( status == ContractStatus.TERMINATED )
            error( 409, () -> service.status( 3, new OfferingStatusRequest( OfferingStatus.ENABLED ) ) );
        error( 409, () -> service.createSeason( 3, season( from, to ) ) );
        error( 409, () -> service.updateSeason( 3, 4, season( from, to ) ) );
        verifyNoInteractions( seasons );
    }

    // Verifies creates season with inclusive contract bounds.
    @ParameterizedTest
    @EnumSource( value = ContractStatus.class, names = { "DRAFT", "APPROVED" } )
    void createsSeasonWithInclusiveContractBounds( ContractStatus status )
    {
        c.setStatus( status );
        locked();
        saveSeason();
        var r = service.createSeason( 3, season( from, to ) );
        assertEquals( from, r.validFrom() );
        assertEquals( to, r.validTo() );
        var order = inOrder( offerings, contracts, seasons );
        order.verify( offerings ).findContractId( 3 );
        order.verify( contracts ).findForUpdate( 1 );
        order.verify( offerings ).findForUpdate( 3 );
        order.verify( seasons ).overlaps( 3, 0, from, to );
        order.verify( seasons ).saveAndFlush( any() );
    }

    // Verifies multiple consecutive periods and single night allowed.
    @Test
    void multipleConsecutivePeriodsAndSingleNightAllowed()
    {
        locked();
        saveSeason();
        service.createSeason( 3, season( from, from.plusDays( 29 ) ) );
        service.createSeason( 3, season( from.plusDays( 30 ), from.plusDays( 60 ) ) );
        service.createSeason( 3, season( to, to ) );
        verify( seasons, times( 3 ) ).saveAndFlush( any() );
    }

    // Verifies overlaps are scoped to offering.
    @Test
    void overlapsAreScopedToOffering()
    {
        locked();
        saveSeason();
        service.createSeason( 3, season( from, to ) );
        verify( seasons ).overlaps( 3, 0, from, to );
        verify( seasons, never() ).findAll();
    }

    // Verifies shared inclusive boundary conflicts.
    @ParameterizedTest
    @ValueSource( ints = { 0, 5 } )
    void sharedInclusiveBoundaryConflicts( int day )
    {
        locked();
        var date = from.plusDays( day );
        when( seasons.overlaps( 3, 0, date, date ) ).thenReturn( List.of( existing() ) );
        error( 409, () -> service.createSeason( 3, season( date, date ) ) );
        verify( seasons, never() ).saveAndFlush( any() );
    }

    // Verifies rejects outside and reversed dates.
    @Test
    void rejectsOutsideAndReversedDates()
    {
        locked();
        error( 400, () -> service.createSeason( 3, season( from.minusDays( 1 ), to ) ) );
        error( 400, () -> service.createSeason( 3, season( from, to.plusDays( 1 ) ) ) );
        error( 400, () -> service.createSeason( 3, season( to, from ) ) );
        verifyNoInteractions( seasons );
    }

    // Verifies invalid seasonal rates.
    @ParameterizedTest
    @ValueSource( strings = { "0", "-1", "100000000", "0.001" } )
    void invalidSeasonalRates( String value )
    {
        locked();
        error( 400, () -> service.createSeason( 3, new SeasonalRateRequest( from, to, new BigDecimal( value ) ) ) );
        verifyNoInteractions( seasons );
    }

    // Verifies update excludes current record and keeps association.
    @Test
    void updateExcludesCurrentRecordAndKeepsAssociation()
    {
        locked();
        var s = existing();
        when( seasons.findBySeasonalRateIdAndOfferingOfferingId( 4, 3 ) ).thenReturn( Optional.of( s ) );
        saveSeason();
        service.updateSeason( 3, 4, season( from, to ) );
        verify( seasons ).overlaps( 3, 4, from, to );
        assertSame( o, s.getOffering() );
        assertEquals( to, s.getValidTo() );
    }

    // Verifies conflicting update preserves period and rate.
    @Test
    void conflictingUpdatePreservesPeriodAndRate()
    {
        locked();
        var s = existing();
        when( seasons.findBySeasonalRateIdAndOfferingOfferingId( 4, 3 ) ).thenReturn( Optional.of( s ) );
        when( seasons.overlaps( 3, 4, from, to ) ).thenReturn( List.of( existing() ) );
        error( 409, () -> service.updateSeason( 3, 4, season( from, to ) ) );
        assertEquals( from.plusDays( 5 ), s.getValidTo() );
        assertEquals( new BigDecimal( "150" ), s.getRatePerPersonPerNight() );
        verify( seasons, never() ).saveAndFlush( any() );
    }

    // Verifies seasonal rate cannot be moved between offerings.
    @Test
    void seasonalRateCannotBeMovedBetweenOfferings()
    {
        locked();
        error( 404, () -> service.updateSeason( 3, 99, season( from, to ) ) );
        verify( seasons, never() ).saveAndFlush( any() );
    }

    // Verifies nightly resolver uses inclusive season then base and does not write snapshots.
    @Test
    void nightlyResolverUsesInclusiveSeasonThenBaseAndDoesNotWriteSnapshots()
    {
        when( offerings.findById( 3 ) ).thenReturn( Optional.of( o ) );
        when( seasons.findByOfferingOfferingIdOrderByValidFrom( 3 ) ).thenReturn( List.of( existing() ) );
        assertEquals( new BigDecimal( "150" ), service.effectiveRate( 3, from ) );
        assertEquals( new BigDecimal( "150" ), service.effectiveRate( 3, from.plusDays( 5 ) ) );
        assertEquals( new BigDecimal( "100" ), service.effectiveRate( 3, from.plusDays( 6 ) ) );
        verify( offerings, never() ).saveAndFlush( any() );
        verify( seasons, never() ).saveAndFlush( any() );
        verifyNoInteractions( contracts, rooms );
    }

    // Verifies corrupt persisted overlaps fail closed.
    @Test
    void corruptPersistedOverlapsFailClosed()
    {
        when( offerings.findById( 3 ) ).thenReturn( Optional.of( o ) );
        when( seasons.findByOfferingOfferingIdOrderByValidFrom( 3 ) ).thenReturn( List.of( existing(), existing() ) );
        error( 409, () -> service.effectiveRate( 3, from ) );
    }

    // Verifies historical offerings and periods are readable.
    @Test
    void historicalOfferingsAndPeriodsAreReadable()
    {
        c.setStatus( ContractStatus.TERMINATED );
        when( contracts.findById( 1 ) ).thenReturn( Optional.of( c ) );
        when( offerings.findByContractContractIdOrderByOfferingId( 1 ) ).thenReturn( List.of( o ) );
        when( offerings.findById( 3 ) ).thenReturn( Optional.of( o ) );
        when( seasons.findByOfferingOfferingIdOrderByValidFrom( 3 ) ).thenReturn( List.of( existing() ) );
        assertEquals( 1, service.list( 1 ).size() );
        assertEquals( 1, service.seasonalRates( 3 ).size() );
    }

}
