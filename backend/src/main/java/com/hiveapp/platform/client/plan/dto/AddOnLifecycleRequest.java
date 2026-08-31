package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.AddOnLifecycleAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AddOnLifecycleRequest(
        @NotNull AddOnLifecycleAction action,
        @NotNull @PositiveOrZero Long expectedVersion,
        @Size(max = 500) String reason,
        @Size(max = 2048) String activationPreviewToken
) {
    public AddOnLifecycleRequest {
        reason = reason == null ? null : reason.trim();
        activationPreviewToken = activationPreviewToken == null
                ? null : activationPreviewToken.trim();
    }
}
