package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductPriceVersionRequest(
        @PositiveOrZero long version,
        @NotBlank @Size(max = 500) String reason
) {
    public ProductPriceVersionRequest {
        reason = reason == null ? null : reason.trim();
    }
}
