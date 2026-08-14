package com.hiveapp.platform.client.plan.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * API input shape kept separate from the invariant-enforcing persisted quota value.
 *
 * <p>The mode is required, never inferred: PLAN-FLOW-007 makes every quota decision explicit, so
 * "I sent a limit" is not the same statement as "this is FINITE", and an omitted mode is a
 * validation error rather than a guess.
 */
public record QuotaLimitRequest(
        @NotBlank @Size(max = 100) String resource,
        @NotNull QuotaLimitMode mode,
        @PositiveOrZero Long limit
) {
    @JsonIgnore
    @AssertTrue(message = "FINITE requires a limit and UNLIMITED must omit it")
    public boolean isModeAndLimitConsistent() {
        if (mode == null) {
            return true; // @NotNull reports the missing mode itself.
        }
        return (mode == QuotaLimitMode.FINITE && limit != null)
                || (mode == QuotaLimitMode.UNLIMITED && limit == null);
    }

    public QuotaLimitEntry toEntry() {
        return new QuotaLimitEntry(resource, mode, limit);
    }
}
