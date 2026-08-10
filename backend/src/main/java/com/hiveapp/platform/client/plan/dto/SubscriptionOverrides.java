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
        int schemaVersion,
        Set<String> addOnCodes,
        List<QuotaPackageSelection> quotaPackages
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public SubscriptionOverrides {
        schemaVersion = schemaVersion == 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported subscription override schema version: " + schemaVersion);
        }
        addOnCodes = addOnCodes == null ? Set.of() : Set.copyOf(addOnCodes);
        quotaPackages = quotaPackages == null ? List.of() : List.copyOf(quotaPackages);
    }

    public SubscriptionOverrides(Set<String> addOnCodes, List<QuotaPackageSelection> quotaPackages) {
        this(CURRENT_SCHEMA_VERSION, addOnCodes, quotaPackages);
    }

    public static SubscriptionOverrides empty() {
        return new SubscriptionOverrides(Set.of(), List.of());
    }
}
