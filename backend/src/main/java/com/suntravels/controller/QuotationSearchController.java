package com.suntravels.controller;
import com.suntravels.dto.*;
import com.suntravels.service.QuotationSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/search")
public class QuotationSearchController {
 private final QuotationSearchService service;
 public QuotationSearchController(QuotationSearchService service) { this.service=service; }
 @GetMapping("/limits") public QuotationSearchResponse.Limits limits() { return service.limits(); }
 @PostMapping("/quotations") public QuotationSearchResponse search(@Valid @RequestBody QuotationSearchRequest request) { return service.search(request); }
}
