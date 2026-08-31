package com.hiveapp.platform.client.plan.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SubscriptionImpactRegistry {

    private final Map<String, SubscriptionImpactContributor> contributors;

    public SubscriptionImpactRegistry(List<SubscriptionImpactContributor> contributors) {
        this.contributors = contributors.stream().collect(Collectors.toUnmodifiableMap(
                SubscriptionImpactContributor::featureCode,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException(
                            "Duplicate subscription impact contributor for " + left.featureCode());
                }));
    }

    public Optional<SubscriptionImpactContributor.FeatureUsage> featureUsage(UUID accountId, String featureCode) {
        return Optional.ofNullable(contributors.get(featureCode))
                .map(contributor -> contributor.featureUsage(accountId));
    }

    public OptionalLong quotaUsage(UUID accountId, String featureCode, String resource) {
        SubscriptionImpactContributor contributor = contributors.get(featureCode);
        return contributor == null ? OptionalLong.empty() : contributor.quotaUsage(accountId, resource);
    }
}
