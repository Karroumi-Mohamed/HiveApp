package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanEntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PermissionRepository permissionRepository;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;

    @Transactional(readOnly = true)
    public boolean isPermissionEntitled(UUID accountId, String permissionCode) {
        Optional<Subscription> subscription = currentSubscription(accountId);
        if (subscription.isEmpty() || isExpired(subscription.get().getCurrentPeriodEnd())) {
            return false;
        }

        var sub = subscription.get();
        var permission = permissionRepository.findByCode(permissionCode).orElse(null);
        if (permission == null || permission.getFeature() == null) {
            return false;
        }

        String featureCode = permission.getFeature().getCode();
        if (snapshotEntitles(sub, featureCode)) {
            return true;
        }

        if (sub.getCustomOverrides() == null) {
            return false;
        }

        try {
            var overrides = subscriptionOverrideReader.read(sub.getCustomOverrides());
            return overrides.addedFeatures() != null
                    && overrides.addedFeatures().contains(featureCode);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Resolves the account entitlement once for catalog and picker construction. */
    @Transactional(readOnly = true)
    public Set<String> entitledFeatureCodes(UUID accountId) {
        Optional<Subscription> subscription = currentSubscription(accountId);
        if (subscription.isEmpty() || isExpired(subscription.get().getCurrentPeriodEnd())) {
            return Set.of();
        }
        Subscription current = subscription.get();
        Set<String> features = subscriptionSnapshotReader.read(current.getEntitlementSnapshot())
                .map(snapshot -> snapshot.features() == null ? new HashSet<String>() : snapshot.features().stream()
                        .map(feature -> feature.featureCode())
                        .collect(Collectors.toCollection(HashSet::new)))
                .orElseGet(() -> planFeatureRepository.findAllByPlanId(current.getPlan().getId()).stream()
                        .map(planFeature -> planFeature.getFeature().getCode())
                        .collect(Collectors.toCollection(HashSet::new)));
        if (current.getCustomOverrides() != null) {
            try {
                var overrides = subscriptionOverrideReader.read(current.getCustomOverrides());
                if (overrides.addedFeatures() != null) {
                    features.addAll(overrides.addedFeatures());
                }
            } catch (RuntimeException ignoredInvalidLegacyOverride) {
                // Invalid legacy overrides grant nothing.
            }
        }
        return Set.copyOf(features);
    }

    private Optional<Subscription> currentSubscription(UUID accountId) {
        Optional<Subscription> subscription = subscriptionRepository.findActiveByAccountId(accountId);
        return subscription.isPresent()
                ? subscription
                : subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING);
    }

    private boolean snapshotEntitles(Subscription subscription, String featureCode) {
        return subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot())
                .map(snapshot -> hasFeature(snapshot, featureCode))
                .orElseGet(() -> planFeatureRepository
                        .findByPlanIdAndFeature_Code(subscription.getPlan().getId(), featureCode)
                        .isPresent());
    }

    private boolean hasFeature(SubscriptionEntitlementSnapshot snapshot, String featureCode) {
        return snapshot.features() != null
                && snapshot.features().stream().anyMatch(feature -> featureCode.equals(feature.featureCode()));
    }

    private boolean isExpired(LocalDateTime currentPeriodEnd) {
        return currentPeriodEnd != null && !currentPeriodEnd.isAfter(LocalDateTime.now());
    }
}
