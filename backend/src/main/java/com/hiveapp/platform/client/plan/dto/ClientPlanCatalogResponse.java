package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.quota.QuotaLimitMode;
import com.hiveapp.shared.quota.QuotaSlot;
import com.hiveapp.shared.money.ExactDecimal;

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
            @ExactDecimal BigDecimal currentPrice,
            String currentPriceCurrencyCode,
            UUID planPriceEntryId,
            BillingCycle billingCycle,
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
            @ExactDecimal BigDecimal basePrice,
            String currencyCode,
            BillingCycle billingCycle,
            boolean current,
            List<CatalogFeature> features,
            List<CatalogAddOn> addOns,
            List<CatalogQuotaPackage> quotaPackages,
            List<CatalogPrice> prices
    ) {
        public CatalogPlan(
                String code, String name, String description, BigDecimal basePrice,
                String currencyCode, BillingCycle billingCycle, boolean current,
                List<CatalogFeature> features, List<CatalogAddOn> addOns,
                List<CatalogQuotaPackage> quotaPackages
        ) {
            this(code, name, description, basePrice, currencyCode, billingCycle, current,
                    features, addOns, quotaPackages, List.of());
        }
    }

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
            @ExactDecimal BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle,
            long definitionVersion,
            Set<String> dependencyCodes,
            Set<String> exclusionCodes,
            List<CatalogAddOnFeature> features,
            List<CatalogPrice> prices
    ) {
        public CatalogAddOn(
                String code, String name, String description, BigDecimal price,
                String currencyCode, BillingCycle billingCycle, long definitionVersion,
                Set<String> dependencyCodes, Set<String> exclusionCodes,
                List<CatalogAddOnFeature> features
        ) {
            this(code, name, description, price, currencyCode, billingCycle, definitionVersion,
                    dependencyCodes, exclusionCodes, features, List.of());
        }
    }

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
            @ExactDecimal BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle,
            boolean repeatable,
            int maximumQuantity,
            Set<String> allowedPlanCodes,
            Set<String> allowedAddOnCodes,
            List<CatalogPrice> prices
    ) {
        public CatalogQuotaPackage(
                String code, String name, String description, long definitionVersion,
                String featureCode, String resource, long capacityPerUnit, BigDecimal price,
                String currencyCode, BillingCycle billingCycle, boolean repeatable,
                int maximumQuantity, Set<String> allowedPlanCodes, Set<String> allowedAddOnCodes
        ) {
            this(code, name, description, definitionVersion, featureCode, resource, capacityPerUnit,
                    price, currencyCode, billingCycle, repeatable, maximumQuantity,
                    allowedPlanCodes, allowedAddOnCodes, List.of());
        }
    }

    public record CatalogPrice(
            UUID priceEntryId,
            @ExactDecimal BigDecimal amount,
            String currencyCode,
            BillingCycle billingCycle,
            Instant effectiveFrom,
            Instant effectiveUntil
    ) {}
}
