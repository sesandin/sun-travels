package com.suntravels.dto;

import java.util.List;
import java.util.Map;

public record DirectoryPage<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Map<String, Object> summary
) {
}
