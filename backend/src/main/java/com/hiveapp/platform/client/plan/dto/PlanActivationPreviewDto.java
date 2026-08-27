package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.PlanActivationBlocker;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanActivationPreviewDto(
        UUID planId,
        long expectedVersion,
        long catalogRevision,
        Instant evaluatedAt,
        Instant expiresAt,
        String previewToken,
        boolean activatable,
        List<PlanActivationBlocker> blockers,
        long includedFeatureCount,
        long optionalAddOnFeatureCount,
        long blockedFeatureCount,
        List<ProductActivationPriceDto> reviewedPrices
) {
    public PlanActivationPreviewDto {
        blockers = List.copyOf(blockers);
        reviewedPrices = List.copyOf(reviewedPrices);
    }
}
