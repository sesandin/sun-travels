package com.suntravels.service;

import com.suntravels.dto.HotelResponse;
import com.suntravels.entity.Hotel;
import com.suntravels.repository.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectoryServiceTest {
    private final HotelRepository hotels = mock(HotelRepository.class);
    private final RoomTypeRepository rooms = mock(RoomTypeRepository.class);
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final OfferingRepository offerings = mock(OfferingRepository.class);
    private final HotelService hotelService = mock(HotelService.class);
    private final DirectoryService service = new DirectoryService(hotels, rooms, contracts, offerings,
            hotelService, mock(RoomTypeService.class), mock(ContractService.class), mock(EntityManager.class));

    // Verifies returns ten rows and matching totals with stable sorting.
    @Test
    void returnsTenRowsAndMatchingTotalsWithStableSorting() {
        List<Hotel> rows = java.util.stream.IntStream.range(0, 10).mapToObj(i -> new Hotel()).toList();
        when(hotels.findAll(org.mockito.ArgumentMatchers.<Specification<Hotel>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 10), 25));
        when(hotelService.toResponse(any())).thenReturn(new HotelResponse());
        var result = service.hotels("Colombo", "ACTIVE", 0);
        assertEquals(10, result.items().size());
        assertEquals(10, result.size());
        assertEquals(25, result.totalElements());
        assertEquals(3, result.totalPages());
        verify(hotels).findAll(org.mockito.ArgumentMatchers.<Specification<Hotel>>any(),
                eq(PageRequest.of(0, 10, Sort.by("hotelId"))));
    }

    // Verifies reconciles an empty last page after filtered records change.
    @Test
    void reconcilesAnEmptyLastPageAfterFilteredRecordsChange() {
        when(hotels.findAll(org.mockito.ArgumentMatchers.<Specification<Hotel>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 20))
                .thenReturn(new PageImpl<>(List.of(new Hotel()), PageRequest.of(1, 10), 20));
        when(hotelService.toResponse(any())).thenReturn(new HotelResponse());
        var result = service.hotels("", "", 2);
        assertEquals(1, result.page());
        assertEquals(20, result.totalElements());
        assertEquals(2, result.totalPages());
        verify(hotels).findAll(org.mockito.ArgumentMatchers.<Specification<Hotel>>any(),
                eq(PageRequest.of(1, 10, Sort.by("hotelId"))));
    }

    // Verifies rejects negative and overflowing pages.
    @Test
    void rejectsNegativeAndOverflowingPages() {
        assertThrows(ResponseStatusException.class, () -> service.hotels("", "", -1));
        assertThrows(ResponseStatusException.class, () -> service.rooms("", Integer.MAX_VALUE));
        verify(hotels, never()).findAll(org.mockito.ArgumentMatchers.<Specification<Hotel>>any(), any(Pageable.class));
    }
}
