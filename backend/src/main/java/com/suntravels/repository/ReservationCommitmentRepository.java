package com.suntravels.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only data access: no reservation entities or booking CRUD.
 * Caller holds HOTEL -> CONTRACT -> OFFERING locks. Future confirmations,
 * date/status/item changes and hotel closures must acquire the same HOTEL lock.
 * FOR UPDATE provides a fresh MySQL current read rather than an earlier snapshot.
 * Candidate room IDs come from freshly locked offerings, not a snapshot subquery.
 * The joins may return an ID more than once; any matching commitment blocks.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ReservationCommitmentRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private static final String SELECT = """
            select r.reservation_id
            from reservation r
            join reservation_item ri on ri.reservation_id = r.reservation_id
            join contract_room_offering o on o.offering_id = ri.offering_id
            join contract c on c.contract_id = o.contract_id
            """;

    public ReservationCommitmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public List<Integer> outstanding(Integer contractId, LocalDate today) {
        // Keep a current locking join read: scalar lookups before the hotel lock may have opened a snapshot.
        return jdbc.queryForList(SELECT + """
                where c.contract_id = ? and r.status = 'CONFIRMED'
                  and r.check_out_date > ?
                order by r.reservation_id for update
                """, Integer.class, contractId, today).stream().distinct().toList();
    }

    public List<Integer> invalidForContract(Integer contractId) {
        return jdbc.queryForList(SELECT + """
                where c.contract_id = ? and r.status = 'CONFIRMED'
                  and (r.check_out_date <= r.check_in_date or ri.number_of_adults <= 0)
                order by r.reservation_id for update
                """, Integer.class, contractId);
    }

    public List<Integer> replacementConflicts(Integer hotelId, Integer candidateId,
            LocalDate from, LocalDate to, LocalDate today, Set<Integer> roomIds) {
        return named.queryForList(SELECT + """
                where c.hotel_id = :hotel and c.contract_id <> :candidate
                  and r.status = 'CONFIRMED' and r.check_out_date > :today
                  and r.check_in_date <= :to and r.check_out_date > :from
                  and o.room_type_id in (:rooms)
                order by r.reservation_id for update
                """, Map.of("hotel", hotelId, "candidate", candidateId, "today", today,
                        "to", to, "from", from, "rooms", roomIds), Integer.class);
    }

    public List<Integer> invalidForReplacement(Integer hotelId, Integer candidateId, Set<Integer> roomIds) {
        return named.queryForList(SELECT + """
                where c.hotel_id = :hotel and c.contract_id <> :candidate
                  and r.status = 'CONFIRMED'
                  and (r.check_out_date <= r.check_in_date or ri.number_of_adults <= 0)
                  and o.room_type_id in (:rooms)
                order by r.reservation_id for update
                """, Map.of("hotel", hotelId, "candidate", candidateId, "rooms", roomIds), Integer.class);
    }
}
