package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAvailabilityAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAvailabilityBlocker;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProductVisibilityPreviewDto(
        CommercialProductType productType,
        UUID productId,
        String productCode,
        long expectedVersion,
        long catalogRevision,
        String registryVersion,
        Instant evaluatedAt,
        Instant expiresAt,
        ProductSalesVisibility currentSalesVisibility,
        ProductSalesVisibility targetSalesVisibility,
        int compatiblePlanCount,
        int clientVisiblePlanCountBefore,
        int clientVisiblePlanCountAfter,
        boolean applicable,
        List<CommercialAvailabilityBlocker> blockers,
        Set<CommercialAvailabilityAction> availableActions,
        String previewToken
) {
    public ProductVisibilityPreviewDto {
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
        availableActions = Set.copyOf(availableActions == null ? Set.of() : availableActions);
    }
}
