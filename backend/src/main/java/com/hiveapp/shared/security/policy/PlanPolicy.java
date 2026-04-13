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
import java.util.Set;

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
        boolean inPlan = planFeatureRepository.existsByPlanIdAndPermissionCode(sub.getPlan().getId(), requested.path());
        if (inPlan) return Decision.GRANTED;

        // 3. Check Custom Overrides (JSONB)
        if (sub.getCustomOverrides() != null) {
            try {
                SubscriptionOverrides overrides = objectMapper.convertValue(sub.getCustomOverrides(), SubscriptionOverrides.class);
                
                // Check explicitly added features
                var permissionOpt = permissionRepository.findByCode(requested.path());
                if (permissionOpt.isPresent()) {
                    String featureCode = permissionOpt.get().getFeature().getCode();
                    String moduleCode = permissionOpt.get().getFeature().getModule().getCode();
                    
                    if (overrides.addedFeatures() != null && overrides.addedFeatures().contains(featureCode)) {
                        return Decision.GRANTED;
                    }
                    
                    if (overrides.addedModules() != null && overrides.addedModules().contains(moduleCode)) {
                        return Decision.GRANTED;
                    }
                }
            } catch (Exception e) {
                // Log error and continue
            }
        }
        
        return Decision.DENIED;
    }
}
