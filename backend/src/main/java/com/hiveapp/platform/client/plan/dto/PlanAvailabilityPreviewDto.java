package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAvailabilityAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAvailabilityBlocker;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PlanAvailabilityPreviewDto(
        UUID planId,
        String planCode,
        long expectedVersion,
        PlanExtensionPolicy currentExtensionPolicy,
        PlanExtensionPolicy targetExtensionPolicy,
        ProductSalesVisibility currentSalesVisibility,
        ProductSalesVisibility targetSalesVisibility,
        long currentSubscriberCount,
        int totalExtensions,
        int operatorSelectableBefore,
        int operatorSelectableAfter,
        int clientVisibleBefore,
        int clientVisibleAfter,
        int changedCount,
        boolean changesTruncated,
        List<ExtensionCompatibilityDto> changedExtensions,
        boolean applicable,
        List<CommercialAvailabilityBlocker> blockers,
        Set<CommercialAvailabilityAction> availableActions,
        String previewToken
) {
    public PlanAvailabilityPreviewDto {
        changedExtensions = List.copyOf(changedExtensions == null ? List.of() : changedExtensions);
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
        availableActions = Set.copyOf(availableActions == null ? Set.of() : availableActions);
    }
}
