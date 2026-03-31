package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanPolicy implements PermissionPolicy {

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx)) {
            return Decision.ABSTAIN;
        }

        // Logic: Get the effective plan ceiling for (ctx.getCurrentAccountId())
        // including the Template permissions and JSONB overrides.
        
        // if (!planAllows(ctx.getCurrentAccountId(), requested)) {
        //     return Decision.DENIED; // Stops the sieve immediately
        // }

        return Decision.ABSTAIN;
    }
}
