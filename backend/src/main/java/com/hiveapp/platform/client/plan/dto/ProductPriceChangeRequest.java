package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.money.ExactDecimal;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The reviewed intent. Currency, cycle and owner are inherited, never editable here. */
public record ProductPriceChangeRequest(
        @NotNull Operation operation,
        @PositiveOrZero long currentVersion,
        UUID scheduledPriceId,
        Long scheduledVersion,
        Timing timing,
        @ExactDecimal @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        Instant effectiveFrom,
        @NotBlank @Size(max = 500) String reason) {
    public enum Operation {
        CHANGE,
        RESCHEDULE,
        CANCEL
    }

    public enum Timing {
        NOW,
        SCHEDULED
    }

    public ProductPriceChangeRequest {
        reason = reason == null ? null : reason.trim();
        effectiveFrom =
                effectiveFrom == null
                        ? null
                        : effectiveFrom.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
