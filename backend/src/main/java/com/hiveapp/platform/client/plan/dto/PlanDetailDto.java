package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlanDetailDto(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal price,
        BillingCycle billingCycle,
        boolean isActive,
        int featureCount,
        int quotaConfiguredFeatureCount,
        long activeSubscriberCount,
        long trialingSubscriberCount,
        long currentSubscriberCount,
        long historicalSubscriberCount,
        BigDecimal currentRecurringPrice,
        List<String> warnings
) {}
