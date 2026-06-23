package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminSubscriptionDto(
        UUID id,
        UUID accountId,
        String accountName,
        String planCode,
        String planName,
        SubscriptionStatus status,
        BigDecimal currentPrice,
        LocalDateTime currentPeriodEnd,
        SubscriptionOverrides customOverrides,
        SubscriptionEntitlementSnapshot entitlementSnapshot
) {}
