package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import jakarta.validation.constraints.NotNull;

public record PlanAvailabilityPreviewRequest(
        @NotNull PlanExtensionPolicy extensionPolicy,
        @NotNull ProductSalesVisibility salesVisibility
) {}
