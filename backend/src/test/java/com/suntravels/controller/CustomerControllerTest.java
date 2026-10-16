package com.suntravels.controller;

import com.suntravels.dto.*;
import com.suntravels.exception.CustomerExceptionHandler;
import com.suntravels.exception.CustomerNotFoundException;
import com.suntravels.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = CustomerController.class, properties = "spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.web.servlet.SecurityAutoConfiguration")
@Import(CustomerExceptionHandler.class)
class CustomerControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    CustomerService service;

    @Test
    void listsGetsCreatesAndUpdatesCustomers() throws Exception {
        CustomerResponse customer = new CustomerResponse(3, "Alice", "+94771234567", "alice@example.com");
        when(service.searchCustomers("Alice", 0, 20)).thenReturn(
                new CustomerPageResponse(List.of(customer), 0, 20, 1, 1));
        when(service.getCustomer(3)).thenReturn(customer);
        when(service.createCustomer(any())).thenReturn(customer);
        when(service.updateCustomer(eq(3), any())).thenReturn(customer);
        mvc.perform(get("/api/customers").param("query", "Alice")).andExpect(status().isOk()).andExpect(
                jsonPath("$.customers[0].customerId").value(3));
        mvc.perform(get("/api/customers/3")).andExpect(status().isOk()).andExpect(
                jsonPath("$.email").value("alice@example.com"));
        String body = "{\"name\":\"Alice\",\"phone\":\"+94771234567\",\"email\":null}";
        mvc.perform(post("/api/customers").contentType("application/json").content(body)).andExpect(
                status().isCreated());
        mvc.perform(put("/api/customers/3").contentType("application/json").content(body)).andExpect(
                status().isOk());
    }

    @Test
    void validatesNameContactEmailPhoneAndLengths() throws Exception {
        for (String body : List.of("{\"name\":\" \",\"phone\":\"+94771234567\"}",
                "{\"name\":\"Alice\",\"phone\":\" \",\"email\":null}",
                "{\"name\":\"Alice\",\"email\":\"wrong\"}", "{\"name\":\"Alice\",\"phone\":\"letters\"}",
                "{\"name\":\"" + "x".repeat(151) + "\",\"email\":\"a@example.com\"}")) {
            mvc.perform(post("/api/customers").contentType("application/json").content(body)).andExpect(
                    status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").exists());
        }
        verify(service, never()).createCustomer(any());
    }

    @Test
    void unknownAndMalformedIdentifiersHaveSafeErrors() throws Exception {
        when(service.getCustomer(99)).thenThrow(new CustomerNotFoundException(99));
        mvc.perform(get("/api/customers/99")).andExpect(status().isNotFound()).andExpect(
                jsonPath("$.message").value("Customer 99 was not found"));
        mvc.perform(get("/api/customers/nope")).andExpect(status().isBadRequest()).andExpect(
                jsonPath("$.message").exists());
    }
}
