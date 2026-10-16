package com.suntravels.controller;

import com.suntravels.dto.*;
import com.suntravels.service.DirectoryService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DirectoryController {
    private final DirectoryService service;

    public DirectoryController(DirectoryService service) {
        this.service = service;
    }

    @GetMapping("/hotels/page")
    public DirectoryPage<HotelResponse> hotels(@RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "status", defaultValue = "") String status, @RequestParam(name = "page", defaultValue = "0") int page) {
        return service.hotels(query, status, page);
    }

    @GetMapping("/room-types/page")
    public DirectoryPage<RoomTypeResponse> rooms(@RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page) {
        return service.rooms(query, page);
    }

    @GetMapping("/contracts/page")
    public DirectoryPage<ContractResponse> contracts(@RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "status", defaultValue = "") String status, @RequestParam(name = "page", defaultValue = "0") int page) {
        return service.contracts(query, status, page);
    }

    @GetMapping("/offerings/page")
    public DirectoryPage<OfferingDirectoryResponse> offerings(@RequestParam Map<String, String> filters,
            @RequestParam(name = "page", defaultValue = "0") int page) {
        return service.offerings(filters, page);
    }
}
