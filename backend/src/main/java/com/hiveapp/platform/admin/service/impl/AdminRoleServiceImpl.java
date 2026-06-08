package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminRoleService;
import com.hiveapp.platform.registry.definition.AdminRolesFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = AdminRolesFeature.KEY, description = "Admin Role Management")
public class AdminRoleServiceImpl extends PlatformControlFeatureService implements AdminRoleService {

    private final AdminRoleRepository adminRoleRepository;
    private final PermissionRepository permissionRepository;
    private final AdminRolePermissionRepository adminRolePermissionRepository;
    private final AdminUserRepository adminUserRepository;
    private final PermissionGrantValidator permissionGrantValidator;

    @Override
    protected FeatureDefinition featureDefinition() {
        return AdminRolesFeature.definition();
    }

    @Override
    @PermissionNode(key = "read_detail", description = "Read an admin role")
    public AdminRole getAdminRole(UUID id) {
        return adminRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole", "id", id));
    }

    @Override
    @PermissionNode(key = "read", description = "List all admin roles")
    public List<AdminRole> getAllAdminRoles() {
        return adminRoleRepository.findAll();
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create admin role")
    public AdminRole createAdminRole(String name, String description) {
        AdminRole adminRole = new AdminRole();
        adminRole.setName(name);
        adminRole.setDescription(description);
        adminRole.setActive(true);
        return adminRoleRepository.save(adminRole);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update", description = "Update admin role metadata")
    public AdminRole updateAdminRole(UUID id, String name, String description) {
        var adminRole = getAdminRole(id);
        adminRole.setName(name);
        adminRole.setDescription(description);
        return adminRoleRepository.save(adminRole);
    }

    @Override
    @Transactional
    @PermissionNode(key = "toggle_active", description = "Activate or deactivate admin role")
    public void toggleActive(UUID id) {
        var adminRole = getAdminRole(id);
        adminRole.setActive(!adminRole.isActive());
        adminRoleRepository.save(adminRole);
    }

    @Override
    @Transactional
    @PermissionNode(key = "grant_permission", description = "Grant admin permission to admin role")
    public void grantPermission(UUID adminRoleId, UUID permissionId) {
        if (adminRolePermissionRepository.existsByAdminRoleIdAndPermissionId(adminRoleId, permissionId)) {
            throw new DuplicateResourceException("AdminRolePermission", "permissionId", permissionId);
        }

        var adminRole = getAdminRole(adminRoleId);
        var permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", permissionId));
        permissionGrantValidator.requirePlatformAdminRoleGrantable(permission.getCode());
        requireActorCanDelegatePermission(permission.getCode());

        AdminRolePermission arp = new AdminRolePermission();
        arp.setAdminRole(adminRole);
        arp.setPermission(permission);
        adminRolePermissionRepository.save(arp);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke_permission", description = "Revoke admin permission from admin role")
    public void revokePermission(UUID adminRoleId, UUID permissionId) {
        adminRolePermissionRepository.deleteByAdminRoleIdAndPermissionId(adminRoleId, permissionId);
    }

    private void requireActorCanDelegatePermission(String permissionCode) {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.actorUserId() == null) {
            throw new InvalidPermissionGrantException("An authenticated platform administrator is required to grant permissions.");
        }

        var actor = adminUserRepository.findByUserId(context.actorUserId())
                .orElseThrow(() -> new InvalidPermissionGrantException("The acting user is not a platform administrator."));
        if (actor.isSuperAdmin()) {
            return;
        }
        if (!adminUserRepository.hasPermission(actor.getId(), permissionCode)) {
            throw new InvalidPermissionGrantException(
                    "A platform administrator cannot grant a permission they do not hold.");
        }
    }
}
