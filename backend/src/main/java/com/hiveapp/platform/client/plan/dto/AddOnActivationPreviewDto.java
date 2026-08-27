package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.AddOnActivationBlocker;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AddOnActivationPreviewDto(
        UUID addOnId,
        long expectedVersion,
        long catalogRevision,
        Instant evaluatedAt,
        Instant expiresAt,
        String previewToken,
        boolean activatable,
        List<AddOnActivationBlocker> blockers,
        long featureCount,
        long evaluatedPlanCount,
        long compatiblePlanCount,
        long explicitTargetPlanCount,
        List<ProductActivationPriceDto> reviewedPrices,
        List<UUID> addOnsToDeactivate
) {
    public AddOnActivationPreviewDto {
        blockers = List.copyOf(blockers);
        reviewedPrices = List.copyOf(reviewedPrices);
        addOnsToDeactivate = List.copyOf(addOnsToDeactivate);
    }
}
