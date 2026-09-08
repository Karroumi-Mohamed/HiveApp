package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves what an administrator may actually do.
 *
 * <p>Shared by the session's own profile and by the operator detail screen so the two can never
 * disagree about what a permission set means. A SuperAdmin holds no role rows at all — their
 * authority is structural — so answering from role grants alone would report them as powerless.
 */
@Component
@RequiredArgsConstructor
public class AdminPermissionResolver {

    private final AdminUserRepository adminUserRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;

    public Set<String> resolve(AdminUser admin) {
        if (admin.isSuperAdmin()) {
            return permissionGrantValidator.platformAdminRoleGrantableCodes(permissionRepository.findAllCodes());
        }
        // This is the hot session/authorization path: keep the code-only projection rather than
        // hydrating full catalogue entities merely to discard their metadata.
        return new HashSet<>(adminUserRepository.findAllPermissionCodes(admin.getId()));
    }

    public Set<Permission> resolveEntities(AdminUser admin) {
        if (admin.isSuperAdmin()) {
            var permissions = permissionRepository.findAll();
            Set<String> allowed = permissionGrantValidator.platformAdminRoleGrantableCodes(
                    permissions.stream().map(Permission::getCode).toList());
            return permissions.stream()
                    .filter(permission -> allowed.contains(permission.getCode()))
                    .collect(Collectors.toSet());
        }
        // Only active roles contribute; deactivating a role withdraws its grants immediately.
        return new HashSet<>(adminUserRepository.findAllPermissions(admin.getId()));
    }

}
