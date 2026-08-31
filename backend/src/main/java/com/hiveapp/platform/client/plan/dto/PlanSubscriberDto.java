package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlanSubscriberDto(
        UUID subscriptionId,
        UUID accountId,
        String accountName,
        String planCode,
        SubscriptionStatus status,
        @ExactDecimal BigDecimal configuredRecurringPrice,
        String configuredRecurringPriceCurrencyCode,
        Instant currentPeriodEnd
) {}
