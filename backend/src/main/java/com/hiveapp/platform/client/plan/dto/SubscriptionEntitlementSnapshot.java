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
        List<SubscriptionAddOnSnapshot> addOns
) {
    public static SubscriptionEntitlementSnapshot empty(
            String planCode, BigDecimal basePrice, String currencyCode, BillingCycle billingCycle) {
        return new SubscriptionEntitlementSnapshot(
                planCode, basePrice, currencyCode, billingCycle, List.of(), List.of());
    }
}
