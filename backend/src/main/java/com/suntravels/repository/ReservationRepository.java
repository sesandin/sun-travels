package com.suntravels.repository;

import com.suntravels.dto.ReservationRequest;
import com.suntravels.dto.ReservationResponse;
import com.suntravels.dto.ReservationResponse.BookedNight;
import com.suntravels.dto.ReservationResponse.Item;
import com.suntravels.dto.ReservationResponse.OperationalWarning;
import com.suntravels.dto.ReservationResponse.Page;
import com.suntravels.dto.ReservationResponse.WarningCode;
import com.suntravels.exception.ReservationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class ReservationRepository
{
    public static final int PAGE_SIZE = 10;
    private static final String CHECK_OUT_DATE = "check_out_date";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String SUSPENDED = "SUSPENDED";
    private static final String STATUS_COLUMN = "status";
    private static final String HOTEL_ID_COLUMN = "hotel_id";
    private static final String CONTRACT_ID_COLUMN = "contract_id";
    private static final String HEADER = """
            select r.*,c.name,c.phone,c.email from reservation r join customer c on c.customer_id=r.customer_id
            """;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    // Initializes the reservation repository and its required dependencies.
    public ReservationRepository( JdbcTemplate jdbc, Clock clock )
    {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // Creates a not-found error for the requested record.
    public static ReservationException missing()
    {
        return new ReservationException( 404, "NOT_FOUND", "Reservation was not found" );
    }

    // Inserts a database row and returns its generated ID.
    public int insert( String sql, Object... values )
    {
        var key = new GeneratedKeyHolder();
        jdbc.update( connection ->
        {
            // JdbcTemplate owns this statement's lifecycle, including generated-key retrieval.
            var statement = connection.prepareStatement( sql, Statement.RETURN_GENERATED_KEYS );
            for( int i = 0; i < values.length; i++ )
            {
                statement.setObject( i + 1, values[i] );
            }
            return statement;
        }, key );
        return Objects.requireNonNull( key.getKey() ).intValue();
    }

    // Stores an occupied night’s agreed rate, markup, and selling price.
    public void night( int item, LocalDate date, BigDecimal rate, BigDecimal markup, BigDecimal selling )
    {
        jdbc.update(
                "insert into reservation_item_nightly_rate(reservation_item_id,occupied_date,hotelier_rate,markup_percentage,selling_price) values (?,?,?,?,?)",
                item, date, rate, markup, selling );
    }

    // Lists the distinct hotels used by a reservation in lock order.
    public List<Integer> hotelIds( int id )
    {
        return jdbc.queryForList(
                "select distinct c.hotel_id from reservation_item i join contract_room_offering o on o.offering_id=i.offering_id join contract c on c.contract_id=o.contract_id where i.reservation_id=? order by c.hotel_id",
                Integer.class, id );
    }

    // Locks a reservation and reads its current status.
    public String lockStatus( int id )
    {
        var rows = jdbc.queryForList( "select status from reservation where reservation_id=? for update",
                String.class, id );
        if( rows.isEmpty() )
        {
            throw missing();
        }
        return rows.get( 0 );
    }

    // Marks a confirmed reservation as cancelled without deleting its history.
    public void cancel( int id )
    {
        jdbc.update( "update reservation set status='CANCELLED' where reservation_id=? and status='CONFIRMED'",
                id );
    }

    // Loads reservation details, stored nightly prices, and current operational warnings.
    public ReservationResponse detail( int id )
    {
        var rows = jdbc.query( HEADER + " where r.reservation_id=?", ( r, n ) ->
        {
            var in = r.getObject( "check_in_date", LocalDate.class );
            var out = r.getObject( CHECK_OUT_DATE, LocalDate.class );
            var items = items( id, in, out );
            BigDecimal total =
                    items.isEmpty() || items.stream().anyMatch( i -> i.totalPrice() == null ) ? null :
                            items.stream().map( Item::totalPrice ).reduce( new BigDecimal( "0.00" ),
                                    BigDecimal::add );
            return new ReservationResponse( id, r.getInt( "customer_id" ), r.getString( "name" ),
                    r.getString( "phone" ), r.getString( "email" ), in, out, ChronoUnit.DAYS.between( in, out ),
                    r.getString( STATUS_COLUMN ), r.getObject( "created_at", LocalDateTime.class ),
                    r.getString( "currency_code" ), total, items,
                    CONFIRMED.equals( r.getString( STATUS_COLUMN ) ) && out.isAfter( LocalDate.now( clock ) ) ?
                            warnings( id ) : List.of() );
        }, id );
        if( rows.isEmpty() )
        {
            throw missing();
        }
        return rows.get( 0 );
    }

    // Current operational state only; never alters booked status, items or rate snapshots.
    // The caller restricts these warnings to confirmed stays whose exclusive checkout is still future.
    private List<OperationalWarning> warnings( int id )
    {
        return jdbc.query( """
                select distinct h.hotel_id,h.name,h.status,c.contract_id,c.status as contract_status,
                 o.offering_id,o.status as offering_status,t.name as room_type_name
                from reservation_item i join contract_room_offering o on o.offering_id=i.offering_id
                join contract c on c.contract_id=o.contract_id join hotel h on h.hotel_id=c.hotel_id
                join room_type t on t.room_type_id=o.room_type_id
                where i.reservation_id=? order by h.hotel_id,c.contract_id,o.offering_id
                """, ( r, n ) ->
        {
            List<OperationalWarning> result = new ArrayList<>();
            if( "INACTIVE".equals( r.getString( STATUS_COLUMN ) ) )
            {
                result.add( new OperationalWarning( r.getInt( HOTEL_ID_COLUMN ), r.getString( "name" ),
                        WarningCode.HOTEL_INACTIVE,
                        "This hotel is currently inactive. Please verify whether this reservation can still be honored." ) );
            }
            if( SUSPENDED.equals( r.getString( "contract_status" ) ) )
            {
                result.add( new OperationalWarning( r.getInt( HOTEL_ID_COLUMN ), r.getString( "name" ),
                        WarningCode.CONTRACT_SUSPENDED,
                        "This reservation is confirmed, but its hotel contract is currently suspended. Please verify the booking arrangements with the hotel.",
                        r.getInt( CONTRACT_ID_COLUMN ), null, null ) );
            }
            if( SUSPENDED.equals( r.getString( "offering_status" ) ) )
            {
                result.add( new OperationalWarning( r.getInt( HOTEL_ID_COLUMN ), r.getString( "name" ),
                        WarningCode.OFFERING_SUSPENDED,
                        "This reservation includes a room offering that has been suspended for new sales. Please verify existing booking arrangements.",
                        r.getInt( CONTRACT_ID_COLUMN ), r.getInt( "offering_id" ),
                        r.getString( "room_type_name" ) ) );
            }
            return result;
        }, id ).stream().flatMap( List::stream ).distinct().toList();
    }

    // Loads reservation rooms and derives totals only from complete nightly snapshots.
    private List<Item> items( int id, LocalDate in, LocalDate out )
    {
        return jdbc.query( """
                select i.*,h.hotel_id,h.name as hotel_name,c.contract_id,t.name as room_name
                from reservation_item i join contract_room_offering o on o.offering_id=i.offering_id
                join contract c on c.contract_id=o.contract_id join hotel h on h.hotel_id=c.hotel_id
                join room_type t on t.room_type_id=o.room_type_id where i.reservation_id=? order by i.reservation_item_id
                """, ( r, n ) ->
        {
            int item = r.getInt( "reservation_item_id" );
            var nights = jdbc.query(
                    "select occupied_date,selling_price,hotelier_rate,markup_percentage from reservation_item_nightly_rate where reservation_item_id=? order by occupied_date",
                    ( v, j ) -> new BookedNight( v.getObject( 1, LocalDate.class ), v.getBigDecimal( 2 ),
                            v.getBigDecimal( 3 ), v.getBigDecimal( 4 ) ), item );
            // Legacy aggregate semantics are not established; never guess a stay total from a single-night field.
            boolean complete = out.isAfter( in ) && nights.size() == ChronoUnit.DAYS.between( in, out ) &&
                                       nights.stream().allMatch(
                                               v -> !v.occupiedDate().isBefore( in ) && v.occupiedDate().isBefore( out ) );
            return new Item( item, r.getInt( "offering_id" ), r.getInt( HOTEL_ID_COLUMN ),
                    r.getString( "hotel_name" ), r.getInt( CONTRACT_ID_COLUMN ), r.getString( "room_name" ),
                    r.getInt( "number_of_adults" ), complete ?
                                                            nights.stream().map( BookedNight::sellingPrice ).reduce( new BigDecimal( "0.00" ), BigDecimal::add ) :
                                                            null, nights );
        }, id );
    }

    // Returns filtered reservation details and authoritative pagination totals.
    public Page list( String query, String status, int page, int size )
    {
        return list( query, status, page, size, null, null );
    }

    // Returns filtered reservation details and authoritative pagination totals.
    public Page list( String query, String status, int page, int size, LocalDate stayFrom, LocalDate stayTo )
    {
        return list( query, status, page, size, stayFrom, stayTo, "" );
    }

    /**
     * Filters in SQL before selecting the newest-first page. Count and ID queries reuse
     * the same predicates and business date so their matching totals stay consistent.
     * Stay From/To match inclusive occupied dates, whereas Stay Period is relative to
     * the injected clock: checkout today is past, not ongoing.
     *
     * @param page zero-based page index
     * @param size validated legacy argument; reservation pages always use {@link #PAGE_SIZE}
     * @return matching reservation details and the total across all matching pages
     */
    public Page list(
            String query, String status, int page, int size, LocalDate stayFrom, LocalDate stayTo,
            String stayPeriod )
    {
        if( !Set.of( "", "ALL", "UPCOMING", "ONGOING", "PAST" ).contains( stayPeriod ) )
        {
            throw new ReservationException( 400, "INVALID_FILTER",
                    "Choose All Stays, Upcoming, Ongoing or Past" );
        }
        if( stayFrom != null && stayTo != null && stayFrom.isAfter( stayTo ) )
        {
            throw new ReservationException( 400, "INVALID_FILTER", "Stay From must be on or before Stay To" );
        }
        if( page < 0 || size < 1 || size > 50 || query.length() > 150 ||
                    !Set.of( "", CONFIRMED, "CANCELLED" ).contains( status ) )
        {
            throw new ReservationException( 400, "INVALID_FILTER",
                    "Use a valid status, page and page size (1–50)" );
        }
        size = PAGE_SIZE;
        // Reservations always use ten records per page, including legacy callers.
        // Dynamic predicates below are fixed SQL fragments; every filter value is bound
        // through JdbcTemplate arguments in both the count and paginated ID queries.
        String where = " where (?='' or r.status=?) and (?='' or cast(r.reservation_id as char)=? or cast(c.customer_id as char)=? or locate(lower(?),lower(c.name))>0 or locate(lower(?),lower(coalesce(c.email,'')))>0 or locate(?,coalesce(c.phone,''))>0)";
        List<Object> values = new ArrayList<>(
                Arrays.asList( status, status, query, query, query, query, query, query ) );
        // Filter dates are inclusive occupied dates; checkout remains excluded.
        if( stayFrom != null )
        {
            where += " and r.check_out_date>?";
            values.add( stayFrom );
        }
        if( stayTo != null )
        {
            where += " and r.check_in_date<=?";
            values.add( stayTo );
        }
        LocalDate today = LocalDate.now( clock );
        switch( stayPeriod )
        {
            case "UPCOMING" ->
            {
                where += " and r.check_in_date>?";
                values.add( today );
            }
            case "ONGOING" ->
            {
                where += " and r.check_in_date<=? and r.check_out_date>?";
                values.add( today );
                values.add( today );
            }
            case "PAST" ->
            {
                where += " and r.check_out_date<=?";
                values.add( today );
            }
            default ->
            {
                // ALL and the empty default deliberately add no relative stay-date predicate.
            }
        }
        Object[] args = values.toArray();
        long count = Objects.requireNonNull( jdbc.queryForObject(
                "select count(*) from reservation r join customer c on c.customer_id=r.customer_id" + where,
                Long.class, args ) );
        var params = new ArrayList<Object>( Arrays.asList( args ) );
        params.add( size );
        params.add( ( long ) page * size );
        var ids = jdbc.queryForList(
                "select r.reservation_id from reservation r join customer c on c.customer_id=r.customer_id" +
                        where + " order by r.reservation_id desc limit ? offset ?", Integer.class,
                params.toArray() );
        return new Page( summaries(ids), page, size, count );
    }

    /** Two batched reads restricted to the selected page; no nightly DTOs or repricing. */
    private List<ReservationResponse.Summary> summaries(List<Integer> ids)
    {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        var rooms = new java.util.HashMap<Integer,List<ReservationResponse.RoomSummary>>();
        var totals = new java.util.HashMap<Integer,BigDecimal>();
        var incomplete = new java.util.HashSet<Integer>();
        var affected = new java.util.HashSet<Integer>();
        // Aggregate each room independently so partial legacy snapshots never yield a guessed total.
        jdbc.query("""
                select i.reservation_id,i.reservation_item_id,h.hotel_id,h.name as hotel_name,t.name as room_name,
                 i.number_of_adults,r.status,r.check_out_date,h.status as hotel_status,c.status as contract_status,
                 o.status as offering_status,
                 case when datediff(r.check_out_date,r.check_in_date)>0
                  and count(n.nightly_rate_id)=datediff(r.check_out_date,r.check_in_date)
                  and min(n.occupied_date)>=r.check_in_date and max(n.occupied_date)<r.check_out_date
                 then sum(n.selling_price) else null end as room_total
                from reservation_item i join reservation r on r.reservation_id=i.reservation_id
                join contract_room_offering o on o.offering_id=i.offering_id
                join contract c on c.contract_id=o.contract_id join hotel h on h.hotel_id=c.hotel_id
                join room_type t on t.room_type_id=o.room_type_id
                left join reservation_item_nightly_rate n on n.reservation_item_id=i.reservation_item_id
                where i.reservation_id in (
                """ + placeholders + ") group by i.reservation_item_id order by i.reservation_item_id", r -> {
            int id = r.getInt("reservation_id");
            rooms.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new ReservationResponse.RoomSummary(
                    r.getInt(HOTEL_ID_COLUMN), r.getString("hotel_name"), r.getString("room_name"), r.getInt("number_of_adults")));
            BigDecimal total = r.getBigDecimal("room_total");
            if (total == null) incomplete.add(id);
            else totals.merge(id, total, BigDecimal::add);
            if (CONFIRMED.equals(r.getString(STATUS_COLUMN))
                    && r.getObject(CHECK_OUT_DATE, LocalDate.class).isAfter(LocalDate.now(clock))
                    && ("INACTIVE".equals(r.getString("hotel_status"))
                    || SUSPENDED.equals(r.getString("contract_status"))
                    || SUSPENDED.equals(r.getString("offering_status")))) affected.add(id);
        }, ids.toArray());
        return jdbc.query("""
                select r.reservation_id,r.customer_id,c.name,r.check_in_date,r.check_out_date,r.status,r.currency_code
                from reservation r join customer c on c.customer_id=r.customer_id where r.reservation_id in (
                """ + placeholders + ") order by r.reservation_id desc", (r, n) -> {
            int id = r.getInt("reservation_id");
            return new ReservationResponse.Summary(id, r.getInt("customer_id"), r.getString("name"),
                    r.getObject("check_in_date", LocalDate.class), r.getObject(CHECK_OUT_DATE, LocalDate.class),
                    r.getString(STATUS_COLUMN), r.getString("currency_code"), incomplete.contains(id) ? null : totals.get(id),
                    List.copyOf(rooms.getOrDefault(id, List.of())), affected.contains(id));
        }, ids.toArray());
    }

    /**
     * Best-effort double-click/retry suppression, serialized by the hotel lock. No durable idempotency key exists.
     */
    public Optional<ReservationResponse> recentDuplicate( ReservationRequest request )
    {
        var ids = jdbc.queryForList(
                "select reservation_id from reservation where customer_id=? and check_in_date=? and check_out_date=? and status='CONFIRMED' and currency_code=? and created_at>=current_timestamp - interval 30 second order by reservation_id desc limit 20",
                Integer.class, request.customerId(), request.checkIn(), request.checkOut(),
                request.acceptedCurrency() );
        var wanted = request.rooms().stream().map( r -> r.offeringId() + ":" + r.adults() ).sorted().toList();
        for( int id : ids )
        {
            var actual = jdbc.query(
                    "select offering_id,number_of_adults from reservation_item where reservation_id=?",
                    ( r, n ) -> r.getInt( 1 ) + ":" + r.getInt( 2 ), id ).stream().sorted().toList();
            if( wanted.equals( actual ) )
            {
                var detail = detail( id );
                if( detail.totalPrice() != null &&
                            detail.totalPrice().compareTo( request.acceptedTotal() ) == 0 &&
                            detail.rooms().stream().allMatch( r -> r.hotelId() == request.hotelId() &&
                                                                           r.contractId() == request.contractId() ) )
                {
                    return Optional.of( detail );
                }
            }
        }
        return Optional.empty();
    }
}
