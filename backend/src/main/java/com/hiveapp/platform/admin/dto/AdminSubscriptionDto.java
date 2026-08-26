package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminSubscriptionDto(
        UUID id,
        UUID accountId,
        String accountName,
        String planCode,
        String planName,
        SubscriptionStatus status,
        @ExactDecimal BigDecimal currentPrice,
        String currentPriceCurrencyCode,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        SubscriptionOverrides customOverrides,
        SubscriptionEntitlementSnapshot entitlementSnapshot
) {}
