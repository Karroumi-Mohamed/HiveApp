package com.hiveapp.platform.client.plan.dto;

import java.util.List;
import java.util.Set;

/**
 * Snapshot stored in Subscription.custom_overrides JSONB.
 *
 * addOnCodes     — selected first-class AddOn products beyond the base plan template.
 * quotaPackages  — predefined, versioned capacity products selected by code and quantity.
 */
public record SubscriptionOverrides(
        Set<String> addOnCodes,
        List<QuotaPackageSelection> quotaPackages
) {
    public static SubscriptionOverrides empty() {
        return new SubscriptionOverrides(Set.of(), List.of());
    }
}
