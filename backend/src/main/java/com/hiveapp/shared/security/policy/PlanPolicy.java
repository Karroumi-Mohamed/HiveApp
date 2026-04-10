package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanPolicy implements PermissionPolicy {

    private final SubscriptionRepository subscriptionRepository;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx)) {
            return Decision.ABSTAIN;
        }

        var sub = subscriptionRepository.findByAccountId(ctx.getCurrentAccountId());
        if (sub.isEmpty() || sub.get().getStatus() != SubscriptionStatus.ACTIVE) {
            return Decision.DENIED;
        }

        // Logic: Check if plan features cover the requested permission
        // Simplified: assuming plan allowed list is available
        return Decision.ABSTAIN; 
    }
}
