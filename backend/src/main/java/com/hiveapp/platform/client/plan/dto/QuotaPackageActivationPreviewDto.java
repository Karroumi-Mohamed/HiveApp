package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageActivationBlocker;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record QuotaPackageActivationPreviewDto(
        UUID quotaPackageId,
        long expectedVersion,
        long catalogRevision,
        String registryVersion,
        Instant evaluatedAt,
        Instant expiresAt,
        String previewToken,
        boolean activatable,
        List<QuotaPackageActivationBlocker> blockers,
        List<ProductActivationPriceDto> reviewedPrices,
        List<UUID> packagesToDeactivate
) {
    public QuotaPackageActivationPreviewDto {
        blockers = List.copyOf(blockers);
        reviewedPrices = List.copyOf(reviewedPrices);
        packagesToDeactivate = List.copyOf(packagesToDeactivate);
    }
}
