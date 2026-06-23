package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PlanSubscriberDto(
        UUID subscriptionId,
        UUID accountId,
        String accountName,
        String planCode,
        SubscriptionStatus status,
        BigDecimal currentPrice,
        LocalDateTime currentPeriodEnd
) {}
