package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
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

    public void requireCanManageRole(UUID adminRoleId, String operation) {
        AdminUser actor = currentActor();
        if (actor.isSuperAdmin()) {
            return;
        }

        boolean exceedsActorPermissions = adminRolePermissionRepository.findAllByAdminRoleId(adminRoleId).stream()
                .anyMatch(grant -> !adminUserRepository.hasPermission(
                        actor.getId(), grant.getPermission().getCode()));
        if (exceedsActorPermissions) {
            throw new InvalidPermissionGrantException(
                    "A platform administrator cannot " + operation
                            + " a role containing permissions they do not hold.");
        }
    }

    public void requireCanManagePermission(String permissionCode, String operation) {
        AdminUser actor = currentActor();
        if (actor.isSuperAdmin()) {
            return;
        }
        if (!adminUserRepository.hasPermission(actor.getId(), permissionCode)) {
            throw new InvalidPermissionGrantException(
                    "A platform administrator cannot " + operation
                            + " a permission they do not hold.");
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
}
