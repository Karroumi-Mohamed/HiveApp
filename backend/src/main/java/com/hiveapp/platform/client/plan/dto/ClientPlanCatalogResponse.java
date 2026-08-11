package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.quota.QuotaLimitMode;
import com.hiveapp.shared.quota.QuotaSlot;

import java.math.BigDecimal;
import java.time.Instant;
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
            String currentPriceCurrencyCode,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages
    ) {}

    public record CatalogPlan(
            String code,
            String name,
            String description,
            BigDecimal basePrice,
            String currencyCode,
            BillingCycle billingCycle,
            boolean current,
            List<CatalogFeature> features,
            List<CatalogAddOn> addOns,
            List<CatalogQuotaPackage> quotaPackages
    ) {}

    public record CatalogFeature(
            String featureCode,
            String displayName,
            String description,
            PlanFeatureMode mode,
            List<CatalogQuota> quotas
    ) {}

    public record CatalogAddOn(
            String code,
            String name,
            String description,
            BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle,
            long definitionVersion,
            Set<String> dependencyCodes,
            Set<String> exclusionCodes,
            List<CatalogAddOnFeature> features
    ) {}

    public record CatalogAddOnFeature(
            String featureCode,
            String displayName,
            String description,
            List<CatalogQuota> quotas
    ) {}

    public record CatalogQuota(
            String featureCode,
            QuotaSlot slot,
            QuotaLimitMode mode,
            Long limit,
            boolean unlimited,
            Long currentUsage
    ) {}

    public record CatalogQuotaPackage(
            String code,
            String name,
            String description,
            long definitionVersion,
            String featureCode,
            String resource,
            long capacityPerUnit,
            BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle,
            boolean repeatable,
            int maximumQuantity,
            Set<String> allowedPlanCodes,
            Set<String> allowedAddOnCodes
    ) {}
}
