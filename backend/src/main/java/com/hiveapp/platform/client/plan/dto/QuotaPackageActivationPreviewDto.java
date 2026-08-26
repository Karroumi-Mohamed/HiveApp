package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageActivationBlocker;

import java.util.List;
import java.util.UUID;

public record QuotaPackageActivationPreviewDto(
        UUID quotaPackageId,
        long expectedVersion,
        String previewToken,
        boolean activatable,
        List<QuotaPackageActivationBlocker> blockers,
        List<QuotaPackagePriceDraftDto> reviewedPrices,
        List<UUID> packagesToDeactivate
) {
    public QuotaPackageActivationPreviewDto {
        blockers = List.copyOf(blockers);
        reviewedPrices = List.copyOf(reviewedPrices);
        packagesToDeactivate = List.copyOf(packagesToDeactivate);
    }
}
