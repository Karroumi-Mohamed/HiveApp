package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
import com.hiveapp.platform.admin.dto.BulkOperationResult;
import com.hiveapp.platform.admin.dto.RoleHolderDto;
import com.hiveapp.shared.exception.ErrorCodes;
import com.hiveapp.shared.transaction.IsolatedOperationRunner;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.admin.service.AdminRoleService;
import com.hiveapp.platform.admin.dto.AdminPermissionSummaryDto;
import com.hiveapp.platform.admin.dto.AdminRoleResponseDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = AdminRolesFeature.KEY, description = "Admin Role Management", guard = PermissionNode.Guard.ON)
public class AdminRoleServiceImpl extends PlatformControlFeatureService implements AdminRoleService {

    private final AdminRoleRepository adminRoleRepository;
    private final PermissionRepository permissionRepository;
    private final AdminRolePermissionRepository adminRolePermissionRepository;
    private final AdminUserRoleRepository adminUserRoleRepository;
    private final IsolatedOperationRunner isolatedOperationRunner;
    private final PermissionGrantValidator permissionGrantValidator;
    private final AdminMutationAuthorizer adminMutationAuthorizer;

    @Override
    protected FeatureDefinition featureDefinition() {
        return AdminRolesFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_detail", description = "Read an admin role")
    public AdminRoleResponseDto getAdminRole(UUID id) {
        AdminRole role = requireAdminRole(id);
        return toResponse(
                role,
                adminRolePermissionRepository.findAllWithPermissionByAdminRoleIdIn(List.of(id)),
                assignedOperatorCount(id));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "List all admin roles")
    public Page<AdminRoleResponseDto> getAdminRoles(String search, Boolean active, Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Page<AdminRole> roles = adminRoleRepository.search(normalizedSearch, active, pageable);
        if (roles.isEmpty()) {
            return roles.map(role -> toResponse(role, List.of(), 0L));
        }
        Map<UUID, List<AdminRolePermission>> grants = adminRolePermissionRepository
                .findAllWithPermissionByAdminRoleIdIn(
                        roles.stream().map(AdminRole::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(grant -> grant.getAdminRole().getId()));
        Map<UUID, Long> assignmentCounts = adminUserRoleRepository
                .countByAdminRoleIdIn(roles.stream().map(AdminRole::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        AdminUserRoleRepository.RoleAssignmentCount::getRoleId,
                        AdminUserRoleRepository.RoleAssignmentCount::getTotal));
        return roles.map(role -> toResponse(
                role,
                grants.getOrDefault(role.getId(), List.of()),
                assignmentCounts.getOrDefault(role.getId(), 0L)));
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create admin role")
    public AdminRoleResponseDto createAdminRole(String name, String description) {
        AdminRole adminRole = new AdminRole();
        adminRole.setName(name);
        adminRole.setDescription(description);
        adminRole.setActive(true);
        // A role that has just been created is held by nobody.
        return toResponse(adminRoleRepository.save(adminRole), List.of(), 0L);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update", description = "Update admin role metadata")
    public AdminRoleResponseDto updateAdminRole(UUID id, String name, String description) {
        var adminRole = requireAdminRole(id);
        adminMutationAuthorizer.requireCanManageRole(id, "update");
        adminRole.setName(name);
        adminRole.setDescription(description);
        var saved = adminRoleRepository.save(adminRole);
        return toResponse(
                saved,
                adminRolePermissionRepository.findAllWithPermissionByAdminRoleIdIn(List.of(id)),
                assignedOperatorCount(saved.getId()));
    }

    @Override
    @Transactional
    @PermissionNode(key = "toggle_active", description = "Activate or deactivate admin role")
    public void toggleActive(UUID id) {
        var adminRole = requireAdminRole(id);
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

        var adminRole = requireAdminRole(adminRoleId);
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
        requireAdminRole(adminRoleId);
        var permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", permissionId));
        adminMutationAuthorizer.requireCanManageRole(adminRoleId, "modify");
        adminMutationAuthorizer.requireCanManagePermission(permission.getCode(), "revoke");
        adminRolePermissionRepository.deleteByAdminRoleIdAndPermissionId(adminRoleId, permissionId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "bulk_set_active", description = "Activate or deactivate several admin roles")
    public BulkOperationResult setActiveBulk(List<UUID> ids, boolean active) {
        List<BulkOperationResult.Failure> failures = new java.util.ArrayList<>();
        for (UUID id : ids) {
            try {
                // Each role in its own transaction, so one rejection cannot undo the rest.
                isolatedOperationRunner.run(() -> {
                    AdminRole role = requireAdminRole(id);
                    if (role.isActive() != active) {
                        role.setActive(active);
                        adminRoleRepository.save(role);
                    }
                });
            } catch (RuntimeException rejection) {
                failures.add(new BulkOperationResult.Failure(
                        id, ErrorCodes.of(rejection), rejection.getMessage()));
            }
        }
        return BulkOperationResult.of(ids.size(), failures);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_holders", description = "List operators holding an admin role")
    public List<RoleHolderDto> getRoleHolders(UUID id) {
        requireAdminRole(id);
        return adminUserRoleRepository.findAllWithOperatorByAdminRoleId(id).stream()
                .map(assignment -> {
                    var admin = assignment.getAdminUser();
                    var user = admin.getUser();
                    return new RoleHolderDto(
                            admin.getId(),
                            user.getEmail(),
                            user.getFirstName(),
                            user.getLastName(),
                            admin.isActive(),
                            admin.isSuperAdmin());
                })
                .sorted(java.util.Comparator.comparing(RoleHolderDto::email, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private AdminRole requireAdminRole(UUID id) {
        return adminRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole", "id", id));
    }

    /** Counts one role's assignments. Use the batched form when rendering a page of roles. */
    private long assignedOperatorCount(UUID roleId) {
        return adminUserRoleRepository.countByAdminRoleIdIn(List.of(roleId)).stream()
                .findFirst()
                .map(AdminUserRoleRepository.RoleAssignmentCount::getTotal)
                .orElse(0L);
    }

    private AdminRoleResponseDto toResponse(
            AdminRole role,
            List<AdminRolePermission> grants,
            long assignedOperatorCount
    ) {
        return new AdminRoleResponseDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isActive(),
                assignedOperatorCount,
                grants.stream()
                        .map(AdminRolePermission::getPermission)
                        .map(permission -> new AdminPermissionSummaryDto(
                                permission.getId(),
                                permission.getCode(),
                                permission.getName(),
                                permission.getDescription(),
                                permission.getAction(),
                                permission.getResource()))
                        .toList());
    }
}
