package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Applies the role picker as one transaction while preserving the existing per-action
 * Permissionizer checks in {@link AdminUserService}.
 */
@Service
@RequiredArgsConstructor
public class AdminUserRoleSetService {

    private final AdminUserRoleRepository adminUserRoleRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminUserService adminUserService;

    @Transactional
    public void replaceRoles(UUID adminUserId, Set<UUID> requestedRoleIds) {
        if (!adminUserRepository.existsById(adminUserId)) {
            throw new ResourceNotFoundException("AdminUser", "id", adminUserId);
        }
        Set<UUID> desired = Set.copyOf(requestedRoleIds);
        Set<UUID> current = new HashSet<>(
                adminUserRoleRepository.findAllRoleIdsByAdminUserId(adminUserId));

        // Add first so a valid replacement never creates a momentary access gap. Any denied or
        // invalid operation rolls the whole outer transaction back, including earlier changes.
        desired.stream()
                .filter(roleId -> !current.contains(roleId))
                .sorted()
                .forEach(roleId -> adminUserService.assignRole(adminUserId, roleId));
        current.stream()
                .filter(roleId -> !desired.contains(roleId))
                .sorted()
                .forEach(roleId -> adminUserService.removeRole(adminUserId, roleId));
    }
}
