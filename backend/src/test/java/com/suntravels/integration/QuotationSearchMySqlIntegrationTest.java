package com.suntravels.integration;

import com.suntravels.dto.QuotationSearchRequest;
import com.suntravels.dto.QuotationSearchRequest.RequestedRoom;
import com.suntravels.dto.QuotationSelectionRequest;
import com.suntravels.dto.ReservationRequest;
import com.suntravels.entity.Contract;
import com.suntravels.entity.ContractRoomOffering;
import com.suntravels.entity.ContractStatus;
import com.suntravels.entity.Hotel;
import com.suntravels.entity.HotelStatus;
import com.suntravels.entity.OfferingSeasonalRate;
import com.suntravels.entity.OfferingStatus;
import com.suntravels.entity.RoomType;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.ContractRepository;
import com.suntravels.repository.HotelRepository;
import com.suntravels.repository.OfferingRepository;
import com.suntravels.repository.RoomTypeRepository;
import com.suntravels.repository.SeasonalRateRepository;
import com.suntravels.service.QuotationSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit opt-in AND _test database suffix. Fixtures rollback; never use operational data.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable( named = "SUNTRAVELS_SEARCH_MYSQL_TESTS", matches = "true" )
class QuotationSearchMySqlIntegrationTest
{
    @Autowired
    QuotationSearchService search;
    @Autowired
    HotelRepository hotels;
    @Autowired
    RoomTypeRepository types;
    @Autowired
    ContractRepository contracts;
    @Autowired
    OfferingRepository offerings;
    @Autowired
    SeasonalRateRepository seasons;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;
    Hotel hotel;
    Contract contract;
    ContractRoomOffering offer;
    RoomType type;
    LocalDate in, out;
    String city;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void offeringRoomTypeDetailsAreFetchedInOneQuery()
    {
        for (int i=0; i<2; i++) {
            RoomType extra = new RoomType();
            extra.setName("Offering room fixture " + UUID.randomUUID());
            extra = types.saveAndFlush(extra);
            newOffering(contract, extra, 3, 2, "100");
        }
        int id = contract.getContractId();
        entityManager.flush();
        entityManager.clear();
        long before = Long.parseLong((String)jdbc.queryForMap("show session status like 'Com_select'").get("Value"));
        var rows = offerings.findByContractContractIdOrderByOfferingId(id);
        assertEquals(3, rows.size());
        rows.forEach(row -> assertTrue(!row.getRoomType().getName().isBlank()));
        long after = Long.parseLong((String)jdbc.queryForMap("show session status like 'Com_select'").get("Value"));
        assertEquals(1, after-before);
    }

    @Test
    void indexedQueriesPreservePaddedCaseInsensitiveLocationsAndCandidateIdentity()
    {
        hotel.setCity( " " + city + " " );
        hotel.setCountry( " sri lanka " );
        hotels.saveAndFlush( hotel );
        var matching = new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 2 ) ),
                city.toUpperCase( Locale.ROOT ), null, type.getRoomTypeId(), "LK" );
        var hotelOnly = new QuotationSearchRequest( in, out, matching.rooms(), null, hotel.getHotelId(), null );
        var wrongCountry = new QuotationSearchRequest( in, out, matching.rooms(), city, null, null, "US" );
        var namedJdbc = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate( jdbc );
        for( var r : List.of( matching, hotelOnly, wrongCountry ) )
        {
            for( Integer cursor : Arrays.asList( null, hotel.getHotelId() ) )
            {
                String sql = """
                        select distinct h.hotel_id from hotel h join contract c on c.hotel_id=h.hotel_id
                        join contract_room_offering o on o.contract_id=c.contract_id
                        where h.status='ACTIVE' and c.status='APPROVED' and o.status='ENABLED'
                        and c.valid_from<=:in and c.valid_to>=:last and o.contracted_room_count>0
                        and h.hotel_id>:after
                        """;
                var params = new java.util.HashMap<String,Object>();
                params.put( "in", in ); params.put( "last", out.minusDays( 1 ) );
                params.put( "after", cursor == null ? 0 : cursor );
                if( r.city() != null ) { sql += " and lower(trim(h.city))=lower(:city)"; params.put( "city", r.city() ); }
                if( r.country() != null ) { sql += " and lower(trim(h.country))=lower(:country)";
                    params.put( "country", com.suntravels.validation.CountryCatalog.filterName( r.country() ) ); }
                if( r.hotelId() != null ) { sql += " and h.hotel_id=:hotel"; params.put( "hotel", r.hotelId() ); }
                if( r.roomTypeId() != null ) { sql += " and o.room_type_id=:room"; params.put( "room", r.roomTypeId() ); }
                var oldIds = namedJdbc.queryForList( sql + " order by h.hotel_id limit 21", params, Integer.class );
                var page = search.candidatePage( r, cursor );
                assertEquals( oldIds.subList( 0, Math.min( 20, oldIds.size() ) ), page.hotelIds() );
            }
        }
        var suggestions = search.citySuggestions( city.substring( 0, 8 ).toLowerCase( Locale.ROOT ), "LK" );
        assertTrue( suggestions.stream().anyMatch( c -> c.city().equals( city ) && c.country().equals( "sri lanka" ) ) );
        assertEquals( 1, search.evaluatedPage( matching, null ).hotels().size() );
        hotel.setCountry( "   " );
        hotels.saveAndFlush( hotel );
        assertEquals( null, search.hotelOption( hotel.getHotelId() ).country() );
    }

    @Test
    void citySuggestionsAreDistinctCountryAwareActiveAndLimited()
    {
        String prefix = "Location-" + UUID.randomUUID();
        for( int i = 0; i < 19; i++ )
        {
            Hotel h = new Hotel();
            h.setName( prefix + " Hotel" );
            h.setAddress( "Fixture street " + i );
            h.setCity( i < 3 ? " " + prefix + " A " : prefix + " B" + String.format( "%02d", i ) );
            h.setCountry( i == 1 ? "United States" : "Sri Lanka" );
            h.setStatus( HotelStatus.ACTIVE );
            hotels.saveAndFlush( h );
        }
        Hotel inactive = new Hotel();
        inactive.setName( prefix + " Inactive" );
        inactive.setCity( prefix + " 0" );
        inactive.setCountry( "Sri Lanka" );
        inactive.setStatus( HotelStatus.INACTIVE );
        hotels.saveAndFlush( inactive );
        var suggestions = search.citySuggestions( " " + prefix.toLowerCase( Locale.ROOT ) + " ", null );
        assertEquals( 15, suggestions.size() );
        assertEquals( prefix + " A", suggestions.get( 0 ).city() );
        assertEquals( "Sri Lanka", suggestions.get( 0 ).country() );
        assertEquals( prefix + " A", suggestions.get( 1 ).city() );
        assertEquals( "United States", suggestions.get( 1 ).country() );
        assertTrue( suggestions.stream().noneMatch( c -> c.city().endsWith( " 0" ) ) );
        assertEquals( suggestions, search.citySuggestions( prefix, null ) );
        assertEquals( 1, search.citySuggestions( prefix, "US" ).size() );
        assertTrue( search.citySuggestions( prefix, "LK" ).stream().allMatch( c -> c.country().equals( "Sri Lanka" ) ) );
        assertEquals( 2, search.hotelSuggestions( prefix, prefix + " A", "LK" ).size() );
        assertEquals( 1, search.hotelSuggestions( prefix, " " + prefix + " A ", "US" ).size() );
        assertTrue( search.hotelSuggestions( prefix, "Unknown city", null ).isEmpty() );
        assertTrue( search.citySuggestions( prefix + "%", null ).isEmpty() );
        Hotel historical = new Hotel();
        historical.setName( prefix + " Historical hotel" );
        historical.setCity( prefix + " Historical city" );
        historical.setCountry( "Historical country name" );
        historical.setStatus( HotelStatus.ACTIVE );
        hotels.saveAndFlush( historical );
        assertEquals( 1, search.citySuggestions( prefix, "Historical country name" ).size() );
        assertEquals( 1, search.hotelSuggestions( prefix, historical.getCity(), "Historical country name" ).size() );
    }

    @Test
    void countryFiltersBothCandidateHotelsAndTheirOfferingsWithoutChangingLegacySearch()
    {
        hotel.setCountry( "Sri Lanka" );
        hotels.saveAndFlush( hotel );
        int sriLankaHotel = hotel.getHotelId();
        hotel = new Hotel();
        hotel.setName( "Same city in another country" );
        hotel.setCity( city );
        hotel.setCountry( "United States" );
        hotel.setStatus( HotelStatus.ACTIVE );
        hotel = hotels.saveAndFlush( hotel );
        var c = newContract( ContractStatus.APPROVED, in, out );
        newOffering( c, type, 3, 2, "100" );
        var r = new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 2 ) ), city, null, null, "lk" );
        var page = search.evaluatedPage( r, null ).availableOnly();
        assertEquals( 1, page.hotels().size() );
        assertEquals( sriLankaHotel, page.hotels().get( 0 ).hotelId() );
        assertEquals( 1, search.search( r ).quotations().size() );
        var legacy = new QuotationSearchRequest( in, out, r.rooms(), city, null, null );
        assertEquals( 2, search.evaluatedPage( legacy, null ).hotels().size() );
        var hotelOnly = new QuotationSearchRequest( in, out, r.rooms(), null, hotel.getHotelId(), null );
        assertEquals( 1, search.evaluatedPage( hotelOnly, null ).hotels().size() );
    }

    @Test
    void hotelAutocompleteIsActiveBoundedOrderedAndResolvesOutsideThePage()
    {
        String prefix = "Auto-" + UUID.randomUUID();
        var expected = new java.util.ArrayList<Integer>();
        for( int i = 0; i < 22; i++ )
        {
            Hotel h = new Hotel();
            h.setName( prefix + " Same name" );
            h.setAddress( "Fixture street " + i );
            h.setCity( city );
            h.setStatus( HotelStatus.ACTIVE );
            expected.add( hotels.saveAndFlush( h ).getHotelId() );
        }
        Hotel inactive = new Hotel();
        inactive.setName( prefix + " A comes first" );
        inactive.setCity( city );
        inactive.setStatus( HotelStatus.INACTIVE );
        inactive = hotels.saveAndFlush( inactive );
        var matches = search.hotelSuggestions( "  " + prefix + "  " );
        assertEquals( expected.subList( 0, 20 ), matches.stream().map( h -> h.hotelId() ).toList() );
        assertTrue( matches.stream().allMatch( h -> city.equals( h.city() ) ) );
        assertEquals( expected.get( 21 ), search.hotelOption( expected.get( 21 ) ).hotelId() );
        int inactiveId = inactive.getHotelId();
        assertEquals( 404, assertThrows( QuotationSearchException.class, () -> search.hotelOption( inactiveId ) ).getStatus() );
        assertTrue( search.hotelSuggestions( "   " ).isEmpty() );
        assertTrue( search.hotelSuggestions( prefix + " missing" ).isEmpty() );
        // SQL LIKE wildcards in user text must remain literal.
        assertTrue( search.hotelSuggestions( prefix + "%" ).isEmpty() );
        Hotel first = hotels.findById( expected.get( 21 ) ).orElseThrow();
        first.setName( prefix + " A" );
        hotels.saveAndFlush( first );
        assertEquals( first.getHotelId(), search.hotelSuggestions( prefix ).get( 0 ).hotelId() );
    }

    @Test
    void cityFilterIsAppliedBeforeCandidateLimit()
    {
        hotel.setCity( "Different city" );
        hotels.saveAndFlush( hotel );
        // More than a full page of eligible hotels with lower IDs must not hide the destination.
        for( int i = 0; i <= 20; i++ )
        {
            hotel = new Hotel();
            hotel.setName( "Destination fixture " + i );
            hotel.setCity( i == 20 ? city : "Different city" );
            hotel.setStatus( HotelStatus.ACTIVE );
            hotel = hotels.saveAndFlush( hotel );
            var c = newContract( ContractStatus.APPROVED, in, out.minusDays( 1 ) );
            newOffering( c, type, 3, 2, "100" );
        }
        for( Integer hotelId : Arrays.asList( null, hotel.getHotelId() ) )
        {
            var r = new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 2 ) ),
                    "  " + city.toUpperCase( Locale.ROOT ) + "  ", hotelId, null );
            var page = search.evaluatedPage( r, null ).availableOnly();
            assertEquals( 1, page.hotels().size() );
            assertEquals( hotel.getHotelId(), page.hotels().get( 0 ).hotelId() );
            assertEquals( null, page.nextAfterHotelId() );
        }
        var hotelOnly = new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 2 ) ),
                "   ", hotel.getHotelId(), null );
        assertEquals( 1, search.evaluatedPage( hotelOnly, null ).hotels().size() );
    }

    @Test
    void availableOnlyCursorCrossesTwentyUnavailableHotels()
    {
        booking( offer, "CONFIRMED", 0, 3, 3 );
        for( int i = 1; i <= 20; i++ )
        {
            hotel = new Hotel();
            hotel.setName( "Page hotel " + i );
            hotel.setCity( city );
            hotel.setStatus( HotelStatus.ACTIVE );
            hotel = hotels.saveAndFlush( hotel );
            var c = newContract( ContractStatus.APPROVED, in, out.minusDays( 1 ) );
            var o = newOffering( c, type, 3, 2, "100" );
            if( i < 20 ) booking( o, "CONFIRMED", 0, 3, 3 );
        }
        var r = new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 2 ) ), city, null, null );
        var first = search.evaluatedPage( r, null ).availableOnly();
        assertTrue( first.hotels().isEmpty() );
        assertTrue( first.nextAfterHotelId() != null );
        var next = search.evaluatedPage( r, first.nextAfterHotelId() ).availableOnly();
        assertEquals( 1, next.hotels().size() );
        assertEquals( hotel.getHotelId(), next.hotels().get( 0 ).hotelId() );
        assertEquals( null, next.nextAfterHotelId() );
        assertEquals( new BigDecimal( "690.00" ), next.hotels().get( 0 ).offerings().get( 0 ).quotation().totalPrice() );
    }

    @ParameterizedTest
    @ValueSource( ints = { 20, 21 } )
    void candidatePagesContainTwentyCompleteHotelsWithoutDuplicates( int hotelCount )
    {
        var firstHotel = hotel;
        for( int i = 1; i < hotelCount; i++ )
        {
            hotel = new Hotel();
            hotel.setName( "Same name" );
            hotel.setAddress( "Fixture street " + i );
            hotel.setCity( city );
            hotel.setStatus( HotelStatus.ACTIVE );
            hotel = hotels.saveAndFlush( hotel );
            var c = newContract( ContractStatus.APPROVED, in, out.minusDays( 1 ) );
            newOffering( c, type, 3, 2, "100" );
        }
        // Multiple offerings on one hotel must not consume hotel-page slots.
        RoomType extra = new RoomType();
        extra.setName( "Extra " + UUID.randomUUID() );
        extra = types.saveAndFlush( extra );
        var secondOffering = newOffering( contract, extra, 2, 2, "120" );
        var request = new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 2 ) ), city, null, null );
        var first = search.candidatePage( request, null );
        assertEquals( 20, first.hotelIds().size() );
        assertEquals( 21, first.offerings().size() );
        assertTrue( first.offerings().stream().anyMatch( c -> c.offeringId() == secondOffering.getOfferingId() ) );
        assertEquals( firstHotel.getHotelId(), first.hotelIds().get( 0 ) );
        var evaluated = search.evaluatedPage( request, null );
        assertEquals( first.hotelIds(), evaluated.hotels().stream().map( h -> h.hotelId() ).toList() );
        assertEquals( first.nextAfterHotelId(), evaluated.nextAfterHotelId() );
        assertEquals( 2, evaluated.hotels().get( 0 ).offerings().size() );
        assertTrue( evaluated.hotels().stream().flatMap( h -> h.offerings().stream() )
                             .allMatch( o -> o.quotation() != null && o.quotation().rooms().size() == 1 ) );
        if( hotelCount == 20 )
        {
            assertEquals( null, first.nextAfterHotelId() );
            assertTrue( search.candidatePage( request, first.hotelIds().get( 19 ) ).hotelIds().isEmpty() );
            return;
        }
        assertEquals( first.hotelIds().get( 19 ), first.nextAfterHotelId() );
        var second = search.candidatePage( request, first.nextAfterHotelId() );
        assertEquals( 1, second.hotelIds().size() );
        var evaluatedNext = search.evaluatedPage( request, first.nextAfterHotelId() );
        assertEquals( second.hotelIds().get( 0 ), evaluatedNext.hotels().get( 0 ).hotelId() );
        assertEquals( new BigDecimal( "690.00" ),
                evaluatedNext.hotels().get( 0 ).offerings().get( 0 ).quotation().totalPrice() );
        assertTrue( second.hotelIds().stream().noneMatch( first.hotelIds()::contains ) );
        assertEquals( null, second.nextAfterHotelId() );
        assertTrue( search.candidatePage( request, second.hotelIds().get( 0 ) ).hotelIds().isEmpty() );
        assertTrue( search.evaluatedPage( request, second.hotelIds().get( 0 ) ).hotels().isEmpty() );
    }

    @Test
    void evaluatedHotelRetainsIndependentOfferingResultsAndSeasonalPrices()
    {
        RoomType other = new RoomType();
        other.setName( "Single " + UUID.randomUUID() );
        other = types.saveAndFlush( other );
        var single = newOffering( contract, other, 3, 1, "50" );
        season( in.plusDays( 1 ), in.plusDays( 1 ), "150" );
        // Reservations touching either stay boundary do not consume occupied nights.
        booking( offer, "CONFIRMED", -2, 0, 3 );
        booking( offer, "CONFIRMED", 3, 5, 3 );
        booking( offer, "CANCELLED", 0, 3, 3 );
        booking( offer, "CONFIRMED", 0, 2, 1 );
        var r = request( 2, 1 );
        var evaluated = search.evaluatedPage( r, null );
        assertEquals( 1, evaluated.hotels().size() );
        var options = evaluated.hotels().get( 0 ).offerings();
        assertEquals( 2, options.size() );
        var doubleResult = options.stream().filter( o -> o.offeringId() == offer.getOfferingId() ).findFirst().orElseThrow();
        var singleResult = options.stream().filter( o -> o.offeringId() == single.getOfferingId() ).findFirst().orElseThrow();
        assertEquals( new BigDecimal( "1207.50" ), doubleResult.quotation().totalPrice() );
        assertEquals( 2, doubleResult.roomChoices().availableRoomsForStay() );
        assertEquals( 2, doubleResult.quotation().rooms().get( 0 ).allocatedRoomsForOffering() );
        assertEquals( new BigDecimal( "345.00" ),
                doubleResult.quotation().rooms().get( 0 ).nightlyPrices().get( 1 ).sellingPrice() );
        assertEquals( search.search( r ).quotations().get( 0 ), doubleResult.quotation() );
        var availablePage = evaluated.availableOnly();
        assertEquals( 1, availablePage.hotels().size() );
        assertEquals( 1, availablePage.hotels().get( 0 ).offerings().size() );
        assertEquals( doubleResult.quotation(), availablePage.hotels().get( 0 ).offerings().get( 0 ).quotation() );
        assertEquals( com.suntravels.dto.QuotationSearchResponse.ReasonCode.MAX_ADULT_OCCUPANCY,
                singleResult.unavailableOption().reasons().get( 0 ).code() );
        assertEquals( null, singleResult.quotation() );
        // A second overlapping confirmed commitment reduces the minimum free quantity to one.
        booking( offer, "CONFIRMED", 1, 3, 1 );
        var unavailable = search.evaluatedPage( r, null ).hotels().get( 0 ).offerings().get( 0 );
        assertEquals( 1, unavailable.roomChoices().availableRoomsForStay() );
        assertEquals( new BigDecimal( "1207.50" ), unavailable.unavailableOption().indicativeTotalPrice() );
        assertEquals( com.suntravels.dto.QuotationSearchResponse.ReasonCode.INSUFFICIENT_INVENTORY,
                unavailable.unavailableOption().reasons().get( 0 ).code() );
        assertTrue( search.evaluatedPage( r, null ).availableOnly().hotels().isEmpty() );
    }

    @Test
    void candidatePageUsesFullStayAndOptionalFilters()
    {
        contract.setValidFrom( in );
        contract.setValidTo( out.minusDays( 1 ) );
        contracts.saveAndFlush( contract );
        assertEquals( 1, search.candidatePage( request( 2 ), null ).offerings().size() );
        var filtered = new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 2 ) ),
                "  " + city.toUpperCase( Locale.ROOT ) + "  ", hotel.getHotelId(), type.getRoomTypeId() );
        assertEquals( 1, search.candidatePage( filtered, null ).offerings().size() );
        contract.setValidTo( out.minusDays( 2 ) );
        contracts.saveAndFlush( contract );
        assertTrue( search.candidatePage( request( 2 ), null ).hotelIds().isEmpty() );
    }

    @Test
    void candidatePageExcludesIneligibleStatuses()
    {
        hotel.setStatus( HotelStatus.INACTIVE );
        hotels.saveAndFlush( hotel );
        assertTrue( search.candidatePage( request( 2 ), null ).hotelIds().isEmpty() );
        hotel.setStatus( HotelStatus.ACTIVE );
        hotels.saveAndFlush( hotel );
        contract.setStatus( ContractStatus.DRAFT );
        contracts.saveAndFlush( contract );
        assertTrue( search.candidatePage( request( 2 ), null ).hotelIds().isEmpty() );
        contract.setStatus( ContractStatus.APPROVED );
        contracts.saveAndFlush( contract );
        offer.setStatus( OfferingStatus.SUSPENDED );
        offerings.saveAndFlush( offer );
        assertTrue( search.candidatePage( request( 2 ), null ).hotelIds().isEmpty() );
    }

    // Creates isolated database fixtures for the test.
    @BeforeEach
    void seed()
    {
        assertTrue( jdbc.queryForObject( "select database()", String.class ).endsWith( "_test" ),
                "Use a disposable database whose name ends _test" );
        assertTrue( jdbc.queryForObject( "select version()", String.class ).startsWith( "8." ) );
        in = LocalDate.now( clock ).plusDays( 10 );
        out = in.plusDays( 3 );
        city = "SearchCity-" + UUID.randomUUID();
        hotel = new Hotel();
        hotel.setName( "Search fixture " + UUID.randomUUID() );
        hotel.setCity( city );
        hotel.setStatus( HotelStatus.ACTIVE );
        hotel = hotels.saveAndFlush( hotel );
        type = new RoomType();
        type.setName( "Search Double " + UUID.randomUUID() );
        type = types.saveAndFlush( type );
        contract = newContract( ContractStatus.APPROVED, in.minusDays( 3 ), out.plusDays( 30 ) );
        offer = newOffering( contract, type, 3, 2, "100" );
    }

    // Creates a contract fixture with the requested status and validity.
    Contract newContract( ContractStatus status, LocalDate from, LocalDate to )
    {
        Contract c = new Contract();
        c.setHotel( hotel );
        c.setContractCode( "S-" + UUID.randomUUID() );
        c.setStatus( status );
        c.setValidFrom( from );
        c.setValidTo( to );
        c.setCurrencyCode( "USD" );
        c.setMarkupPercentage( new BigDecimal( "15.00" ) );
        return contracts.saveAndFlush( c );
    }

    // Creates an offering fixture with the requested rate and inventory.
    ContractRoomOffering newOffering( Contract c, RoomType room, int count, int adults, String rate )
    {
        ContractRoomOffering o = new ContractRoomOffering();
        o.setContract( c );
        o.setRoomType( room );
        o.setStatus( OfferingStatus.ENABLED );
        o.setRatePerPersonPerNight( new BigDecimal( rate ) );
        o.setContractedRoomCount( count );
        o.setMaxAdults( adults );
        return offerings.saveAndFlush( o );
    }

    // Builds a valid request fixture for the tested operation.
    QuotationSearchRequest request( int... adults )
    {
        return new QuotationSearchRequest( in, out,
                Arrays.stream( adults ).mapToObj( RequestedRoom::new ).toList(), null, hotel.getHotelId(), null );
    }

    // Creates reservation commitments for inventory checks.
    Integer booking( ContractRoomOffering o, String status, int start, int end, int items )
    {
        jdbc.update( "insert into customer(name) values (?)", "Search customer " + UUID.randomUUID() );
        Integer customer = jdbc.queryForObject( "select last_insert_id()", Integer.class );
        jdbc.update(
                "insert into reservation(check_in_date,check_out_date,status,customer_id,currency_code) values (?,?,?,?,?)",
                in.plusDays( start ), in.plusDays( end ), status, customer, "USD" );
        Integer reservation = jdbc.queryForObject( "select last_insert_id()", Integer.class );
        for( int i = 0; i < items; i++ )
            jdbc.update(
                    "insert into reservation_item(number_of_adults,offering_id,reservation_id,booked_rate_per_person_per_night,booked_markup_percentage,booked_selling_price) values (?,?,?,?,?,?)",
                    2, o.getOfferingId(), reservation, new BigDecimal( "100" ), new BigDecimal( "15" ),
                    new BigDecimal( "230" ) );
        return reservation;
    }

    // Creates a seasonal rate fixture for the selected offering.
    void season( LocalDate from, LocalDate to, String rate )
    {
        OfferingSeasonalRate s = new OfferingSeasonalRate();
        s.setOffering( offer );
        s.setValidFrom( from );
        s.setValidTo( to );
        s.setRatePerPersonPerNight( new BigDecimal( rate ) );
        seasons.saveAndFlush( s );
    }

    // Verifies actual sql applies hotel contract and offering eligibility.
    @ParameterizedTest
    @CsvSource( { "ACTIVE,APPROVED,ENABLED,true", "INACTIVE,APPROVED,ENABLED,false",
            "ACTIVE,DRAFT,ENABLED,false", "ACTIVE,SUSPENDED,ENABLED,false", "ACTIVE,TERMINATED,ENABLED,false",
            "ACTIVE,APPROVED,SUSPENDED,false" } )
    void actualSqlAppliesHotelContractAndOfferingEligibility( String h, String c, String o, boolean match )
    {
        hotel.setStatus( HotelStatus.valueOf( h ) );
        hotels.saveAndFlush( hotel );
        contract.setStatus( ContractStatus.valueOf( c ) );
        contracts.saveAndFlush( contract );
        offer.setStatus( OfferingStatus.valueOf( o ) );
        offerings.saveAndFlush( offer );
        var result = search.search( request( 2 ) );
        assertEquals( match, !result.quotations().isEmpty() );
        assertTrue( result.unavailableOptions().isEmpty() );
    }

    // Verifies full stay contract validity is inclusive for nights and exclusive for checkout.
    @Test
    void fullStayContractValidityIsInclusiveForNightsAndExclusiveForCheckout()
    {
        contract.setValidTo( out.minusDays( 1 ) );
        contracts.saveAndFlush( contract );
        assertEquals( 1, search.search( request( 2 ) ).quotations().size() );
        contract.setValidTo( out.minusDays( 2 ) );
        contracts.saveAndFlush( contract );
        assertTrue( search.search( request( 2 ) ).quotations().isEmpty() );
        assertTrue( search.search( request( 2 ) ).unavailableOptions().isEmpty() );
    }

    // Verifies occupancy uses approved enabled full stay offerings and returns changed limits.
    @Test
    void occupancyUsesApprovedEnabledFullStayOfferingsAndReturnsChangedLimits()
    {
        assertEquals( 2,
                search.occupancy( hotel.getHotelId(), type.getRoomTypeId(), in, out, null ).get( 0 ).maxAdults() );
        offer.setMaxAdults( 4 );
        offerings.saveAndFlush( offer );
        assertEquals( 4,
                search.occupancy( hotel.getHotelId(), type.getRoomTypeId(), in, out, null ).get( 0 ).maxAdults() );
        contract.setValidTo( out.minusDays( 2 ) );
        contracts.saveAndFlush( contract );
        assertTrue( search.occupancy( hotel.getHotelId(), type.getRoomTypeId(), in, out, null ).isEmpty() );
        contract.setValidTo( out );
        contract.setStatus( ContractStatus.DRAFT );
        contracts.saveAndFlush( contract );
        assertTrue( search.occupancy( hotel.getHotelId(), type.getRoomTypeId(), in, out, null ).isEmpty() );
        contract.setStatus( ContractStatus.APPROVED );
        contracts.saveAndFlush( contract );
        offer.setStatus( OfferingStatus.SUSPENDED );
        offerings.saveAndFlush( offer );
        assertTrue( search.occupancy( hotel.getHotelId(), type.getRoomTypeId(), in, out, null ).isEmpty() );
    }

    // Verifies administrative exclusions never appear as unavailable inventory.
    @Test
    void administrativeExclusionsNeverAppearAsUnavailableInventory()
    {
        for( ContractStatus status : List.of( ContractStatus.DRAFT, ContractStatus.SUSPENDED,
                ContractStatus.TERMINATED ) )
        {
            contract.setStatus( status );
            contracts.saveAndFlush( contract );
            var result = search.search( request( 1 ) );
            assertTrue( result.quotations().isEmpty() );
            assertTrue( result.unavailableOptions().isEmpty() );
        }
        contract.setStatus( ContractStatus.APPROVED );
        contracts.saveAndFlush( contract );
        hotel.setStatus( HotelStatus.INACTIVE );
        hotels.saveAndFlush( hotel );
        assertTrue( search.search( request( 1 ) ).unavailableOptions().isEmpty() );
        hotel.setStatus( HotelStatus.ACTIVE );
        hotels.saveAndFlush( hotel );
        offer.setStatus( OfferingStatus.SUSPENDED );
        offerings.saveAndFlush( offer );
        assertTrue( search.search( request( 1 ) ).unavailableOptions().isEmpty() );
    }

    // Verifies non confirmed reservations do not consume inventory.
    @ParameterizedTest
    @ValueSource( strings = { "PENDING", "CANCELLED" } )
    void nonConfirmedReservationsDoNotConsumeInventory( String status )
    {
        booking( offer, status, 0, 3, 3 );
        assertEquals( 3, search.search( request( 1 ) ).quotations().get( 0 ).rooms().get( 0 ).availableRoomsForStay() );
    }

    // Verifies one item is one room and peak night is not sum across nights.
    @Test
    void oneItemIsOneRoomAndPeakNightIsNotSumAcrossNights()
    {
        booking( offer, "CONFIRMED", 0, 1, 1 );
        booking( offer, "CONFIRMED", 1, 3, 1 );
        assertEquals( 2,
                search.search( request( 2, 1 ) ).quotations().get( 0 ).rooms().get( 0 ).availableRoomsForStay() );
    }

    // Verifies multiple items and exclusive checkouts are counted per night.
    @Test
    void multipleItemsAndExclusiveCheckoutsAreCountedPerNight()
    {
        booking( offer, "CONFIRMED", -3, 0, 3 );
        booking( offer, "CONFIRMED", 3, 5, 3 );
        booking( offer, "CONFIRMED", 0, 3, 2 );
        assertEquals( 1, search.search( request( 2 ) ).quotations().get( 0 ).rooms().get( 0 ).availableRoomsForStay() );
        assertTrue( search.search( request( 2, 1 ) ).quotations().isEmpty() );
    }

    // Verifies fully booked and overbooked have different outcomes.
    @Test
    void fullyBookedAndOverbookedHaveDifferentOutcomes()
    {
        booking( offer, "CONFIRMED", 0, 3, 3 );
        assertTrue( search.search( request( 1 ) ).quotations().isEmpty() );
        booking( offer, "CONFIRMED", 1, 2, 1 );
        var requestForAssertion1 = request( 1 );
        assertEquals( 409, assertThrows( QuotationSearchException.class,
                () -> search.search( requestForAssertion1 ) ).getStatus() );
    }

    // Verifies unrelated offering consumption does not reduce requested room inventory.
    @Test
    void unrelatedOfferingConsumptionDoesNotReduceRequestedRoomInventory()
    {
        RoomType other = new RoomType();
        other.setName( "Other " + UUID.randomUUID() );
        other = types.saveAndFlush( other );
        ContractRoomOffering o = newOffering( contract, other, 3, 2, "150" );
        booking( o, "CONFIRMED", 0, 3, 3 );
        var result = search.search(
                new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 2 ) ), null, hotel.getHotelId(),
                        type.getRoomTypeId() ) );
        assertEquals( 3, result.quotations().get( 0 ).rooms().get( 0 ).availableRoomsForStay() );
    }

    // Verifies explicit mixed room selection uses actual capacity and seasonal pricing.
    @Test
    void explicitMixedRoomSelectionUsesActualCapacityAndSeasonalPricing()
    {
        offer.setContractedRoomCount( 1 );
        offerings.saveAndFlush( offer );
        RoomType other = new RoomType();
        other.setName( "Single " + UUID.randomUUID() );
        other = types.saveAndFlush( other );
        var single = newOffering( contract, other, 1, 1, "50" );
        season( in.plusDays( 1 ), in.plusDays( 1 ), "150" );
        var q = search.customize(
                new QuotationSelectionRequest( hotel.getHotelId(), contract.getContractId(), in, out,
                        List.of( new ReservationRequest.Room( offer.getOfferingId(), 2 ),
                                new ReservationRequest.Room( single.getOfferingId(), 1 ) ) ) );
        assertEquals( new BigDecimal( "977.50" ), q.totalPrice() );
        assertEquals( offer.getOfferingId(), q.rooms().get( 0 ).offeringId() );
        assertEquals( other.getRoomTypeId(), q.rooms().get( 1 ).roomTypeId() );
    }

    // Verifies city and reference filters use real existing records.
    @Test
    void cityAndReferenceFiltersUseRealExistingRecords()
    {
        assertEquals( 1, search.search( new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 1 ) ),
                " " + city.toLowerCase( Locale.ROOT ) + " ", hotel.getHotelId(),
                type.getRoomTypeId() ) ).quotations().size() );
        assertTrue( search.search(
                new QuotationSearchRequest( in, out, List.of( new RequestedRoom( 1 ) ), "No matching city",
                        hotel.getHotelId(), null ) ).quotations().isEmpty() );
    }

    // Verifies old contract commitments outside old validity cannot become extra inventory.
    @ParameterizedTest
    @EnumSource( value = ContractStatus.class, names = { "SUSPENDED", "TERMINATED" } )
    void oldContractCommitmentsOutsideOldValidityCannotBecomeExtraInventory( ContractStatus status )
    {
        Contract old = newContract( status, in.minusDays( 100 ), in.minusDays( 50 ) );
        ContractRoomOffering prior = newOffering( old, type, 3, 2, "100" );
        booking( prior, "CONFIRMED", 0, 3, 1 );
        var requestForAssertion2 = request( 1 );

        assertEquals( 409, assertThrows( QuotationSearchException.class,
                () -> search.search( requestForAssertion2 ) ).getStatus() );
    }

    // Verifies malformed or duplicate approval data fails closed.
    @Test
    void malformedOrDuplicateApprovalDataFailsClosed()
    {
        newContract( ContractStatus.APPROVED, in.minusDays( 1 ), out );
        var requestForAssertion3 = request( 1 );
        assertEquals( 409, assertThrows( QuotationSearchException.class,
                () -> search.search( requestForAssertion3 ) ).getStatus() );
    }

    // Verifies overlapping seasonal data is never arbitrarily selected.
    @Test
    void overlappingSeasonalDataIsNeverArbitrarilySelected()
    {
        season( in, in.plusDays( 1 ), "120" );
        season( in.plusDays( 1 ), out, "130" );
        var requestForAssertion4 = request( 1 );
        assertEquals( 409, assertThrows( QuotationSearchException.class,
                () -> search.search( requestForAssertion4 ) ).getStatus() );
    }

    // Verifies search does not mutate confirmed reservation or snapshot rows.
    @Test
    void searchDoesNotMutateConfirmedReservationOrSnapshotRows()
    {
        Integer reservation = booking( offer, "CONFIRMED", 0, 3, 1 );
        Integer item = jdbc.queryForObject(
                "select reservation_item_id from reservation_item where reservation_id=?", Integer.class,
                reservation );
        jdbc.update(
                "insert into reservation_item_nightly_rate(reservation_item_id,occupied_date,hotelier_rate,markup_percentage,selling_price) values (?,?,?,?,?)",
                item, in, new BigDecimal( "100" ), new BigDecimal( "15" ), new BigDecimal( "230" ) );
        var r = jdbc.queryForMap( "select * from reservation where reservation_id=?", reservation );
        var i = jdbc.queryForMap( "select * from reservation_item where reservation_item_id=?", item );
        var n = jdbc.queryForMap( "select * from reservation_item_nightly_rate where reservation_item_id=?",
                item );
        search.search( request( 2 ) );
        assertEquals( r, jdbc.queryForMap( "select * from reservation where reservation_id=?", reservation ) );
        assertEquals( i, jdbc.queryForMap( "select * from reservation_item where reservation_item_id=?", item ) );
        assertEquals( n,
                jdbc.queryForMap( "select * from reservation_item_nightly_rate where reservation_item_id=?",
                        item ) );
    }
}
