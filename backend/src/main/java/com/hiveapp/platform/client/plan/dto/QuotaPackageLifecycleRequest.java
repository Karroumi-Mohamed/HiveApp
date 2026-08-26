package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageLifecycleAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record QuotaPackageLifecycleRequest(
        @NotNull QuotaPackageLifecycleAction action,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 500) String reason,
        String activationPreviewToken
) {
    public QuotaPackageLifecycleRequest {
        reason = reason == null ? null : reason.trim();
        activationPreviewToken = activationPreviewToken == null
                ? null : activationPreviewToken.trim();
    }
}
