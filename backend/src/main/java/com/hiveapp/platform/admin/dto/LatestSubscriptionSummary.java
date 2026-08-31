package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LatestSubscriptionSummary(
        UUID id,
        SubscriptionStatus status,
        UUID planId,
        String planCode,
        String planName,
        int planRevisionNumber,
        BillingCycle billingCycle,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        BigDecimal currentPrice,
        String currencyCode
) {
}
