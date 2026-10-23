package com.suntravels.repository;

import com.suntravels.dto.QuotationSearchRequest;
import com.suntravels.dto.HotelSuggestion;
import com.suntravels.dto.CitySuggestion;
import com.suntravels.validation.CountryCatalog;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot SELECTs only. Deliberately separate from lifecycle FOR UPDATE guards.
 */
@Repository
public class QuotationSearchRepository
{
    private static final String COUNTRY = "country";
    public static final int HOTEL_PAGE_SIZE = 20;
    public static final int MAX_OFFERINGS = 500;
    public static final int MAX_SEASONS = 10000;
    public static final int MAX_BOOKED_ITEMS = 20000;
    private final NamedParameterJdbcTemplate jdbc;

    // Initializes the quotation search repository and its required dependencies.
    public QuotationSearchRepository( NamedParameterJdbcTemplate jdbc )
    {
        this.jdbc = jdbc;
    }

    // Escape LIKE metacharacters so the supplied text is a literal name prefix.
    public List<HotelSuggestion> hotelSuggestions( String term )
    {
        return hotelSuggestions( term, null, null );
    }

    private String prefix( String term )
    {
        return term.replace( "!", "!!" ).replace( "%", "!%" ).replace( "_", "!_" ) + "%";
    }

    public List<HotelSuggestion> hotelSuggestions( String term, String city, String country )
    {
        Map<String,Object> p = new HashMap<>();
        p.put( "prefix", prefix( term ) );
        String sql = """
                select hotel_id,name,search_city as city,search_country as country from hotel
                where status='ACTIVE' and name like :prefix escape '!'
                """;
        if( city != null && !city.isBlank() )
        {
            sql += " and search_city=:city";
            p.put( "city", city.trim() );
        }
        if( country != null )
        {
            sql += " and search_country=:country";
            p.put( COUNTRY, country );
        }
        return jdbc.query( sql + " order by name,hotel_id limit 20", p,
                ( r, n ) -> new HotelSuggestion( r.getInt( 1 ), r.getString( 2 ), r.getString( 3 ), r.getString( 4 ) ) );
    }

    public List<CitySuggestion> citySuggestions( String term, String country )
    {
        Map<String,Object> p = new HashMap<>();
        p.put( "prefix", prefix( term.toLowerCase( java.util.Locale.ROOT ) ) );
        String sql = """
                select distinct search_city as city,search_country as country from hotel
                where status='ACTIVE' and search_city like :prefix escape '!'
                """;
        if( country != null )
        {
            sql += " and search_country=:country";
            p.put( COUNTRY, country );
        }
        return jdbc.query( sql + " order by city,country limit 15", p,
                ( r, n ) -> new CitySuggestion( r.getString( 1 ), r.getString( 2 ) ) );
    }

    public Optional<HotelSuggestion> activeHotelOption( int id )
    {
        return jdbc.query( "select hotel_id,name,search_city,search_country from hotel where hotel_id=:id and status='ACTIVE'",
                Map.of( "id", id ),
                ( r, n ) -> new HotelSuggestion( r.getInt( 1 ), r.getString( 2 ), r.getString( 3 ), r.getString( 4 ) ) ).stream().findFirst();
    }

    // Loads active-hotel offerings with approved contracts covering the requested stay.
    public List<Candidate> candidates( QuotationSearchRequest request )
    {
        return candidates( request, null );
    }

    /** Select hotel IDs first, then load complete eligible offering groups. */
    public CandidatePage candidatePage( QuotationSearchRequest request, Integer afterHotelId )
    {
        Map<String,Object> p = new HashMap<>();
        p.put( "in", request.checkIn() );
        p.put( "last", request.checkOut().minusDays( 1 ) );
        p.put( "after", afterHotelId == null ? 0 : afterHotelId );
        String sql = """
                select h.hotel_id from hotel h
                where h.status='ACTIVE' and h.hotel_id>:after
                and (
                    select 1 from contract c join contract_room_offering o on o.contract_id=c.contract_id
                    where c.hotel_id=h.hotel_id and c.status='APPROVED' and o.status='ENABLED'
                     and c.valid_from<=:in and c.valid_to>=:last and o.contracted_room_count>0
                """;
        if( request.roomTypeId() != null )
        {
            sql += " and o.room_type_id=:room";
            p.put( "room", request.roomTypeId() );
        }
        // A scalar LIMIT 1 preserves the hotel-ID scan. EXISTS was transformed into
        // a semijoin that sorted/materialized broad destinations in measured plans.
        sql += " limit 1) is not null";
        if( request.hotelId() != null )
        {
            sql += " and h.hotel_id=:hotel";
            p.put( "hotel", request.hotelId() );
        }
        if( request.city() != null && !request.city().isBlank() )
        {
            sql += " and h.search_city=:city";
            p.put( "city", request.city().trim() );
        }
        // One extra ID detects continuation without counting every eligible hotel.
        if( request.country() != null && !request.country().isBlank() )
        {
            sql += " and h.search_country=:country";
            p.put( COUNTRY, CountryCatalog.filterName( request.country() ) );
        }
        List<Integer> found = jdbc.queryForList(
                sql + " order by h.hotel_id limit " + ( HOTEL_PAGE_SIZE + 1 ), p, Integer.class );
        List<Integer> ids = List.copyOf( found.subList( 0, Math.min( HOTEL_PAGE_SIZE, found.size() ) ) );
        Integer next = found.size() > HOTEL_PAGE_SIZE ? ids.get( ids.size() - 1 ) : null;
        return new CandidatePage( ids, ids.isEmpty() ? List.of() : candidates( request, ids ), next );
    }

    public record CandidatePage(List<Integer> hotelIds, List<Candidate> offerings, Integer nextAfterHotelId)
    {
        public CandidatePage
        {
            hotelIds = List.copyOf( hotelIds );
            offerings = List.copyOf( offerings );
        }
    }

    private List<Candidate> candidates( QuotationSearchRequest request, List<Integer> hotelIds )
    {
        // Only fixed SQL fragments and internal numeric bounds are concatenated below.
        // Request dates, IDs and city remain named parameters, never executable SQL text.
        Map<String,Object> p = new HashMap<>();
        p.put( "in", request.checkIn() );
        p.put( "last", request.checkOut().minusDays( 1 ) );
        String sql = """
                select o.offering_id,c.contract_id,h.hotel_id,h.name as hotel_name,h.city,c.contract_code,
                 c.currency_code,c.markup_percentage,c.valid_from,c.valid_to,rt.room_type_id,rt.name as room_type_name,
                 o.rate_per_person_per_night,o.contracted_room_count,o.max_adults
                from contract_room_offering o join contract c on c.contract_id=o.contract_id
                join hotel h on h.hotel_id=c.hotel_id join room_type rt on rt.room_type_id=o.room_type_id
                where h.status='ACTIVE' and c.status='APPROVED' and o.status='ENABLED'
                 and c.valid_from<=:in and c.valid_to>=:last and o.contracted_room_count>0
                """;
        if( request.hotelId() != null )
        {
            sql += " and h.hotel_id=:hotel";
            p.put( "hotel", request.hotelId() );
        }
        if( request.roomTypeId() != null )
        {
            sql += " and rt.room_type_id=:room";
            p.put( "room", request.roomTypeId() );
        }
        if( request.city() != null && !request.city().isBlank() )
        {
            sql += " and h.search_city=:city";
            p.put( "city", request.city().trim() );
        }
        if( hotelIds != null )
        {
            sql += " and h.hotel_id in (:hotels)";
            p.put( "hotels", hotelIds );
        }
        if( request.country() != null && !request.country().isBlank() )
        {
            sql += " and h.search_country=:country";
            p.put( COUNTRY, CountryCatalog.filterName( request.country() ) );
        }
        sql += " order by h.hotel_id,c.contract_id,o.offering_id";
        // Preserve the legacy guard, but never truncate a candidate hotel's offerings.
        if( hotelIds == null ) sql += " limit " + ( MAX_OFFERINGS + 1 );
        return jdbc.query( sql, p, ( r, n ) -> new Candidate( r.getInt( "offering_id" ), r.getInt( "contract_id" ),
                r.getInt( "hotel_id" ), r.getString( "hotel_name" ), r.getString( "city" ),
                r.getString( "contract_code" ), r.getString( "currency_code" ),
                r.getBigDecimal( "markup_percentage" ), r.getObject( "valid_from", LocalDate.class ),
                r.getObject( "valid_to", LocalDate.class ), r.getInt( "room_type_id" ),
                r.getString( "room_type_name" ), r.getBigDecimal( "rate_per_person_per_night" ),
                r.getInt( "contracted_room_count" ), r.getInt( "max_adults" ) ) );
    }

    // Loads the seasonal periods for all candidate offerings.
    public List<Season> seasons( Set<Integer> ids )
    {
        return jdbc.query(
                "select seasonal_rate_id,offering_id,valid_from,valid_to,rate_per_person_per_night from offering_seasonal_rate where offering_id in (:ids) order by offering_id,valid_from,seasonal_rate_id limit " +
                        ( MAX_SEASONS + 1 ), Map.of( "ids", ids ),
                ( r, n ) -> new Season( r.getInt( 1 ), r.getInt( 2 ), r.getObject( 3, LocalDate.class ),
                        r.getObject( 4, LocalDate.class ), r.getBigDecimal( 5 ) ) );
    }

    // Loads confirmed room commitments that affect the requested stay.
    public List<BookedRoom> bookedRooms( Set<Integer> ids, LocalDate in, LocalDate out )
    {
        return jdbc.query( """
                        select ri.reservation_item_id,ri.offering_id,r.check_in_date,r.check_out_date,ri.number_of_adults
                        from reservation_item ri join reservation r on r.reservation_id=ri.reservation_id
                        where ri.offering_id in (:ids) and r.status='CONFIRMED'
                         and ((r.check_in_date<:out and r.check_out_date>:in) or r.check_out_date<=r.check_in_date or ri.number_of_adults<=0)
                        order by ri.reservation_item_id
                        """ + " limit " + ( MAX_BOOKED_ITEMS + 1 ), Map.of( "ids", ids, "in", in, "out", out ),
                ( r, n ) -> new BookedRoom( r.getInt( 1 ), r.getInt( 2 ), r.getObject( 3, LocalDate.class ),
                        r.getObject( 4, LocalDate.class ), r.getInt( 5 ) ) );
    }

    // Detects conflicting approvals or invalid confirmed commitments before quoting.
    public boolean inconsistent(
            Set<Integer> offeringIds, Set<Integer> contractIds,
            LocalDate today )
    {
        // Enforce the existing approval invariant over entire candidate validity, not just the requested stay.
        if( !jdbc.queryForList( """
                select c.contract_id from contract c join contract other on other.hotel_id=c.hotel_id
                where c.contract_id in (:contracts) and other.contract_id<>c.contract_id and other.status='APPROVED'
                 and other.valid_from<=c.valid_to and other.valid_to>=c.valid_from limit 1
                """, Map.of( "contracts", contractIds ), Integer.class ).isEmpty() ) return true;
        return !jdbc.queryForList( """
                select ri.reservation_item_id from contract_room_offering candidate
                join contract cc on cc.contract_id=candidate.contract_id
                join contract old on old.hotel_id=cc.hotel_id and old.contract_id<>cc.contract_id
                join contract_room_offering old_offer on old_offer.contract_id=old.contract_id and old_offer.room_type_id=candidate.room_type_id
                join reservation_item ri on ri.offering_id=old_offer.offering_id
                join reservation r on r.reservation_id=ri.reservation_id
                where candidate.offering_id in (:ids) and r.status='CONFIRMED'
                 and ((r.check_out_date>:today and r.check_in_date<=cc.valid_to and r.check_out_date>cc.valid_from)
                   or r.check_out_date<=r.check_in_date or ri.number_of_adults<=0) limit 1
                """, Map.of( "ids", offeringIds, "today", today ), Integer.class ).isEmpty();
    }

    public record Candidate(
            int offeringId,
            int contractId,
            int hotelId,
            String hotelName,
            String city,
            String contractCode,
            String currency,
            BigDecimal markup,
            LocalDate validFrom,
            LocalDate validTo,
            int roomTypeId,
            String roomTypeName,
            BigDecimal baseRate,
            int contractedRooms,
            int maxAdults
    )
    {
    }

    public record Season(
            int id,
            int offeringId,
            LocalDate from,
            LocalDate to,
            BigDecimal rate
    )
    {
    }

    public record BookedRoom(
            int itemId,
            int offeringId,
            LocalDate checkIn,
            LocalDate checkOut,
            int adults
    )
    {
    }
}
