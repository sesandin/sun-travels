package com.suntravels.controller;

import com.suntravels.dto.ManagementHotelOption;
import com.suntravels.entity.HotelStatus;
import com.suntravels.repository.HotelRepository;
import com.suntravels.service.HotelService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManagementHotelLookupTest
{
    @Test void routesManagementLookupAndResolution() throws Exception
    {
        var service = mock(HotelService.class);
        ManagementHotelOption option = new ManagementHotelOption() {
            public Integer getHotelId() { return 7; }
            public String getName() { return "Ocean"; }
            public String getCity() { return "Galle"; }
            public String getCountry() { return "Sri Lanka"; }
            public String getStatus() { return "INACTIVE"; }
        };
        when(service.options("Oc", HotelStatus.INACTIVE)).thenReturn(List.of(option));
        when(service.option(7)).thenReturn(option);
        var mvc = MockMvcBuilders.standaloneSetup(new HotelController(service)).build();
        mvc.perform(get("/api/hotels/options").param("term","Oc").param("status","INACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].hotelId").value(7))
                .andExpect(jsonPath("$[0].status").value("INACTIVE"));
        mvc.perform(get("/api/hotels/options/7")).andExpect(status().isOk());
        mvc.perform(get("/api/hotels/options").param("status","UNKNOWN")).andExpect(status().isBadRequest());
        verify(service, never()).getAllHotels();
    }

    @Test void validatesAndEscapesLiteralContainsPatternWithoutEntityLoading()
    {
        var repository = mock(HotelRepository.class);
        var service = new HotelService(repository);
        assertTrue(service.options(" ", null).isEmpty());
        verifyNoInteractions(repository);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.options("a".repeat(151), null));
        service.options(" Ab!%_ ", HotelStatus.ACTIVE);
        verify(repository).options("%Ab!!!%!_%", "ACTIVE");
        service.options( " beach ", null );
        verify(repository).allStatusOptions( "%beach%" );
        verify(repository, never()).findAll();
    }
}
