package com.hiveapp.shared.quota;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Enforces quota limits at the Feature/slot level.
 *
 * Check order:
 *   1. Subscription-level quota override (client paid to bump this slot)
 *   2. Plan-level quota config (the template default)
 *
 * The LongSupplier is only called when a real limit exists (lazy evaluation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaEnforcer {

    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    public void check(AppFeature feature, String slot, UUID accountId, LongSupplier currentUsage) {
        var subscription = subscriptionRepository
                .findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));

        // 1. Check subscription-level quota override first
        var overrideResult = resolveSubscriptionOverride(subscription, feature.code(), slot);

        Long effectiveLimit;
        if (overrideResult.isPresent()) {
            long v = overrideResult.get();
            if (v == OVERRIDE_UNLIMITED) return; // explicitly unlimited — skip check
            effectiveLimit = v;
        } else {
            // 2. Fall back to plan-level limit
            var planFeature = planFeatureRepository
                    .findByPlanIdAndFeature_Code(subscription.getPlan().getId(), feature.code());

            if (planFeature.isEmpty()) return; // feature not in plan — PermissionGuard handles denial

            var limitEntry = planFeature.get().getQuotaConfigs().stream()
                    .filter(e -> e.resource().equals(slot))
                    .findFirst();

            if (limitEntry.isEmpty()) return;             // no quota defined for this slot
            if (limitEntry.get().limit() == null) return; // plan-level unlimited

            effectiveLimit = limitEntry.get().limit();
        }

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

    /** Sentinel: override exists and is explicitly unlimited. */
    private static final long OVERRIDE_UNLIMITED = -1L;

    /**
     * Three-state result:
     *   Optional.empty()          → no override, caller falls back to plan default
     *   Optional.of(OVERRIDE_UNLIMITED) → override exists, explicitly unlimited
     *   Optional.of(n)            → override exists with limit n
     */
    private java.util.Optional<Long> resolveSubscriptionOverride(
            Subscription sub, String featureCode, String slot) {

        if (sub.getCustomOverrides() == null) return java.util.Optional.empty();
        try {
            var overrides = objectMapper.convertValue(sub.getCustomOverrides(), SubscriptionOverrides.class);
            if (overrides.quotaOverrides() == null) return java.util.Optional.empty();

            var match = overrides.quotaOverrides().stream()
                    .filter(o -> featureCode.equals(o.featureCode()) && slot.equals(o.resource()))
                    .findFirst();

            if (match.isEmpty()) return java.util.Optional.empty();

            Long limit = match.get().limit();
            return java.util.Optional.of(limit != null ? limit : OVERRIDE_UNLIMITED);

        } catch (Exception e) {
            log.warn("Could not read quota overrides for subscription {}: {}", sub.getId(), e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
