package com.hiveapp.platform.client.plan.dto;

import java.util.List;
import java.util.Set;

public record SubscriptionOverrides(
    Set<String> addedFeatures,
    Set<String> addedModules
) {
    public static SubscriptionOverrides empty() {
        return new SubscriptionOverrides(Set.of(), Set.of());
    }
}
