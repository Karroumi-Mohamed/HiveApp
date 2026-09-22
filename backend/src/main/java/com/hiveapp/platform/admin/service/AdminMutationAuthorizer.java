package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminMutationAuthorizer {

    private final AdminUserRepository adminUserRepository;
    private final AdminRolePermissionRepository adminRolePermissionRepository;

    public boolean currentActorIsSuperAdmin() {
        return currentActor().isSuperAdmin();
    }

    public void requireCanModifyAdmin(AdminUser target) {
        AdminUser actor = currentActor();
        if (target.isSuperAdmin() && !actor.isSuperAdmin()) {
            throw new ForbiddenException("Only a SuperAdmin can modify another SuperAdmin.");
        }
    }

    public void requireCanModifyRoleAssignments(AdminUser target) {
        AdminUser actor = currentActor();
        requireCanModifyAdmin(target);
        if (!actor.isSuperAdmin()
                && target.getUser() != null
                && actor.getUser() != null
                && target.getUser().getId().equals(actor.getUser().getId())) {
            throw new ForbiddenException(
                    "A platform administrator cannot modify their own role assignments.");
        }
    }

    public void requireCanManageRole(UUID adminRoleId, String operation) {
        GrantCeiling ceiling = currentActorGrantCeiling();
        boolean exceedsActorPermissions = adminRolePermissionRepository.findAllByAdminRoleId(adminRoleId).stream()
                .anyMatch(grant -> !ceiling.allows(grant.getPermission().getCode()));
        if (exceedsActorPermissions) {
            throw new InvalidPermissionGrantException(
                    "A platform administrator cannot " + operation
                            + " a role containing permissions they do not hold.");
        }
    }

    public void requireCanManagePermission(String permissionCode, String operation) {
        requireCanManagePermission(currentActorGrantCeiling(), permissionCode, operation);
    }

    public void requireCanManagePermission(
            GrantCeiling ceiling,
            String permissionCode,
            String operation) {
        if (!ceiling.allows(permissionCode)) {
            throw new InvalidPermissionGrantException(
                    "A platform administrator cannot " + operation
                            + " a permission they do not hold.");
        }
    }

    public boolean canManagePermission(String permissionCode) {
        return currentActorGrantCeiling().allows(permissionCode);
    }

    public GrantCeiling currentActorGrantCeiling() {
        AdminUser actor = currentActor();
        if (actor.isSuperAdmin()) {
            return new GrantCeiling(true, Set.of());
        }
        return new GrantCeiling(false, Set.copyOf(adminUserRepository.findAllPermissionCodes(actor.getId())));
    }

    public UUID currentActorUserId() {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.actorUserId() == null) {
            throw new ForbiddenException("An authenticated platform administrator is required.");
        }
        return context.actorUserId();
    }

    public UUID currentActorAdminUserId() {
        return currentActor().getId();
    }

    /** Re-evaluate a durable instruction's actor, including runtime vetoes, without impersonating
     * a browser session or trusting the permissions captured when the job was created. */
    public void requireBackgroundPermission(UUID userId, String permissionCode) {
        adminUserRepository.findByUserId(userId)
                .filter(AdminUser::isActive)
                .filter(admin -> admin.getUser() != null && admin.getUser().isActive())
                .orElseThrow(() -> new ForbiddenException("The requesting administrator is no longer active."));
        var context = new com.hiveapp.shared.security.context.HiveAppPermissionContext(
                userId, null, null, null, null, false);
        if (!dev.karroumi.permissionizer.PermissionGuard.has(
                new dev.karroumi.permissionizer.Permission(permissionCode), context)) {
            throw new ForbiddenException("The requesting administrator no longer holds " + permissionCode + ".");
        }
    }

    private AdminUser currentActor() {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.actorUserId() == null) {
            throw new ForbiddenException("An authenticated platform administrator is required.");
        }
        return adminUserRepository.findByUserId(context.actorUserId())
                .filter(AdminUser::isActive)
                .orElseThrow(() -> new ForbiddenException(
                        "The acting user is not an active platform administrator."));
    }

    public record GrantCeiling(boolean unrestricted, Set<String> permissionCodes) {
        public boolean allows(String permissionCode) {
            return unrestricted || permissionCodes.contains(permissionCode);
        }

        public boolean allowsAll(Collection<String> codes) {
            return unrestricted || permissionCodes.containsAll(codes);
        }
    }
}
