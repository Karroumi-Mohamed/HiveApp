package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SubscriptionEntitlementSnapshot(
        int schemaVersion,
        String planCode,
        String planName,
        long planDefinitionVersion,
        BigDecimal basePrice,
        String currencyCode,
        BillingCycle billingCycle,
        Instant effectiveFrom,
        Instant effectiveUntil,
        List<SubscriptionFeatureSnapshot> features,
        List<SubscriptionAddOnSnapshot> addOns,
        List<SubscriptionQuotaPackageSnapshot> quotaPackages
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public SubscriptionEntitlementSnapshot {
        schemaVersion = schemaVersion == 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported subscription snapshot schema version: " + schemaVersion);
        }
        if (effectiveFrom != null && effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("Subscription snapshot effectiveUntil must be after effectiveFrom");
        }
        features = features == null ? List.of() : List.copyOf(features);
        addOns = addOns == null ? List.of() : List.copyOf(addOns);
        quotaPackages = quotaPackages == null ? List.of() : List.copyOf(quotaPackages);
    }

    public SubscriptionEntitlementSnapshot(
            String planCode,
            BigDecimal basePrice,
            String currencyCode,
            BillingCycle billingCycle,
            List<SubscriptionFeatureSnapshot> features,
            List<SubscriptionAddOnSnapshot> addOns,
            List<SubscriptionQuotaPackageSnapshot> quotaPackages
    ) {
        this(CURRENT_SCHEMA_VERSION, planCode, null, 0L, basePrice, currencyCode, billingCycle,
                null, null, features, addOns, quotaPackages);
    }

    public SubscriptionEntitlementSnapshot(
            String planCode,
            BigDecimal basePrice,
            String currencyCode,
            BillingCycle billingCycle,
            List<SubscriptionFeatureSnapshot> features,
            List<SubscriptionAddOnSnapshot> addOns
    ) {
        this(planCode, basePrice, currencyCode, billingCycle, features, addOns, List.of());
    }

    public static SubscriptionEntitlementSnapshot empty(
            String planCode, BigDecimal basePrice, String currencyCode, BillingCycle billingCycle) {
        return new SubscriptionEntitlementSnapshot(
                planCode, basePrice, currencyCode, billingCycle, List.of(), List.of(), List.of());
    }

    public SubscriptionEntitlementSnapshot withEffectivePeriod(Instant from, Instant until) {
        return new SubscriptionEntitlementSnapshot(
                schemaVersion, planCode, planName, planDefinitionVersion, basePrice, currencyCode, billingCycle,
                from, until, features, addOns, quotaPackages);
    }
}
