package com.hiveapp.platform.client.plan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause;
import com.hiveapp.shared.money.ExactDecimal;

public record SubscriptionDto(
        UUID id,
        PlanSummaryDto plan,
        SubscriptionStatus status,
        @ExactDecimal BigDecimal currentPrice,
        String currentPriceCurrencyCode,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant pastDueAt,
        Instant graceEndsAt,
        Instant suspendedAt,
        SubscriptionSuspensionCause suspensionCause
) {
    public record PlanSummaryDto(
            String code,
            String name,
            @ExactDecimal BigDecimal basePrice,
            String currencyCode
    ) {}
}
