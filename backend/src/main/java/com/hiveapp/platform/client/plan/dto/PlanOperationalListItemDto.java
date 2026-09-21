package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialProductAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductBlocker;
import com.hiveapp.platform.client.plan.domain.constant.PlanCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanOperationalListItemDto(
        UUID id,
        String code,
        String name,
        PlanStatus status,
        UUID lineageId,
        int revisionNumber,
        UUID sourcePlanId,
        PlanCreationReason creationReason,
        PlanExtensionPolicy extensionPolicy,
        ProductSalesVisibility salesVisibility,
        long version,
        Instant createdAt,
        Instant updatedAt,
        long featureCount,
        long includedFeatureCount,
        long applicablePriceCount,
        long draftPriceCount,
        long publishedPriceCount,
        long currentSubscriberCount,
        long affectedSubscriptionCount,
        List<CommercialProductAction> availableActions,
        List<CommercialProductBlocker> blockers
) {
    @com.fasterxml.jackson.annotation.JsonProperty("productVersionNumber")
    public int productVersionNumber() { return revisionNumber; }

    public PlanOperationalListItemDto {
        availableActions = List.copyOf(availableActions);
        blockers = List.copyOf(blockers);
    }
}
