package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import lombok.RequiredArgsConstructor;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class B2bCollaborationPolicy implements PermissionPolicy {

    private final CollaborationPermissionRepository collaborationPermissionRepository;
    private final PlanEntitlementService planEntitlementService;
    private final PermissionGrantValidator permissionGrantValidator;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx) || !ctx.isB2B()) {
            return Decision.ABSTAIN;
        }

        if (ctx.collaborationId() == null) return Decision.DENIED;

        if (!permissionGrantValidator.isB2bRuntimeEligible(requested.path())) {
            return Decision.DENIED;
        }

        boolean isGranted = collaborationPermissionRepository.existsActiveByCollaborationIdAndPermissionCode(
            ctx.collaborationId(), requested.path());

        if (!isGranted) {
            return Decision.DENIED;
        }

        return planEntitlementService.isPermissionEntitled(ctx.currentAccountId(), requested.path())
                ? Decision.GRANTED
                : Decision.DENIED;
    }
}
