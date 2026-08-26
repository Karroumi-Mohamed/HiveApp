package com.hiveapp.platform.client.plan.dto;

import java.util.List;
import java.util.UUID;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceBlocker;

public record ProductPriceActivationPreview(
        UUID priceEntryId,
        boolean activatable,
        List<ProductPriceBlocker> blockers
) {
    public ProductPriceActivationPreview {
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
    }
}
