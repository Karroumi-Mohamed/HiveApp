package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.entity.AdminUserRole;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
import com.hiveapp.platform.admin.service.AdminUserService;
import com.hiveapp.identity.service.IdentityService;
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
@PermissionNode(key = "users", description = "Admin User Management")
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserRoleRepository adminUserRoleRepository;
    private final IdentityService identityService;

    @Override
    public AdminUser getAdminUser(UUID id) {
        return adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "id", id));
    }

    @Override
    @PermissionNode(key = "read", description = "List all admin users")
    public List<AdminUser> getAllAdminUsers() {
        return adminUserRepository.findAll();
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create admin user")
    public AdminUser createAdminUser(UUID userId, boolean isSuperAdmin) {
        var user = identityService.getUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        AdminUser adminUser = new AdminUser();
        adminUser.setUser(user);
        adminUser.setSuperAdmin(isSuperAdmin);
        adminUser.setActive(true);
        return adminUserRepository.save(adminUser);
    }

    @Override
    @Transactional
    @PermissionNode(key = "toggle_active", description = "Activate or deactivate admin user")
    public void toggleActive(UUID id) {
        var adminUser = getAdminUser(id);
        adminUser.setActive(!adminUser.isActive());
        adminUserRepository.save(adminUser);
    }

    @Override
    @Transactional
    @PermissionNode(key = "assign_role", description = "Assign admin role to admin user")
    public void assignRole(UUID adminUserId, UUID adminRoleId) {
        if (adminUserRoleRepository.existsByAdminUserIdAndAdminRoleId(adminUserId, adminRoleId)) {
            throw new DuplicateResourceException("AdminUserRole", "adminRoleId", adminRoleId);
        }

        var adminUser = getAdminUser(adminUserId);
        var adminRole = adminRoleRepository.findById(adminRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole", "id", adminRoleId));

        AdminUserRole aur = new AdminUserRole();
        aur.setAdminUser(adminUser);
        aur.setAdminRole(adminRole);
        adminUserRoleRepository.save(aur);
    }

    @Override
    @Transactional
    @PermissionNode(key = "remove_role", description = "Remove admin role from admin user")
    public void removeRole(UUID adminUserId, UUID adminRoleId) {
        adminUserRoleRepository.deleteByAdminUserIdAndAdminRoleId(adminUserId, adminRoleId);
    }
}
