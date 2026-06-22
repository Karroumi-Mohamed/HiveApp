package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.quota.QuotaOverride;
import com.hiveapp.shared.quota.QuotaSlot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ClientPlanCatalogResponse(
        CurrentSubscription currentSubscription,
        List<CatalogPlan> plans
) {
    public record CurrentSubscription(
            UUID id,
            String planCode,
            SubscriptionStatus status,
            BigDecimal currentPrice,
            LocalDateTime currentPeriodEnd,
            Set<String> addOnFeatureCodes,
            List<QuotaOverride> quotaOverrides
    ) {}

    public record CatalogPlan(
            String code,
            String name,
            String description,
            BigDecimal basePrice,
            BillingCycle billingCycle,
            boolean current,
            List<CatalogFeature> features
    ) {}

    public record CatalogFeature(
            String featureCode,
            String displayName,
            String description,
            boolean included,
            boolean addOnAvailable,
            BigDecimal addOnPrice,
            List<CatalogQuota> quotas
    ) {}

    public record CatalogQuota(
            String resource,
            String unit,
            QuotaSlot slot,
            Long limit,
            boolean unlimited,
            BigDecimal pricePerUnit,
            Long currentUsage
    ) {}
}
