package com.hiveapp.admin.domain.service;

import com.hiveapp.admin.domain.dto.*;
import com.hiveapp.admin.domain.entity.*;
import com.hiveapp.admin.domain.mapper.AdminMapper;
import com.hiveapp.admin.domain.repository.*;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminPermissionRepository adminPermissionRepository;
    private final AdminMapper adminMapper;

    @Transactional
    public AdminUserResponse createAdminUser(UUID userId, boolean isSuperAdmin) {
        if (adminUserRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("AdminUser", "userId", userId);
        }

        AdminUser adminUser = AdminUser.builder()
                .userId(userId)
                .isSuperAdmin(isSuperAdmin)
                .build();

        AdminUser saved = adminUserRepository.save(adminUser);
        log.info("Admin user created: {} (superAdmin: {})", saved.getId(), isSuperAdmin);
        return adminMapper.toUserResponse(saved);
    }

    @Transactional
    public AdminRoleResponse createAdminRole(CreateAdminRoleRequest request) {
        AdminRole role = AdminRole.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        if (request.getPermissionIds() != null) {
            for (UUID permissionId : request.getPermissionIds()) {
                AdminPermission permission = adminPermissionRepository.findById(permissionId)
                        .orElseThrow(() -> new ResourceNotFoundException("AdminPermission", "id", permissionId));
                AdminRolePermission arp = AdminRolePermission.builder()
                        .adminPermission(permission)
                        .build();
                role.addPermission(arp);
            }
        }

        AdminRole saved = adminRoleRepository.save(role);
        log.info("Admin role created: {}", saved.getName());
        return adminMapper.toRoleResponse(saved);
    }

    @Transactional
    public void assignRoleToAdmin(UUID adminUserId, UUID adminRoleId) {
        AdminUser adminUser = adminUserRepository.findByIdWithRoles(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "id", adminUserId));
        AdminRole adminRole = adminRoleRepository.findById(adminRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole", "id", adminRoleId));

        AdminUserRole aur = AdminUserRole.builder()
                .adminRole(adminRole)
                .build();
        adminUser.addRole(aur);
        adminUserRepository.save(adminUser);
        log.info("Role {} assigned to admin {}", adminRoleId, adminUserId);
    }

    public AdminUserResponse getAdminUserById(UUID id) {
        AdminUser adminUser = adminUserRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "id", id));
        return adminMapper.toUserResponse(adminUser);
    }

    public AdminUserResponse getAdminUserByUserId(UUID userId) {
        AdminUser adminUser = adminUserRepository.findByUserIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "userId", userId));
        return adminMapper.toUserResponse(adminUser);
    }

    public List<AdminRoleResponse> getAllActiveRoles() {
        return adminRoleRepository.findAllActiveWithPermissions().stream()
                .map(adminMapper::toRoleResponse)
                .toList();
    }

    public List<AdminPermissionResponse> getAllPermissions() {
        return adminPermissionRepository.findAll().stream()
                .map(adminMapper::toPermissionResponse)
                .toList();
    }
}
