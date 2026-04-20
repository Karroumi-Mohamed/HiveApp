package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminPermissionRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.service.AdminRoleService;
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
@PermissionNode(key = "roles", description = "Admin Role Management")
public class AdminRoleServiceImpl implements AdminRoleService {

    private final AdminRoleRepository adminRoleRepository;
    private final AdminPermissionRepository adminPermissionRepository;
    private final AdminRolePermissionRepository adminRolePermissionRepository;

    @Override
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
    public void grantPermission(UUID adminRoleId, UUID adminPermissionId) {
        if (adminRolePermissionRepository.existsByAdminRoleIdAndAdminPermissionId(adminRoleId, adminPermissionId)) {
            throw new DuplicateResourceException("AdminRolePermission", "adminPermissionId", adminPermissionId);
        }

        var adminRole = getAdminRole(adminRoleId);
        var adminPermission = adminPermissionRepository.findById(adminPermissionId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminPermission", "id", adminPermissionId));

        AdminRolePermission arp = new AdminRolePermission();
        arp.setAdminRole(adminRole);
        arp.setAdminPermission(adminPermission);
        adminRolePermissionRepository.save(arp);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke_permission", description = "Revoke admin permission from admin role")
    public void revokePermission(UUID adminRoleId, UUID adminPermissionId) {
        adminRolePermissionRepository.deleteByAdminRoleIdAndAdminPermissionId(adminRoleId, adminPermissionId);
    }
}
