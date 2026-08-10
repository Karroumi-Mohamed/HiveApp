package com.hiveapp.shared.quota;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Enforces quota limits at the Feature/slot level.
 *
 * Resolves the included snapshot quota plus snapshotted quota-package capacity.
 * Legacy subscriptions without a snapshot fall back to the Plan's included limit.
 *
 * The LongSupplier is only called when a real limit exists (lazy evaluation).
 */
@Service
@RequiredArgsConstructor
public class QuotaEnforcer {

    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;

    public void check(FeatureDefinition feature, String slot, UUID accountId, LongSupplier currentUsage) {
        var subscription = subscriptionRepository
                .findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));

        var snapshot = subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot());
        var limitEntry = snapshot
                .flatMap(value -> resolveSnapshotLimit(value, feature.code(), slot))
                .or(() -> resolvePlanLimit(subscription, feature.code(), slot));

        if (limitEntry.isEmpty()) return;
        if (limitEntry.get().mode() == QuotaLimitMode.UNLIMITED) return;

        long purchasedCapacity = snapshot
                .map(value -> value.quotaPackages() == null ? 0L : value.quotaPackages().stream()
                        .filter(item -> feature.code().equals(item.featureCode()) && slot.equals(item.resource()))
                        .mapToLong(item -> item.purchasedCapacity())
                        .reduce(0L, Math::addExact))
                .orElse(0L);
        long effectiveLimit = Math.addExact(limitEntry.get().limit(), purchasedCapacity);

        long current = currentUsage.getAsLong();
        String unit = feature.quotaSlots().stream()
                .filter(s -> s.resource().equals(slot))
                .map(QuotaSlot::unit)
                .findFirst()
                .orElse(slot);

        if (current >= effectiveLimit) {
            throw new QuotaExceededException(slot, effectiveLimit, current, unit);
        }
    }

    private java.util.Optional<QuotaLimitEntry> resolveSnapshotLimit(
            com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot snapshot,
            String featureCode,
            String slot
    ) {
        return snapshot.features() == null
                ? java.util.Optional.empty()
                : snapshot.features().stream()
                        .filter(feature -> featureCode.equals(feature.featureCode()))
                        .flatMap(feature -> feature.quotaConfigs() != null
                                ? feature.quotaConfigs().stream()
                                : java.util.stream.Stream.empty())
                        .filter(quota -> slot.equals(quota.resource()))
                        .findFirst();
    }

    private java.util.Optional<QuotaLimitEntry> resolvePlanLimit(
            Subscription subscription, String featureCode, String slot) {
        var planFeature = planFeatureRepository
                .findByPlanIdAndFeature_Code(subscription.getPlan().getId(), featureCode);

        if (planFeature.isEmpty()) {
            return java.util.Optional.empty();
        }

        return planFeature.get().getQuotaConfigs().stream()
                .filter(e -> e.resource().equals(slot))
                .findFirst();
    }
}
