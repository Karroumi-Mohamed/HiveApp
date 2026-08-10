package com.hiveapp.platform.client.plan.service;

import java.util.OptionalLong;
import java.util.UUID;

/** Feature-owned measurement used before an entitlement or quota can be reduced. */
public interface SubscriptionImpactContributor {

    String featureCode();

    FeatureUsage featureUsage(UUID accountId);

    default OptionalLong quotaUsage(UUID accountId, String resource) {
        return OptionalLong.empty();
    }

    record FeatureUsage(long activeRecords, String explanation) {
        public FeatureUsage {
            if (activeRecords < 0) {
                throw new IllegalArgumentException("Feature usage cannot be negative");
            }
        }
    }
}
