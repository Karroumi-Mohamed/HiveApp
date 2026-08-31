package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialProductAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialTargetingMode;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuotaPackageOperationalListItemDto(
        UUID id,
        String code,
        String name,
        String featureCode,
        String resource,
        QuotaPackageStatus status,
        UUID lineageId,
        int revisionNumber,
        UUID sourceQuotaPackageId,
        QuotaPackageCreationReason creationReason,
        ProductSalesVisibility salesVisibility,
        long version,
        Instant createdAt,
        Instant updatedAt,
        long applicablePriceCount,
        long draftPriceCount,
        long publishedPriceCount,
        CommercialTargetingMode targetingMode,
        int targetPlanCount,
        int targetAddOnCount,
        List<CommercialProductAction> availableActions,
        List<CommercialProductBlocker> blockers
) {
    public QuotaPackageOperationalListItemDto {
        availableActions = List.copyOf(availableActions);
        blockers = List.copyOf(blockers);
    }
}
