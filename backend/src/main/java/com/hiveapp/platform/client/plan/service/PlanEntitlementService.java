package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanEntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PermissionRepository permissionRepository;
    private final SubscriptionOverrideReader subscriptionOverrideReader;

    public boolean isPermissionEntitled(UUID accountId, String permissionCode) {
        Optional<Subscription> subscription = subscriptionRepository.findActiveByAccountId(accountId);
        if (subscription.isEmpty()) {
            subscription = subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING);
        }

        if (subscription.isEmpty() || isExpired(subscription.get().getCurrentPeriodEnd())) {
            return false;
        }

        var sub = subscription.get();
        if (planFeatureRepository.existsByPlanIdAndPermissionCode(sub.getPlan().getId(), permissionCode)) {
            return true;
        }

        if (sub.getCustomOverrides() == null) {
            return false;
        }

        var permission = permissionRepository.findByCode(permissionCode).orElse(null);
        if (permission == null || permission.getFeature() == null) {
            return false;
        }

        try {
            var overrides = subscriptionOverrideReader.read(sub.getCustomOverrides());
            return overrides.addedFeatures() != null
                    && overrides.addedFeatures().contains(permission.getFeature().getCode());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isExpired(LocalDateTime currentPeriodEnd) {
        return currentPeriodEnd != null && !currentPeriodEnd.isAfter(LocalDateTime.now());
    }
}
