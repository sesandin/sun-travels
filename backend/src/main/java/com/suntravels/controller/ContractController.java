package com.suntravels.controller;

import com.suntravels.dto.ContractMarkupRequest;
import com.suntravels.dto.ContractRequest;
import com.suntravels.dto.ContractResponse;
import com.suntravels.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping( "/api/contracts" )
public class ContractController
{
    private final ContractService service;

    // Initializes the contract controller and its required dependencies.
    public ContractController( ContractService service )
    {
        this.service = service;
    }

    // Lists contract records matching the supplied filters.
    @GetMapping
    public List<ContractResponse> list()
    {
        return service.getAllContracts();
    }

    // Retrieves the requested contract details.
    @GetMapping( "/{id}" )
    public ContractResponse get( @PathVariable( "id" ) Integer id )
    {
        return service.getContractById( id );
    }

    // Creates a contract from the validated request.
    @PostMapping
    public ResponseEntity<ContractResponse> create( @Valid @RequestBody ContractRequest request )
    {
        return ResponseEntity.status( 201 ).body( service.createContract( request ) );
    }

    // Updates the editable fields of the selected contract.
    @PutMapping( "/{id}" )
    public ContractResponse update(
            @PathVariable( "id" ) Integer id,
            @Valid @RequestBody ContractRequest request )
    {
        return service.updateContract( id, request );
    }

    // Updates the approved contract’s markup percentage.
    @PatchMapping( "/{id}/markup" )
    public ContractResponse markup(
            @PathVariable( "id" ) Integer id,
            @Valid @RequestBody ContractMarkupRequest request )
    {
        return service.updateMarkup( id, request );
    }

    // Approves the selected draft contract after eligibility checks.
    @PatchMapping( "/{id}/approve" )
    public ContractResponse approve( @PathVariable( "id" ) Integer id )
    {
        return service.approveContract( id );
    }

    // Reapproves the selected suspended contract after eligibility checks.
    @PatchMapping( "/{id}/reapprove" )
    public ContractResponse reapprove( @PathVariable( "id" ) Integer id )
    {
        return service.reapproveContract( id );
    }

    // Suspends new sales under the selected contract.
    @PatchMapping( "/{id}/suspend" )
    public ContractResponse suspend( @PathVariable( "id" ) Integer id )
    {
        return service.suspendContract( id );
    }

    // Terminates the selected contract after checking outstanding stays.
    @PatchMapping( "/{id}/terminate" )
    public ContractResponse terminate( @PathVariable( "id" ) Integer id )
    {
        return service.terminateContract( id );
    }
}
