package com.suntravels.dto;

import java.util.List;

public record CustomerPageResponse(
        List<CustomerResponse> customers,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
