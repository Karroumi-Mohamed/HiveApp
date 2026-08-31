package com.hiveapp.shared.quota;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One included limit for a feature-owned quota slot.
 *
 * resource      — matches a resource name declared in the Feature's QuotaSlot list.
 * mode          — explicit FINITE or UNLIMITED commercial promise.
 * limit         — required and non-negative for FINITE; absent for UNLIMITED.
 */
public record QuotaLimitEntry(
        @NotBlank @Size(max = 100) String resource,
        QuotaLimitMode mode,
        @PositiveOrZero Long limit
) {
    public QuotaLimitEntry {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("Quota resource is required");
        }
        if (mode == null) {
            mode = limit == null ? QuotaLimitMode.UNLIMITED : QuotaLimitMode.FINITE;
        }
        if (mode == QuotaLimitMode.FINITE && (limit == null || limit < 0)) {
            throw new IllegalArgumentException("Finite quota limit must be non-negative");
        }
        if (mode == QuotaLimitMode.UNLIMITED && limit != null) {
            throw new IllegalArgumentException("Unlimited quota cannot define a finite limit");
        }
    }

    public QuotaLimitEntry(String resource, Long limit) {
        this(resource, limit == null ? QuotaLimitMode.UNLIMITED : QuotaLimitMode.FINITE, limit);
    }

    public static QuotaLimitEntry unlimited(String resource) {
        return new QuotaLimitEntry(resource, QuotaLimitMode.UNLIMITED, null);
    }
}
