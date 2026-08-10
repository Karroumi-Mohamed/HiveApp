package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import java.math.BigDecimal;
import java.util.List;

public record SubscriptionEntitlementSnapshot(
        String planCode,
        BigDecimal basePrice,
        String currencyCode,
        BillingCycle billingCycle,
        List<SubscriptionFeatureSnapshot> features,
        List<SubscriptionAddOnSnapshot> addOns,
        List<SubscriptionQuotaPackageSnapshot> quotaPackages
) {
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
}
