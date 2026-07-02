package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.admin.service.AdminRoleService;
import com.hiveapp.platform.registry.definition.AdminRolesFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = AdminRolesFeature.KEY, description = "Admin Role Management", guard = PermissionNode.Guard.ON)
public class AdminRoleServiceImpl extends PlatformControlFeatureService implements AdminRoleService {

    private final AdminRoleRepository adminRoleRepository;
    private final PermissionRepository permissionRepository;
    private final AdminRolePermissionRepository adminRolePermissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final AdminMutationAuthorizer adminMutationAuthorizer;

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
        adminMutationAuthorizer.requireCanManageRole(id, "update");
        adminRole.setName(name);
        adminRole.setDescription(description);
        return adminRoleRepository.save(adminRole);
    }

    @Override
    @Transactional
    @PermissionNode(key = "toggle_active", description = "Activate or deactivate admin role")
    public void toggleActive(UUID id) {
        var adminRole = getAdminRole(id);
        adminMutationAuthorizer.requireCanManageRole(id, "activate or deactivate");
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
        adminMutationAuthorizer.requireCanManageRole(adminRoleId, "modify");
        adminMutationAuthorizer.requireCanManagePermission(permission.getCode(), "grant");

        AdminRolePermission arp = new AdminRolePermission();
        arp.setAdminRole(adminRole);
        arp.setPermission(permission);
        adminRolePermissionRepository.save(arp);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke_permission", description = "Revoke admin permission from admin role")
    public void revokePermission(UUID adminRoleId, UUID permissionId) {
        getAdminRole(adminRoleId);
        var permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", permissionId));
        adminMutationAuthorizer.requireCanManageRole(adminRoleId, "modify");
        adminMutationAuthorizer.requireCanManagePermission(permission.getCode(), "revoke");
        adminRolePermissionRepository.deleteByAdminRoleIdAndPermissionId(adminRoleId, permissionId);
    }
}
