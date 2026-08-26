package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.AddOnCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialTargetingMode;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AddOnOperationalListItemDto(
        UUID id,
        String code,
        String name,
        AddOnStatus status,
        UUID lineageId,
        int revisionNumber,
        UUID sourceAddOnId,
        AddOnCreationReason creationReason,
        ProductSalesVisibility salesVisibility,
        long version,
        Instant createdAt,
        Instant updatedAt,
        long featureCount,
        long applicablePriceCount,
        long draftPriceCount,
        long publishedPriceCount,
        CommercialTargetingMode targetingMode,
        int targetPlanCount,
        int blockedPlanCount,
        int dependencyCount,
        int exclusionCount,
        long referencedByAddOnCount,
        long referencedByQuotaPackageCount,
        List<CommercialProductAction> availableActions,
        List<CommercialProductBlocker> blockers
) {
    public AddOnOperationalListItemDto {
        availableActions = List.copyOf(availableActions);
        blockers = List.copyOf(blockers);
    }
}
