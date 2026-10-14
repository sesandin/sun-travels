package com.suntravels.controller;
import com.suntravels.dto.*;
import com.suntravels.service.ContractService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService service;
    public ContractController(ContractService service) { this.service = service; }
    @GetMapping public List<ContractResponse> list() { return service.getAllContracts(); }
    @GetMapping("/{id}") public ContractResponse get(@PathVariable("id") Integer id) { return service.getContractById(id); }
    @PostMapping public ResponseEntity<ContractResponse> create(@Valid @RequestBody ContractRequest request) {
        return ResponseEntity.status(201).body(service.createContract(request));
    }
    @PutMapping("/{id}") public ContractResponse update(@PathVariable("id") Integer id, @Valid @RequestBody ContractRequest request) {
        return service.updateContract(id, request);
    }
    @PatchMapping("/{id}/markup") public ContractResponse markup(@PathVariable("id") Integer id, @Valid @RequestBody ContractMarkupRequest request) {
        return service.updateMarkup(id, request);
    }
    @PatchMapping("/{id}/approve") public ContractResponse approve(@PathVariable("id") Integer id) { return service.approveContract(id); }
    @PatchMapping("/{id}/reapprove") public ContractResponse reapprove(@PathVariable("id") Integer id) { return service.reapproveContract(id); }
    @PatchMapping("/{id}/suspend") public ContractResponse suspend(@PathVariable("id") Integer id) { return service.suspendContract(id); }
    @PatchMapping("/{id}/terminate") public ContractResponse terminate(@PathVariable("id") Integer id) { return service.terminateContract(id); }
}
