package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanPolicy implements PermissionPolicy {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PermissionRepository permissionRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx) || ctx.currentAccountId() == null) {
            return Decision.ABSTAIN;
        }

        // 1. Get Active or Trialing Subscription
        var subOpt = subscriptionRepository.findActiveByAccountId(ctx.currentAccountId());
        if (subOpt.isEmpty()) {
            subOpt = subscriptionRepository.findByAccountIdAndStatus(ctx.currentAccountId(), SubscriptionStatus.TRIALING);
        }

        if (subOpt.isEmpty()) {
            return Decision.DENIED;
        }

        var sub = subOpt.get();

        // 2. Check Plan Template Features
        // ABSTAIN (not GRANTED) — entitlement gate only.
        // Returning GRANTED here would stop the sieve and skip UserRolePolicy entirely,
        // meaning any member in the workspace could access any plan feature regardless of role.
        // Instead: ABSTAIN if entitled → UserRolePolicy decides who specifically can act.
        //          DENIED if not entitled → account cannot use this feature at all.
        boolean inPlan = planFeatureRepository.existsByPlanIdAndPermissionCode(sub.getPlan().getId(), requested.path());
        if (inPlan) return Decision.ABSTAIN;

        // 3. Check Custom Overrides (Expanded Feature Snapshot)
        if (sub.getCustomOverrides() != null) {
            try {
                SubscriptionOverrides overrides = objectMapper.convertValue(sub.getCustomOverrides(), SubscriptionOverrides.class);

                // Get the Feature Code for this Permission Brick
                var permissionOpt = permissionRepository.findByCode(requested.path());
                if (permissionOpt.isPresent()) {
                    String featureCode = permissionOpt.get().getFeature().getCode();
                    if (overrides.addedFeatures() != null && overrides.addedFeatures().contains(featureCode)) {
                        return Decision.ABSTAIN;
                    }
                }
            } catch (Exception e) {
                // Sieve continues on error
            }
        }

        return Decision.DENIED;
    }
}
