package com.suntravels.controller;

import com.suntravels.dto.CustomerPageResponse;
import com.suntravels.dto.CustomerRequest;
import com.suntravels.dto.CustomerResponse;
import com.suntravels.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping( "/api/customers" )
public class CustomerController
{
    private final CustomerService service;

    // Initializes the customer controller and its required dependencies.
    public CustomerController( CustomerService service )
    {
        this.service = service;
    }

    // Searches customer records using the supplied criteria.
    @GetMapping
    public ResponseEntity<CustomerPageResponse> search(
            @RequestParam( required = false ) String query,
            @RequestParam( defaultValue = "0" ) int page, @RequestParam( defaultValue = "10" ) int size )
    {
        return ResponseEntity.ok( service.searchCustomers( query, page, size ) );
    }

    // Retrieves the requested customer details.
    @GetMapping( "/{id}" )
    public ResponseEntity<CustomerResponse> get( @PathVariable Integer id )
    {
        return ResponseEntity.ok( service.getCustomer( id ) );
    }

    // Creates a customer from the validated request.
    @PostMapping
    public ResponseEntity<CustomerResponse> create( @Valid @RequestBody CustomerRequest request )
    {
        return ResponseEntity.status( 201 ).body( service.createCustomer( request ) );
    }

    // Updates the editable fields of the selected customer.
    @PutMapping( "/{id}" )
    public ResponseEntity<CustomerResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody CustomerRequest request )
    {
        return ResponseEntity.ok( service.updateCustomer( id, request ) );
    }
}
