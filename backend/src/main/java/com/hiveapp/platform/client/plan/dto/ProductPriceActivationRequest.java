package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Applies an activation only against the exact signed assessment the operator reviewed. */
public record ProductPriceActivationRequest(
        @PositiveOrZero long version,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 2048) String activationPreviewToken
) {
    public ProductPriceActivationRequest {
        reason = reason == null ? null : reason.trim();
        activationPreviewToken = activationPreviewToken == null
                ? null : activationPreviewToken.trim();
    }
}
