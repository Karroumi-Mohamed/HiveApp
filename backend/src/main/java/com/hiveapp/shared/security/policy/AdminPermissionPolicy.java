package com.hiveapp.shared.security.policy;

import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * First policy in the sieve — evaluated before all client policies.
 *
 * SuperAdmin → GRANTED unconditionally.
 * Active non-super admin → checked against AdminRole → AdminRolePermission → AdminPermission chain.
 * Not an admin user → ABSTAIN (let client policies continue).
 *
 * This policy intentionally short-circuits the sieve for admin actors so they
 * are never evaluated against PlanPolicy or UserRolePolicy.
 */
@Component
@RequiredArgsConstructor
public class AdminPermissionPolicy implements PermissionPolicy {

    private final AdminUserRepository adminUserRepository;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx) || ctx.actorUserId() == null) {
            return Decision.ABSTAIN;
        }

        // ctx.actorUserId() = User.id (set consistently by AdminUserDetailsServiceImpl)
        var adminUser = adminUserRepository.findByUserId(ctx.actorUserId()).orElse(null);

        if (adminUser == null || !adminUser.isActive()) {
            // Not an admin — pass down to client policies
            return Decision.ABSTAIN;
        }

        // SuperAdmin can do everything
        if (adminUser.isSuperAdmin()) {
            return Decision.GRANTED;
        }

        // Non-super admin: check AdminRole → AdminRolePermission → AdminPermission chain
        boolean granted = adminUserRepository.hasPermission(adminUser.getId(), requested.path());
        return granted ? Decision.GRANTED : Decision.DENIED;
    }
}
