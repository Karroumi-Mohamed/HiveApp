package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.ProductPriceReplacementBlocker;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductPriceReplacementPreview(
        UUID currentPriceId,
        long currentVersion,
        UUID successorPriceId,
        long successorVersion,
        Instant cutoff,
        boolean schedulable,
        List<ProductPriceReplacementBlocker> blockers
) {
    public ProductPriceReplacementPreview {
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
    }
}
