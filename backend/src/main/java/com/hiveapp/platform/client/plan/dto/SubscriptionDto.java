package com.hiveapp.platform.client.plan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;

public record SubscriptionDto(
        UUID id,
        PlanSummaryDto plan,
        SubscriptionStatus status,
        BigDecimal currentPrice,
        String currentPriceCurrencyCode,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd
) {
    public record PlanSummaryDto(String code, String name, BigDecimal basePrice, String currencyCode) {}
}
