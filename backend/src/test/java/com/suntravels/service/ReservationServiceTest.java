package com.suntravels.service;

import com.suntravels.dto.*;
import com.suntravels.entity.*;
import com.suntravels.exception.ReservationException;
import com.suntravels.repository.*;
import com.suntravels.repository.QuotationSearchRepository.*;
import jakarta.validation.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    @Mock
    ReservationRepository reservations;
    @Mock
    HotelRepository hotels;
    @Mock
    CustomerRepository customers;
    @Mock
    QuotationSearchRepository data;
    @Mock
    ContractRepository contracts;
    @Mock
    OfferingRepository offerings;
    ValidatorFactory factory;
    ReservationService service;
    final LocalDate today = LocalDate.of(2026, 10, 15), in = today.plusDays(10), out = in.plusDays(3);
    final BigDecimal markup = new BigDecimal("15.00");

    @BeforeEach
    void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new ReservationService(reservations, hotels, customers, data, new NightlyQuotationPricing(),
                new StayAvailability(), factory.getValidator(),
                Clock.fixed(today.atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant(),
                        ZoneId.of("Asia/Colombo")), contracts, offerings);
        var hotel = new Hotel();
        org.springframework.test.util.ReflectionTestUtils.setField(hotel, "hotelId", 1);
        var contract = new Contract();
        contract.setHotel(hotel);
        lenient().when(contracts.findHotelId(4)).thenReturn(Optional.of(1));
        lenient().when(contracts.findForUpdate(4)).thenReturn(Optional.of(contract));
        lenient().when(hotels.findForUpdate(1)).thenReturn(Optional.of(new Hotel()));
        lenient().when(customers.existsById(9)).thenReturn(true);
        lenient().when(data.candidates(any())).thenReturn(List.of(candidate(2, 3)));
        lenient().when(reservations.insert(anyString(), any(Object[].class))).thenReturn(101, 201, 202);
    }

    @AfterEach
    void close() {
        factory.close();
    }

    Candidate candidate(int max, int inventory) {
        return new Candidate(7, 4, 1, "Ocean", "Colombo", "C004", "USD", markup, in.minusDays(1),
                out.plusDays(30), 3, "Double", new BigDecimal("100.00"), inventory, max);
    }

    ReservationRequest request(String price, int... adults) {
        return new ReservationRequest(9, 1, 4, in, out,
                Arrays.stream(adults).mapToObj(a -> new ReservationRequest.Room(7, a)).toList(),
                new BigDecimal(price), "USD");
    }

    @Test
    void oneRoomStoresThreeExactNightlySnapshotsAfterHotelLock() {
        service.confirm(request("690", 2));
        var order = inOrder(hotels, customers, data, reservations);
        order.verify(hotels).findForUpdate(1);
        order.verify(customers).existsById(9);
        order.verify(reservations).recentDuplicate(any());
        order.verify(data).candidates(any());
        verify(reservations).night(201, in, new BigDecimal("100.00"), markup, new BigDecimal("230.00"));
        verify(reservations).night(201, in.plusDays(1), new BigDecimal("100.00"), markup,
                new BigDecimal("230.00"));
        verify(reservations).night(201, in.plusDays(2), new BigDecimal("100.00"), markup,
                new BigDecimal("230.00"));
    }

    @Test
    void multipleRoomsOfSameOfferingEachHaveOwnSnapshots() {
        service.confirm(request("1035", 2, 1));
        verify(reservations, times(6)).night(anyInt(), any(), any(), any(), any());
        verify(reservations).night(202, in, new BigDecimal("100.00"), markup, new BigDecimal("115.00"));
    }

    @Test
    void perRoomAdultCapacityCannotUseCombinedOccupancy() {
        var requestForAssertion1 = request("1035", 3);

        assertEquals("UNAVAILABLE", assertThrows(ReservationException.class,
                () -> service.confirm(requestForAssertion1)).getCode());
        noWrites();
    }

    @Test
    void invalidDatesAndCustomerAreRejectedBeforeWrites() {
        var r = request("690", 2);
        for (var dates : List.of(List.of(today.minusDays(1), out), List.of(in, in), List.of(out, in),
                List.of(in, in.plusDays(32)), List.of(today.plusDays(731), today.plusDays(732)))) {
            var invalid = new ReservationRequest(9, 1, 4, dates.get(0), dates.get(1), r.rooms(),
                    r.acceptedTotal(), "USD");
            assertEquals(400,
                    assertThrows(ReservationException.class, () -> service.confirm(invalid)).getStatus());
        }
        when(customers.existsById(9)).thenReturn(false);
        assertEquals("CUSTOMER_NOT_FOUND",
                assertThrows(ReservationException.class, () -> service.confirm(r)).getCode());
        noWrites();
    }

    @Test
    void emptyOrModifiedSelectionsCannotBeConfirmed() {
        var requestForAssertion2 = request("0");

        assertThrows(ReservationException.class, () -> service.confirm(requestForAssertion2));
        var r = request("690", 2);
        var other = new ReservationRequest(9, 1, 88, in, out, r.rooms(), r.acceptedTotal(), "USD");
        assertThrows(ReservationException.class, () -> service.confirm(other));
        noWrites();
    }

    @Test
    void ineligibleSelectionIsRejectedWhenSearchNoLongerReturnsIt() {
        when(data.candidates(any())).thenReturn(List.of());
        var requestForAssertion3 = request("690", 2);
        assertThrows(ReservationException.class, () -> service.confirm(requestForAssertion3));
        noWrites();
    }

    @Test
    void priceChangeReturnsSellingQuoteAndNoPartialHeader() {
        var requestForAssertion4 = request("500", 2);

        var ex = assertThrows(ReservationException.class, () -> service.confirm(requestForAssertion4));
        assertEquals("PRICE_CHANGED", ex.getCode());
        assertEquals(new BigDecimal("690.00"), ex.getQuotation().totalPrice());
        assertEquals(3, ex.getQuotation().rooms().get(0).nightlyPrices().size());
        noWrites();
    }

    @Test
    void updatedAmountMustMatchAgainAndCurrencyMustAlsoMatch() {
        var requestForAssertion5 = request("500", 2);

        assertThrows(ReservationException.class, () -> service.confirm(requestForAssertion5));
        service.confirm(request("690", 2));
        verify(reservations).detail(101);
        var r = request("690", 2);
        var requestForAssertion6 = new ReservationRequest(9, 1, 4, in, out, r.rooms(), r.acceptedTotal(),
                "EUR");
        assertEquals("PRICE_CHANGED", assertThrows(ReservationException.class,
                () -> service.confirm(requestForAssertion6)).getCode());
    }

    @Test
    void seasonalMixedRateLeavesSingleNightFieldsNull() {
        when(data.seasons(Set.of(7))).thenReturn(
                List.of(new Season(1, 7, in.plusDays(1), in.plusDays(1), new BigDecimal("150.00"))));
        service.confirm(request("805", 2));
        verify(reservations).insert(startsWith("insert into reservation_item("), eq(101), eq(7), eq(2),
                isNull(), eq(markup), isNull());
        verify(reservations).night(201, in.plusDays(1), new BigDecimal("150.00"), markup,
                new BigDecimal("345.00"));
    }

    @Test
    void fullInventoryAndPartialStayInventoryRejectGroupedRoomDemand() {
        when(data.candidates(any())).thenReturn(List.of(candidate(2, 2)));
        when(data.bookedRooms(Set.of(7), in, out)).thenReturn(
                List.of(new BookedRoom(20, 7, in.plusDays(1), in.plusDays(2), 2)));
        var requestForAssertion7 = request("1380", 2, 2);

        assertThrows(ReservationException.class, () -> service.confirm(requestForAssertion7));
        noWrites();
        when(data.candidates(any())).thenReturn(List.of(candidate(2, 1)));
        var requestForAssertion8 = request("690", 2);

        assertThrows(ReservationException.class, () -> service.confirm(requestForAssertion8));
        noWrites();
    }

    @Test
    void recentIdenticalRetryReturnsExistingBookingWithoutNewInventoryConsumption() {
        var saved = new ReservationResponse(101, 9, "Customer", null, null, in, out, 3, "CONFIRMED",
                LocalDateTime.now(), "USD", new BigDecimal("690.00"), List.of());
        when(reservations.recentDuplicate(any())).thenReturn(Optional.of(saved));
        assertSame(saved, service.confirm(request("690", 2)));
        verifyNoInteractions(data);
        noWrites();
    }

    @Test
    void cancellationUsesAscendingHotelLocksAndIsRepeatable() {
        when(reservations.hotelIds(101)).thenReturn(List.of(1));
        when(reservations.lockStatus(101)).thenReturn("CONFIRMED", "CANCELLED");
        service.cancel(101);
        service.cancel(101);
        verify(reservations, times(1)).cancel(101);
        var order = inOrder(hotels, reservations);
        order.verify(reservations).hotelIds(101);
        order.verify(hotels).findForUpdate(1);
        order.verify(reservations).lockStatus(101);
        order.verify(reservations).cancel(101);
        verify(reservations, never()).night(anyInt(), any(), any(), any(), any());
    }

    @Test
    void explicitExpensiveMixedSelectionIsNeverReallocatedAtConfirmation() {
        var expensive = new Candidate(8, 4, 1, "Ocean", "Colombo", "C004", "USD", markup, in.minusDays(1),
                out.plusDays(30), 4, "Sea View", new BigDecimal("150.00"), 3, 3);
        when(data.candidates(any())).thenReturn(List.of(candidate(2, 3), expensive));
        var request = new ReservationRequest(9, 1, 4, in, out,
                List.of(new ReservationRequest.Room(8, 1), new ReservationRequest.Room(7, 2)),
                new BigDecimal("1207.50"), "USD");
        service.confirm(request);
        verify(reservations).insert(startsWith("insert into reservation_item("), eq(101), eq(8), eq(1),
                eq(new BigDecimal("150.00")), eq(markup), eq(new BigDecimal("172.50")));
        verify(reservations).insert(startsWith("insert into reservation_item("), eq(101), eq(7), eq(2),
                eq(new BigDecimal("100.00")), eq(markup), eq(new BigDecimal("230.00")));
    }

    private void noWrites() {
        verify(reservations, never()).insert(anyString(), any(Object[].class));
        verify(reservations, never()).night(anyInt(), any(), any(), any(), any());
    }

    @Test
    void confirmsAboveTenOnlyWhenTheSelectedOfferingAllowsIt() {
        when(data.candidates(any())).thenReturn(List.of(candidate(12, 3)));
        service.confirm(request("4140", 12));
        verify(reservations).insert(startsWith("insert into reservation_item("), eq(101), eq(7), eq(12),
                eq(new BigDecimal("100.00")), eq(markup), eq(new BigDecimal("1380.00")));
        verify(reservations, times(3)).night(eq(201), any(), eq(new BigDecimal("100.00")), eq(markup),
                eq(new BigDecimal("1380.00")));
    }

    @Test
    void decreasedContractualOccupancyRejectsConfirmationBeforeAnyWrite() {
        when(data.candidates(any())).thenReturn(List.of(candidate(6, 3)));
        var requestForAssertion9 = request("4140", 12);

        assertEquals("UNAVAILABLE", assertThrows(ReservationException.class,
                () -> service.confirm(requestForAssertion9)).getCode());
        noWrites();
    }
}
