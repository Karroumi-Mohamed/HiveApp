package com.hiveapp.platform.client.plan.dto;

import java.math.BigDecimal;
import java.util.List;

public record SubscriptionEntitlementSnapshot(
        String planCode,
        BigDecimal basePrice,
        List<SubscriptionFeatureSnapshot> features
) {
    public static SubscriptionEntitlementSnapshot empty(String planCode, BigDecimal basePrice) {
        return new SubscriptionEntitlementSnapshot(planCode, basePrice, List.of());
    }
}
