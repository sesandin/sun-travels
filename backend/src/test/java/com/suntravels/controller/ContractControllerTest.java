package com.suntravels.controller;

import com.suntravels.dto.*;
import com.suntravels.entity.ContractStatus;
import com.suntravels.exception.*;
import com.suntravels.service.ContractService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ContractControllerTest {
    @Mock ContractService service;
    MockMvc mvc;
    LocalValidatorFactoryBean validator;
    final String json = """
        {"contractCode":"CODE","hotelId":7,"validFrom":"2026-01-01","validTo":"2026-12-31","markupPercentage":15.00,"currencyCode":"USD"}
        """;
    ContractResponse response() { return new ContractResponse(1,"CODE",7,"Hotel",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),new BigDecimal("15.00"),"USD",ContractStatus.DRAFT); }
    @BeforeEach void setup() {
        validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ContractController(service))
            .setControllerAdvice(new ContractExceptionHandler(), new GlobalExceptionHandler()).setValidator(validator).build();
    }
    @AfterEach void close() { validator.close(); }
    @Test void listsAndReadsDtos() throws Exception {
        when(service.getAllContracts()).thenReturn(List.of(response())); when(service.getContractById(1)).thenReturn(response());
        mvc.perform(get("/api/contracts")).andExpect(status().isOk()).andExpect(jsonPath("$[0].hotelName").value("Hotel"));
        mvc.perform(get("/api/contracts/1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
    }
    @Test void writesValidPayloadsAndIgnoresClientStatus() throws Exception {
        when(service.createContract(any())).thenReturn(response()); when(service.updateContract(eq(1),any())).thenReturn(response());
        when(service.updateMarkup(eq(1),any())).thenReturn(response());
        mvc.perform(post("/api/contracts").contentType("application/json").content(json.replace("\"USD\"}","\"USD\",\"status\":\"APPROVED\"}")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(put("/api/contracts/1").contentType("application/json").content(json)).andExpect(status().isOk());
        mvc.perform(patch("/api/contracts/1/markup").contentType("application/json").content("{\"markupPercentage\":0}" )).andExpect(status().isOk());
        verify(service).createContract(any(ContractRequest.class));
    }
    @Test void validatesHeaderBoundariesOnBothWrites() throws Exception {
        for (String invalid : List.of("{}",
            json.replace("15.00","-0.01"),json.replace("15.00","1000"),json.replace("15.00","0.001"),
            json.replace("USD","usd"),json.replace("USD","US"),json.replace("USD","USDD"),
            json.replace("2026-01-01","2027-01-01"),json.replace("\"hotelId\":7","\"hotelId\":0"))) {
            mvc.perform(post("/api/contracts").contentType("application/json").content(invalid)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").exists());
            mvc.perform(put("/api/contracts/1").contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void acceptsMaximumDecimalAndEqualDates() throws Exception {
        mvc.perform(post("/api/contracts").contentType("application/json").content(json.replace("CODE","a".repeat(45)).replace("15.00","999.99").replace("2026-12-31","2026-01-01")))
            .andExpect(status().isCreated()); verify(service).createContract(any());
    }
    @Test void validatesMarkupAndMalformedInputs() throws Exception {
        for (String body : List.of("{}","{\"markupPercentage\":-1}","{\"markupPercentage\":1000}","{\"markupPercentage\":1.001}"))
            mvc.perform(patch("/api/contracts/1/markup").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/contracts/abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/contracts").contentType("application/json").content("{")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/contracts").contentType("application/json").content(json.replace("2026-01-01","bad-date"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void reportsMissingHotelsContractsAndConflicts() throws Exception {
        when(service.getContractById(9)).thenThrow(ContractException.missing(9));
        when(service.createContract(any())).thenThrow(new HotelNotFoundException(7));
        when(service.updateContract(eq(1),any())).thenThrow(ContractException.conflict("Only DRAFT contracts allow header editing"));
        mvc.perform(get("/api/contracts/9")).andExpect(status().isNotFound());
        mvc.perform(post("/api/contracts").contentType("application/json").content(json)).andExpect(status().isNotFound());
        mvc.perform(put("/api/contracts/1").contentType("application/json").content(json)).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Only DRAFT contracts allow header editing"));
    }
    @Test void lockContentionIsTranslatedWithoutSqlDetails() throws Exception {
        when(service.approveContract(1)).thenThrow(new org.springframework.dao.PessimisticLockingFailureException("secret deadlock SQL"));
        mvc.perform(patch("/api/contracts/1/approve")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Another operation is updating this contract. Refresh and try again."));
    }
    @Test void lifecycleConflictsAndDatabaseFailuresAreSafe() throws Exception {
        when(service.approveContract(1)).thenThrow(ContractException.conflict("Approval blocked by CONFIRMED reservation 5"));
        when(service.reapproveContract(1)).thenThrow(ContractException.conflict("Only SUSPENDED contracts may be reapproved"));
        when(service.terminateContract(1)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secret SQL"));
        mvc.perform(patch("/api/contracts/1/approve")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Approval blocked by CONFIRMED reservation 5"));
        mvc.perform(patch("/api/contracts/1/reapprove")).andExpect(status().isConflict());
        mvc.perform(patch("/api/contracts/1/terminate")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Database verification or save failed. Reload the contract before retrying."));
    }
    @Test void exposesSafeLifecycleOperationsOnly() throws Exception {
        when(service.suspendContract(1)).thenReturn(response()); when(service.terminateContract(1)).thenReturn(response());
        when(service.approveContract(1)).thenReturn(response()); when(service.reapproveContract(1)).thenReturn(response());
        mvc.perform(patch("/api/contracts/1/suspend")).andExpect(status().isOk());
        mvc.perform(patch("/api/contracts/1/terminate")).andExpect(status().isOk());
        mvc.perform(patch("/api/contracts/1/approve")).andExpect(status().isOk());
        mvc.perform(patch("/api/contracts/1/reapprove")).andExpect(status().isOk());
        mvc.perform(delete("/api/contracts/1")).andExpect(status().isMethodNotAllowed());
        verify(service).approveContract(1); verify(service).reapproveContract(1);
    }
}
