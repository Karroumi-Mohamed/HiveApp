package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserRolePolicy implements PermissionPolicy {

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx)) {
            return Decision.ABSTAIN;
        }

        // Logic: Check MemberPermissionOverride first (High Priority)
        // Logic: Then check union of Permissions in all MemberRoles for (ctx.getTargetCompanyId())
        
        // return resolveUserDecision(ctx, requested);
        return Decision.ABSTAIN; 
    }
}
