package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QuotaPackagePriceDraftDto(
        UUID id,
        @ExactDecimal BigDecimal amount,
        String currencyCode,
        BillingCycle billingCycle,
        ProductPriceStatus status,
        Instant effectiveFrom,
        Instant effectiveUntil,
        UUID lineageId,
        int revisionNumber,
        long version,
        boolean compatibilityDefault
) {}
