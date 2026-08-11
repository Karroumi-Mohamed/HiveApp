package com.hiveapp.platform.client.plan.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** API input shape kept separate from the invariant-enforcing persisted quota value. */
public record QuotaLimitRequest(
        @NotBlank @Size(max = 100) String resource,
        QuotaLimitMode mode,
        @PositiveOrZero Long limit
) {
    @JsonIgnore
    @AssertTrue(message = "FINITE requires a limit and UNLIMITED must omit it")
    public boolean isModeAndLimitConsistent() {
        QuotaLimitMode resolved = effectiveMode();
        return (resolved == QuotaLimitMode.FINITE && limit != null)
                || (resolved == QuotaLimitMode.UNLIMITED && limit == null);
    }

    public QuotaLimitEntry toEntry() {
        return new QuotaLimitEntry(resource, effectiveMode(), limit);
    }

    private QuotaLimitMode effectiveMode() {
        return mode == null
                ? (limit == null ? QuotaLimitMode.UNLIMITED : QuotaLimitMode.FINITE)
                : mode;
    }
}
