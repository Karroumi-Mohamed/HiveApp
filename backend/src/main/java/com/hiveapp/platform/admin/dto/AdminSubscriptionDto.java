package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;

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
        Instant pastDueAt,
        Instant graceEndsAt,
        Instant suspendedAt,
        SubscriptionSuspensionCause suspensionCause,
        String suspensionReason,
        Set<SubscriptionLifecycleAction> availableLifecycleActions,
        SubscriptionOverrides customOverrides,
        SubscriptionEntitlementSnapshot entitlementSnapshot
) {}
