package com.hiveapp.platform.client.plan.dto;

import java.math.BigDecimal;
import java.util.List;

public record SubscriptionEntitlementSnapshot(
        String planCode,
        BigDecimal basePrice,
        String currencyCode,
        List<SubscriptionFeatureSnapshot> features
) {
    public static SubscriptionEntitlementSnapshot empty(String planCode, BigDecimal basePrice, String currencyCode) {
        return new SubscriptionEntitlementSnapshot(planCode, basePrice, currencyCode, List.of());
    }
}
