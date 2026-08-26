package com.hiveapp.platform.client.plan.api;

import com.hiveapp.shared.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Map;

final class CommercialProductPageRequest {

    private CommercialProductPageRequest() {}

    static PageRequest of(
            int page,
            int size,
            String requestedSort,
            String requestedDirection,
            Map<String, String> sortable,
            String defaultSort,
            Sort.Direction defaultDirection
    ) {
        if (page < 0) {
            throw new InvalidRequestException("Page must be non-negative.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("Page size must be between 1 and 100.");
        }
        String sort = requestedSort == null || requestedSort.isBlank()
                ? defaultSort
                : requestedSort.trim();
        String property = sortable.get(sort);
        if (property == null) {
            throw new InvalidRequestException("Unsupported sort column: " + sort);
        }
        Sort.Direction direction = direction(requestedDirection, defaultDirection);
        Sort ordering = Sort.by(direction, property);
        if (!"id".equals(property)) {
            ordering = ordering.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(page, size, ordering);
    }

    private static Sort.Direction direction(String value, Sort.Direction fallback) {
        if (value == null || value.isBlank()) return fallback;
        if ("asc".equalsIgnoreCase(value)) return Sort.Direction.ASC;
        if ("desc".equalsIgnoreCase(value)) return Sort.Direction.DESC;
        throw new InvalidRequestException("Sort direction must be 'asc' or 'desc'.");
    }
}
