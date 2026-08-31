package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceAction;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceBlocker;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductPriceDto(
        UUID id,
        ProductPriceOwnerType productType,
        UUID productId,
        String productCode,
        String productName,
        @ExactDecimal BigDecimal amount,
        String currencyCode,
        BillingCycle billingCycle,
        ProductPriceStatus status,
        Instant effectiveFrom,
        Instant effectiveUntil,
        UUID lineageId,
        int revisionNumber,
        UUID sourcePriceId,
        boolean compatibilityDefault,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<ProductPriceAction> availableActions,
        List<ProductPriceBlocker> blockers
) {
    public ProductPriceDto {
        availableActions = List.copyOf(availableActions == null ? List.of() : availableActions);
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
    }
}
