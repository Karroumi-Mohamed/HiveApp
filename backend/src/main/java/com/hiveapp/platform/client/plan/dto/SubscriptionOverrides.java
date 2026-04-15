package com.hiveapp.platform.client.plan.dto;

import java.util.Set;

public record SubscriptionOverrides(
    Set<String> addedFeatures
) {
    public static SubscriptionOverrides empty() {
        return new SubscriptionOverrides(Set.of());
    }
}
