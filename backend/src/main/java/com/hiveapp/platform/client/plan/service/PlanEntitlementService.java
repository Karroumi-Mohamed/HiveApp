package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
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
    private final AddOnRepository addOnRepository;
    private final AddOnFeatureRepository addOnFeatureRepository;

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
        return snapshotEntitles(sub, featureCode);
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
                        .filter(planFeature -> planFeature.getMode() == PlanFeatureMode.INCLUDED)
                        .map(planFeature -> planFeature.getFeature().getCode())
                        .collect(Collectors.toCollection(HashSet::new)));
        if (current.getEntitlementSnapshot() == null && current.getCustomOverrides() != null) {
            try {
                var overrides = subscriptionOverrideReader.read(current.getCustomOverrides());
                if (overrides.addOnCodes() != null) {
                    addOnRepository.findAllByCodeIn(overrides.addOnCodes()).forEach(addOn ->
                            addOnFeatureRepository.findAllByAddOnId(addOn.getId()).forEach(addOnFeature ->
                                    features.add(addOnFeature.getFeature().getCode())));
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
                        .filter(planFeature -> planFeature.getMode() == PlanFeatureMode.INCLUDED)
                        .isPresent()
                        || legacyAddOnEntitles(subscription, featureCode));
    }

    private boolean legacyAddOnEntitles(Subscription subscription, String featureCode) {
        if (subscription.getCustomOverrides() == null) {
            return false;
        }
        try {
            var overrides = subscriptionOverrideReader.read(subscription.getCustomOverrides());
            return overrides.addOnCodes() != null
                    && addOnRepository.findAllByCodeIn(overrides.addOnCodes()).stream()
                    .flatMap(addOn -> addOnFeatureRepository.findAllByAddOnId(addOn.getId()).stream())
                    .anyMatch(addOnFeature -> featureCode.equals(addOnFeature.getFeature().getCode()));
        } catch (RuntimeException ignoredInvalidLegacyOverride) {
            return false;
        }
    }

    private boolean hasFeature(SubscriptionEntitlementSnapshot snapshot, String featureCode) {
        return snapshot.features() != null
                && snapshot.features().stream().anyMatch(feature -> featureCode.equals(feature.featureCode()));
    }

    private boolean isExpired(LocalDateTime currentPeriodEnd) {
        return currentPeriodEnd != null && !currentPeriodEnd.isAfter(LocalDateTime.now());
    }
}
