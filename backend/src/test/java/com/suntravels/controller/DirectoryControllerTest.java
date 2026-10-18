package com.suntravels.controller;

import com.suntravels.dto.DirectoryPage;
import com.suntravels.service.DirectoryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DirectoryControllerTest {
    private final DirectoryService service = mock(DirectoryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DirectoryController(service)).build();

    // Verifies binds hotel filters and returns authoritative page metadata.
    @Test
    void bindsHotelFiltersAndReturnsAuthoritativePageMetadata() throws Exception {
        when(service.hotels("Galle", "ACTIVE", 2)).thenReturn(
                new DirectoryPage<>(List.of(), 2, 10, 25, 3, Map.of()));
        mvc.perform(get("/api/hotels/page").param("query", "Galle").param("status", "ACTIVE").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    // Verifies defaults to first page and passes all offering filters.
    @Test
    void defaultsToFirstPageAndPassesAllOfferingFilters() throws Exception {
        mvc.perform(get("/api/room-types/page")).andExpect(status().isOk());
        mvc.perform(get("/api/contracts/page").param("query", "C26").param("status", "SUSPENDED"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/offerings/page").param("hotelId", "7").param("roomTypeId", "3")
                .param("contractStatus", "APPROVED").param("offeringStatus", "ENABLED")
                .param("sortBy", "rate").param("direction", "desc").param("page", "1"))
                .andExpect(status().isOk());
        verify(service).rooms("", 0);
        verify(service).contracts("C26", "SUSPENDED", 0);
        verify(service).offerings(Map.of("hotelId", "7", "roomTypeId", "3", "contractStatus", "APPROVED",
                "offeringStatus", "ENABLED", "sortBy", "rate", "direction", "desc", "page", "1"), 1);
    }
}
