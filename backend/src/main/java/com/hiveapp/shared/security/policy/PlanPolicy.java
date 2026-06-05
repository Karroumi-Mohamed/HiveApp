package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanPolicy implements PermissionPolicy {

    private final PlanEntitlementService planEntitlementService;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx) || ctx.currentAccountId() == null) {
            return Decision.ABSTAIN;
        }

        // ABSTAIN (not GRANTED) — entitlement gate only.
        // Returning GRANTED here would stop the sieve and skip UserRolePolicy entirely,
        // meaning any member in the workspace could access any plan feature regardless of role.
        // Instead: ABSTAIN if entitled → UserRolePolicy decides who specifically can act.
        //          DENIED if not entitled → account cannot use this feature at all.
        return planEntitlementService.isPermissionEntitled(ctx.currentAccountId(), requested.path())
                ? Decision.ABSTAIN
                : Decision.DENIED;
    }
}
