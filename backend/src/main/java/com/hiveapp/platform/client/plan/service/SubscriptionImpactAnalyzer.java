package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.EffectiveQuotaLimit;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeConflict;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot;
import com.hiveapp.shared.quota.QuotaLimitMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.OptionalLong;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SubscriptionImpactAnalyzer {

    private final SubscriptionImpactRegistry impactRegistry;
    private final SubscriptionSnapshotReader snapshotReader;

    public List<SubscriptionChangeConflict> analyze(
            UUID accountId,
            Subscription current,
            SubscriptionEntitlementSnapshot targetSnapshot
    ) {
        SubscriptionEntitlementSnapshot currentSnapshot = snapshotReader.read(current.getEntitlementSnapshot())
                .orElseThrow(() -> new IllegalStateException("Current subscription snapshot is required"));
        Set<String> currentFeatures = currentSnapshot.features().stream()
                .map(feature -> feature.featureCode())
                .collect(Collectors.toSet());
        Set<String> targetFeatures = targetSnapshot.features().stream()
                .map(feature -> feature.featureCode())
                .collect(Collectors.toSet());
        Map<String, EffectiveQuotaLimit> currentLimits = effectiveQuotaLimits(currentSnapshot).stream()
                .collect(Collectors.toMap(
                        limit -> limit.featureCode() + ":" + limit.resource(),
                        java.util.function.Function.identity()));
        List<SubscriptionChangeConflict> conflicts = new ArrayList<>();

        currentFeatures.stream()
                .filter(featureCode -> !targetFeatures.contains(featureCode))
                .sorted()
                .forEach(featureCode -> {
                    var usage = impactRegistry.featureUsage(accountId, featureCode);
                    if (usage.isEmpty()) {
                        conflicts.add(new SubscriptionChangeConflict(
                                "IMPACT_UNKNOWN", featureCode, null, null, null,
                                "Feature " + featureCode
                                        + " has no impact contributor; destructive change is blocked."));
                    } else if (usage.orElseThrow().activeRecords() > 0) {
                        conflicts.add(new SubscriptionChangeConflict(
                                "FEATURE_IN_USE", featureCode, null,
                                usage.orElseThrow().activeRecords(), null,
                                usage.orElseThrow().explanation()));
                    }
                });

        for (EffectiveQuotaLimit limit : effectiveQuotaLimits(targetSnapshot)) {
            if (limit.mode() == QuotaLimitMode.UNLIMITED) {
                continue;
            }
            EffectiveQuotaLimit currentLimit = currentLimits.get(
                    limit.featureCode() + ":" + limit.resource());
            boolean reduction = currentLimit != null
                    && (currentLimit.mode() == QuotaLimitMode.UNLIMITED
                    || currentLimit.effectiveLimit() > limit.effectiveLimit());
            if (!reduction) {
                continue;
            }
            var measuredUsage = impactRegistry.quotaUsage(
                    accountId, limit.featureCode(), limit.resource());
            if (measuredUsage.isEmpty()) {
                conflicts.add(new SubscriptionChangeConflict(
                        "QUOTA_USAGE_UNKNOWN", limit.featureCode(), limit.resource(), null,
                        limit.effectiveLimit(),
                        "No usage contributor exists for " + limit.featureCode() + "." + limit.resource()
                                + "; quota reduction is blocked."));
                continue;
            }
            long usage = measuredUsage.getAsLong();
            if (usage > limit.effectiveLimit()) {
                conflicts.add(new SubscriptionChangeConflict(
                        "QUOTA_BELOW_USAGE", limit.featureCode(), limit.resource(), usage,
                        limit.effectiveLimit(),
                        "Current usage for " + limit.featureCode() + "." + limit.resource()
                                + " is " + usage + ", above requested limit "
                                + limit.effectiveLimit() + "."));
            }
        }
        return List.copyOf(conflicts);
    }

    public List<EffectiveQuotaLimit> effectiveQuotaLimits(SubscriptionEntitlementSnapshot snapshot) {
        Map<String, Long> purchasedByQuota = snapshot.quotaPackages().stream()
                .collect(Collectors.toMap(
                        item -> item.featureCode() + ":" + item.resource(),
                        SubscriptionQuotaPackageSnapshot::purchasedCapacity,
                        Math::addExact));
        List<EffectiveQuotaLimit> limits = new ArrayList<>();
        for (var feature : snapshot.features()) {
            for (var base : feature.quotaConfigs()) {
                long purchased = purchasedByQuota.getOrDefault(
                        feature.featureCode() + ":" + base.resource(), 0L);
                Long effective = base.mode() == QuotaLimitMode.UNLIMITED
                        ? null
                        : Math.addExact(base.limit(), purchased);
                limits.add(new EffectiveQuotaLimit(
                        feature.featureCode(), base.resource(), base.mode(), base.limit(), purchased, effective));
            }
        }
        return List.copyOf(limits);
    }

    public OptionalLong quotaUsage(UUID accountId, String featureCode, String resource) {
        return impactRegistry.quotaUsage(accountId, featureCode, resource);
    }
}
