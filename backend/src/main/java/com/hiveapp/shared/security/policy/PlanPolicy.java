package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanPolicy implements PermissionPolicy {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanFeatureRepository planFeatureRepository;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx) || ctx.currentAccountId() == null) {
            return Decision.ABSTAIN;
        }

        // 1. Get Active Subscription
        var subOpt = subscriptionRepository.findByAccountId(ctx.currentAccountId());
        if (subOpt.isEmpty() || subOpt.get().getStatus() != SubscriptionStatus.ACTIVE) {
            return Decision.DENIED;
        }

        var sub = subOpt.get();

        // 2. Check if the Permission belongs to a Feature included in the Plan
        // Logic: permission -> feature -> plan_features table
        boolean hasFeature = planFeatureRepository.existsByPlanIdAndPermissionCode(sub.getPlan().getId(), requested.path());
        
        // 3. TODO: Check custom_overrides JSONB for added features
        
        return hasFeature ? Decision.GRANTED : Decision.DENIED;
    }
}
