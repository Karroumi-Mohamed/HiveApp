package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProductPriceReplacementRequest(
        @NotNull UUID currentPriceId,
        @PositiveOrZero long currentVersion,
        @PositiveOrZero long successorVersion,
        @NotBlank @Size(max = 500) String reason
) {
    public ProductPriceReplacementRequest {
        reason = reason == null ? null : reason.trim();
    }
}
