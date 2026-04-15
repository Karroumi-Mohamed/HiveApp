package com.hiveapp.shared.quota;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Enforces quota limits at the Feature/slot level.
 * Used by all ERP module services before performing resource-creating operations.
 *
 * Does NOT know how to count resources — the caller provides a LongSupplier.
 * The supplier is only called when a real limit exists (lazy evaluation).
 *
 * Usage:
 *   quotaEnforcer.check(
 *       PlatformFeature.WORKSPACE,
 *       PlatformFeature.MEMBERS,
 *       accountId,
 *       () -> memberRepo.countByAccountId(accountId)
 *   );
 */
@Service
@RequiredArgsConstructor
public class QuotaEnforcer {

    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionRepository subscriptionRepository;

    public void check(AppFeature feature, String slot, UUID accountId, LongSupplier currentUsage) {
        var subscription = subscriptionRepository
                .findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));

        var planFeature = planFeatureRepository
                .findByPlanIdAndFeature_Code(subscription.getPlan().getId(), feature.code());

        if (planFeature.isEmpty()) return; // feature not in plan — PermissionGuard handles access denial

        var limitEntry = planFeature.get().getQuotaConfigs().stream()
                .filter(e -> e.resource().equals(slot))
                .findFirst();

        if (limitEntry.isEmpty()) return;           // no quota defined for this slot
        if (limitEntry.get().limit() == null) return; // explicitly unlimited

        long limit   = limitEntry.get().limit();
        long current = currentUsage.getAsLong();    // only called when a real limit exists

        // Find unit for readable exception message
        String unit = feature.quotaSlots().stream()
                .filter(s -> s.resource().equals(slot))
                .map(QuotaSlot::unit)
                .findFirst()
                .orElse(slot);

        if (current >= limit) {
            throw new QuotaExceededException(slot, limit, current, unit);
        }
    }
}
