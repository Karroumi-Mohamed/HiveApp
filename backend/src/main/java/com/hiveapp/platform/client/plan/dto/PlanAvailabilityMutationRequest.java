package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PlanAvailabilityMutationRequest(
        @PositiveOrZero long expectedVersion,
        @NotNull PlanExtensionPolicy extensionPolicy,
        @NotNull ProductSalesVisibility salesVisibility,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 2048) String previewToken
) {}
