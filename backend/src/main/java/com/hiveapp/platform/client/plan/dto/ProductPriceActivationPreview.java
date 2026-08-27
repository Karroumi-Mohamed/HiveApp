package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.ProductPriceBlocker;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductPriceActivationPreview(
        UUID priceEntryId,
        long expectedVersion,
        long catalogRevision,
        String registryVersion,
        Instant evaluatedAt,
        Instant expiresAt,
        String previewToken,
        boolean activatable,
        List<ProductPriceBlocker> blockers
) {
    public ProductPriceActivationPreview {
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
    }
}
