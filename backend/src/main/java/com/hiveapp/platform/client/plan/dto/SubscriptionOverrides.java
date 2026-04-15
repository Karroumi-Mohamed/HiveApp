package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaOverride;

import java.util.List;
import java.util.Set;

/**
 * Snapshot stored in Subscription.custom_overrides JSONB.
 *
 * addedFeatures  — feature codes unlocked beyond the base plan template.
 * quotaOverrides — per-slot limit bumps beyond the plan's default limits.
 */
public record SubscriptionOverrides(
        Set<String> addedFeatures,
        List<QuotaOverride> quotaOverrides
) {
    public static SubscriptionOverrides empty() {
        return new SubscriptionOverrides(Set.of(), List.of());
    }
}
