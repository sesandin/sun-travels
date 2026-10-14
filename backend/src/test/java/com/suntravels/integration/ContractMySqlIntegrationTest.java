package com.suntravels.integration;

import com.suntravels.dto.*;
import com.suntravels.entity.*;
import com.suntravels.exception.ContractException;
import com.suntravels.repository.*;
import com.suntravels.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Opt in on a disposable MySQL 8 database with the existing schema.
 * No DDL: creates and deletes only dedicated fixture records. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="SUNTRAVELS_MYSQL_TESTS", matches="true")
class ContractMySqlIntegrationTest {
    @Autowired ContractService service;
    @Autowired OfferingService offeringService;
    @Autowired ContractRepository contracts;
    @Autowired HotelRepository hotels;
    @Autowired RoomTypeRepository rooms;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired Clock clock;
    final List<Integer> hotelIds = new ArrayList<>(), roomIds = new ArrayList<>(), customerIds = new ArrayList<>();
    Integer hotelId, roomId, candidateId, candidateOffering;
    LocalDate today;

    TransactionTemplate tx() { return new TransactionTemplate(manager); }

    Integer hotel() {
        Integer id = tx().execute(t -> {
            Hotel h = new Hotel(); h.setName("Contract test " + UUID.randomUUID()); h.setStatus(HotelStatus.ACTIVE);
            return hotels.saveAndFlush(h).getHotelId();
        });
        hotelIds.add(id); return id;
    }

    Integer room() {
        Integer id = tx().execute(t -> {
            RoomType r = new RoomType(); r.setName("Contract test " + UUID.randomUUID());
            return rooms.saveAndFlush(r).getRoomTypeId();
        });
        roomIds.add(id); return id;
    }

    Integer contract(Integer h, LocalDate from, LocalDate to) {
        return service.createContract(new ContractRequest(h, from, to, BigDecimal.TEN, "USD")).contractId();
    }

    Integer offering(Integer c, Integer room) {
        return offeringService.create(c, new OfferingRequest(room, BigDecimal.TEN, 10, 2, OfferingStatus.ENABLED)).offeringId();
    }

    void fixtureStatus(Integer id, ContractStatus status) {
        // Imported/historical state setup only; no production approval shortcut.
        tx().executeWithoutResult(t -> {
            Contract c = contracts.findForUpdate(id).orElseThrow(); c.setStatus(status); contracts.saveAndFlush(c);
        });
    }

    Integer reservation(Integer offer, String status, LocalDate checkIn, LocalDate checkOut) {
        return tx().execute(t -> {
            jdbc.update("insert into customer(name) values (?)", "Contract test " + UUID.randomUUID());
            Integer customer = jdbc.queryForObject("select last_insert_id()", Integer.class); customerIds.add(customer);
            jdbc.update("insert into reservation(check_in_date,check_out_date,status,customer_id,currency_code) values (?,?,?,?,?)", checkIn, checkOut, status, customer, "USD");
            Integer reservation = jdbc.queryForObject("select last_insert_id()", Integer.class);
            jdbc.update("insert into reservation_item(number_of_adults,offering_id,reservation_id,booked_rate_per_person_per_night,booked_markup_percentage,booked_selling_price) values (?,?,?,?,?,?)", 2, offer, reservation, new BigDecimal("10"), new BigDecimal("15"), new BigDecimal("23"));
            Integer item = jdbc.queryForObject("select last_insert_id()", Integer.class);
            jdbc.update("insert into reservation_item_nightly_rate(reservation_item_id,occupied_date,hotelier_rate,markup_percentage,selling_price) values (?,?,?,?,?)", item, checkIn, new BigDecimal("10"), new BigDecimal("15"), new BigDecimal("23"));
            return reservation;
        });
    }

    @BeforeEach void seed() {
        assertTrue(jdbc.queryForObject("select version()", String.class).startsWith("8."));
        today = LocalDate.now(clock); hotelId = hotel(); roomId = room();
        candidateId = contract(hotelId, today, today.plusDays(30)); candidateOffering = offering(candidateId, roomId);
    }

    @AfterEach void cleanup() {
        tx().executeWithoutResult(t -> {
            for (Integer customer : customerIds) {
                jdbc.update("delete n from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id join reservation r on r.reservation_id=i.reservation_id where r.customer_id=?", customer);
                jdbc.update("delete i from reservation_item i join reservation r on r.reservation_id=i.reservation_id where r.customer_id=?", customer);
                jdbc.update("delete from reservation where customer_id=?", customer);
                jdbc.update("delete from customer where customer_id=?", customer);
            }
            for (Integer h : hotelIds) {
                jdbc.update("delete s from offering_seasonal_rate s join contract_room_offering o on o.offering_id=s.offering_id join contract c on c.contract_id=o.contract_id where c.hotel_id=?", h);
                jdbc.update("delete o from contract_room_offering o join contract c on c.contract_id=o.contract_id where c.hotel_id=?", h);
                jdbc.update("delete from contract where hotel_id=?", h);
                jdbc.update("delete from hotel where hotel_id=?", h);
            }
            for (Integer r : roomIds) jdbc.update("delete from room_type where room_type_id=?", r);
        });
    }

    @Test void concurrentCreationReturnsOnlyDistinctFinalCodes() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ContractResponse> create = () -> {
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return service.createContract(new ContractRequest(hotelId, today, today.plusDays(30), BigDecimal.TEN, "USD"));
        };
        try {
            Future<ContractResponse> one = pool.submit(create), two = pool.submit(create); start.countDown();
            ContractResponse a = one.get(15, TimeUnit.SECONDS), b = two.get(15, TimeUnit.SECONDS);
            assertNotEquals(a.contractId(), b.contractId()); assertNotEquals(a.contractCode(), b.contractCode());
            assertEquals("C" + String.format(Locale.ROOT,"%03d", a.contractId()), a.contractCode());
            assertEquals("C" + String.format(Locale.ROOT,"%03d", b.contractId()), b.contractCode());
            assertEquals(0, jdbc.queryForObject("select count(*) from contract where hotel_id=? and contract_code like 'TMP-%'", Integer.class, hotelId));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }

    @Test void codeCollisionRollsBackTemporaryInsertAndPreservesLegacyRow() {
        assertEquals(1, jdbc.queryForObject("select @@auto_increment_increment", Integer.class), "Collision fixture requires default identity increment");
        Integer legacy = tx().execute(t -> {
            Contract c = new Contract(); c.setHotel(hotels.findById(hotelId).orElseThrow()); c.setStatus(ContractStatus.DRAFT);
            c.setContractCode("LEGACY-" + UUID.randomUUID()); c.setValidFrom(today); c.setValidTo(today.plusDays(30));
            c.setMarkupPercentage(BigDecimal.TEN); c.setCurrencyCode("USD"); c = contracts.saveAndFlush(c);
            // Controlled fixture reserves the NEXT identity's code; runtime does not use ID+1.
            c.setContractCode("C" + String.format(Locale.ROOT, "%03d", c.getContractId() + 1));
            return contracts.saveAndFlush(c).getContractId();
        });
        var before = jdbc.queryForMap("select * from contract where contract_id=?", legacy);
        Integer count = jdbc.queryForObject("select count(*) from contract where hotel_id=?", Integer.class, hotelId);
        assertEquals(409, assertThrows(ContractException.class, () -> contract(hotelId, today, today.plusDays(30))).getStatus());
        assertEquals(count, jdbc.queryForObject("select count(*) from contract where hotel_id=?", Integer.class, hotelId));
        assertEquals(before, jdbc.queryForMap("select * from contract where contract_id=?", legacy));
        assertEquals(0, jdbc.queryForObject("select count(*) from contract where hotel_id=? and contract_code like 'TMP-%'", Integer.class, hotelId));
    }

    @Test void concurrentSameHotelApprovalHasOneWinnerAndNeverSuspendsOther() throws Exception {
        Integer secondId = contract(hotelId, today, today.plusDays(30)); offering(secondId, roomId);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch approved = new CountDownLatch(1), release = new CountDownLatch(1), attempting = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> tx().executeWithoutResult(t -> {
                service.approveContract(candidateId); approved.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }));
            assertTrue(approved.await(10, TimeUnit.SECONDS));
            Future<Integer> second = pool.submit(() -> {
                attempting.countDown();
                try { service.approveContract(secondId); return 200; }
                catch (ContractException ex) { return ex.getStatus(); }
            });
            assertTrue(attempting.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            release.countDown(); first.get(15, TimeUnit.SECONDS); assertEquals(409, second.get(15, TimeUnit.SECONDS));
            assertEquals(ContractStatus.APPROVED, service.getContractById(candidateId).status());
            assertEquals(ContractStatus.DRAFT, service.getContractById(secondId).status());
        } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }

    @Test void failedOuterTransactionRollsBackApprovalStatus() {
        assertThrows(IllegalStateException.class, () -> tx().executeWithoutResult(t -> {
            service.approveContract(candidateId); throw new IllegalStateException("force rollback");
        }));
        assertEquals(ContractStatus.DRAFT, service.getContractById(candidateId).status());
    }

    @Test void inclusiveContractBoundaryConflictsButNextDayAndFutureApprovalSucceed() {
        service.approveContract(candidateId);
        Integer shared = contract(hotelId, today.plusDays(30), today.plusDays(40)); offering(shared, roomId);
        assertEquals(409, assertThrows(ContractException.class, () -> service.approveContract(shared)).getStatus());
        Integer adjacent = contract(hotelId, today.plusDays(31), today.plusDays(40)); offering(adjacent, roomId);
        assertEquals(ContractStatus.APPROVED, service.approveContract(adjacent).status());
    }

    static Stream<Arguments> replacementCases() {
        return Stream.of(
            Arguments.of("APPROVED","CONFIRMED",-2,1,true,true,true),
            Arguments.of("SUSPENDED","CONFIRMED",-2,1,true,true,true),
            Arguments.of("TERMINATED","CONFIRMED",0,2,true,true,true),
            Arguments.of("SUSPENDED","CONFIRMED",-2,0,true,true,false),
            Arguments.of("SUSPENDED","PENDING",0,2,true,true,false),
            Arguments.of("SUSPENDED","CANCELLED",0,2,true,true,false),
            Arguments.of("SUSPENDED","CONFIRMED",30,31,true,true,true),
            Arguments.of("SUSPENDED","CONFIRMED",31,32,true,true,false),
            Arguments.of("SUSPENDED","CONFIRMED",0,2,false,true,false),
            Arguments.of("SUSPENDED","CONFIRMED",0,2,true,false,false)
        );
    }

    @ParameterizedTest @MethodSource("replacementCases")
    void replacementUsesActualNightsHotelRoomAndOutstandingStatus(String oldStatus, String reservationStatus,
            int in, int out, boolean sameRoom, boolean sameHotel, boolean blocked) {
        Integer old = contract(sameHotel ? hotelId : hotel(), today.minusDays(300), today.minusDays(100));
        Integer oldOffering = offering(old, sameRoom ? roomId : room()); fixtureStatus(old, ContractStatus.valueOf(oldStatus));
        reservation(oldOffering, reservationStatus, today.plusDays(in), today.plusDays(out));
        if (blocked) {
            assertEquals(409, assertThrows(ContractException.class, () -> service.approveContract(candidateId)).getStatus());
            assertEquals(ContractStatus.DRAFT, service.getContractById(candidateId).status());
        } else assertEquals(ContractStatus.APPROVED, service.approveContract(candidateId).status());
    }

    @Test void exclusiveCheckoutAtFutureCandidateStartDoesNotBlock() {
        service.updateContract(candidateId, new ContractRequest(hotelId,today.plusDays(10),today.plusDays(30),BigDecimal.TEN,"USD"));
        Integer old = contract(hotelId,today.minusDays(100),today.minusDays(1)); Integer oldOffer = offering(old,roomId);
        fixtureStatus(old,ContractStatus.SUSPENDED); reservation(oldOffer,"CONFIRMED",today.plusDays(9),today.plusDays(10));
        assertEquals(ContractStatus.APPROVED,service.approveContract(candidateId).status());
    }

    static Stream<Arguments> terminationCases() {
        return Stream.of(Arguments.of("CONFIRMED",-1,1,true),Arguments.of("CONFIRMED",1,2,true),
                Arguments.of("CONFIRMED",-2,0,false),Arguments.of("PENDING",1,2,false),Arguments.of("CANCELLED",1,2,false));
    }

    @ParameterizedTest @MethodSource("terminationCases")
    void terminationUsesBusinessDateExclusiveCheckoutAndStatus(String status, int in, int out, boolean blocked) {
        service.approveContract(candidateId); reservation(candidateOffering,status,today.plusDays(in),today.plusDays(out));
        if (blocked) {
            assertEquals(409,assertThrows(ContractException.class,()->service.terminateContract(candidateId)).getStatus());
            assertEquals(ContractStatus.APPROVED,service.getContractById(candidateId).status());
        } else assertEquals(ContractStatus.TERMINATED,service.terminateContract(candidateId).status());
    }

    @Test void suspensionReapprovalAndMarkupPreserveConfirmedSnapshotsAndHotelState() {
        service.approveContract(candidateId); Integer reservation = reservation(candidateOffering,"CONFIRMED",today,today.plusDays(1));
        var r = jdbc.queryForMap("select * from reservation where reservation_id=?",reservation);
        var i = jdbc.queryForMap("select * from reservation_item where reservation_id=?",reservation);
        var n = jdbc.queryForMap("select n.* from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id where i.reservation_id=?",reservation);
        service.suspendContract(candidateId);
        assertEquals(409,assertThrows(ContractException.class,()->service.terminateContract(candidateId)).getStatus());
        service.reapproveContract(candidateId); service.updateMarkup(candidateId,new ContractMarkupRequest(new BigDecimal("25")));
        assertEquals(r,jdbc.queryForMap("select * from reservation where reservation_id=?",reservation));
        assertEquals(i,jdbc.queryForMap("select * from reservation_item where reservation_id=?",reservation));
        assertEquals(n,jdbc.queryForMap("select n.* from reservation_item_nightly_rate n join reservation_item i on i.reservation_item_id=n.reservation_item_id where i.reservation_id=?",reservation));
        assertEquals("ACTIVE",jdbc.queryForObject("select status from hotel where hotel_id=?",String.class,hotelId));
    }

    @Test void coordinatedPendingToConfirmedWriteCannotRaceTerminationGuard() throws Exception {
        service.approveContract(candidateId);
        Integer reservation = reservation(candidateOffering,"PENDING",today,today.plusDays(1));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1), attempting = new CountDownLatch(1);
        try {
            Future<?> writer = pool.submit(() -> tx().executeWithoutResult(t -> {
                // Fixture writer demonstrates the REQUIRED future coordination convention.
                // It is not a production reservation confirmation implementation.
                hotels.findForUpdate(hotelId).orElseThrow();
                Contract eligible = contracts.findForUpdate(candidateId).orElseThrow();
                assertEquals(ContractStatus.APPROVED,eligible.getStatus());
                jdbc.update("update reservation set status='CONFIRMED' where reservation_id=?",reservation);
                written.countDown();
                try { assertTrue(release.await(10,TimeUnit.SECONDS)); }
                catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }));
            assertTrue(written.await(10,TimeUnit.SECONDS));
            Future<Integer> terminating = pool.submit(() -> {
                attempting.countDown();
                try { service.terminateContract(candidateId); return 200; }
                catch(ContractException ex) { return ex.getStatus(); }
            });
            assertTrue(attempting.await(10,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->terminating.get(200,TimeUnit.MILLISECONDS));
            release.countDown(); writer.get(15,TimeUnit.SECONDS); assertEquals(409,terminating.get(15,TimeUnit.SECONDS));
            assertEquals(ContractStatus.APPROVED,service.getContractById(candidateId).status());
        } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS)); }
    }

    @Test void malformedConfirmedStayFailsClosed() {
        reservation(candidateOffering,"CONFIRMED",today,today);
        assertEquals(409,assertThrows(ContractException.class,()->service.approveContract(candidateId)).getStatus());
        assertEquals(409,assertThrows(ContractException.class,()->service.terminateContract(candidateId)).getStatus());
        assertEquals(ContractStatus.DRAFT,service.getContractById(candidateId).status());
    }
}
