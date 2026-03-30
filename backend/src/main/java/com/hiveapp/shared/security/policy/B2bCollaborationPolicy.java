package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class B2bCollaborationPolicy implements PermissionPolicy {

    // Note: In real implementation, this would use a Repository or Cache
    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx) || !ctx.isB2B()) {
            return Decision.ABSTAIN;
        }

        // Logic: Check if (ctx.getCurrentAccountId()) is granted (requested.path()) 
        // by the owner of (ctx.getTargetCompanyId())
        
        // return checkB2bGrant(ctx, requested) ? Decision.GRANTED : Decision.ABSTAIN;
        return Decision.ABSTAIN; // Placeholder for now
    }
}
