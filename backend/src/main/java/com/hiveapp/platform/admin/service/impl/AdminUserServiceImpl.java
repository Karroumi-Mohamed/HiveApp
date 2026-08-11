package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.entity.AdminUserRole;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.admin.service.AdminUserService;
import com.hiveapp.platform.admin.dto.AdminMeDto;
import com.hiveapp.platform.admin.dto.AdminRoleSummaryDto;
import com.hiveapp.platform.admin.dto.AdminUserResponseDto;
import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.platform.registry.definition.AdminUsersFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = AdminUsersFeature.KEY, description = "Admin User Management", guard = PermissionNode.Guard.ON)
public class AdminUserServiceImpl extends PlatformControlFeatureService implements AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserRoleRepository adminUserRoleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final IdentityService identityService;
    private final AdminMutationAuthorizer adminMutationAuthorizer;

    @Override
    protected FeatureDefinition featureDefinition() {
        return AdminUsersFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_detail", description = "Read an admin user")
    public AdminUserResponseDto getAdminUser(UUID id) {
        AdminUser admin = requireAdminUser(id);
        return toResponse(admin, adminUserRoleRepository
                .findAllWithRoleByAdminUserIdIn(List.of(id)));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "List all admin users")
    public Page<AdminUserResponseDto> getAdminUsers(Pageable pageable) {
        Page<AdminUser> admins = adminUserRepository.findPageWithUser(pageable);
        if (admins.isEmpty()) {
            return admins.map(admin -> toResponse(admin, List.of()));
        }
        Map<UUID, List<AdminUserRole>> assignments = adminUserRoleRepository
                .findAllWithRoleByAdminUserIdIn(
                        admins.stream().map(AdminUser::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getAdminUser().getId()));
        return admins.map(admin -> toResponse(
                admin, assignments.getOrDefault(admin.getId(), List.of())));
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create admin user")
    public AdminUserResponseDto createAdminUser(UUID userId, boolean isSuperAdmin) {
        if (adminUserRepository.findByUserId(userId).isPresent()) {
            throw new DuplicateResourceException("AdminUser", "userId", userId);
        }
        if (isSuperAdmin && !adminMutationAuthorizer.currentActorIsSuperAdmin()) {
            throw new InvalidPermissionGrantException("Only a SuperAdmin can create another SuperAdmin.");
        }
        // Entity door: the managed row is needed to own the AdminUser @OneToOne relationship.
        var user = identityService.requireManagedUser(userId);

        AdminUser adminUser = new AdminUser();
        adminUser.setUser(user);
        adminUser.setSuperAdmin(isSuperAdmin);
        adminUser.setActive(true);
        return toResponse(adminUserRepository.save(adminUser), List.of());
    }

    @Override
    @Transactional
    @PermissionNode(key = "toggle_active", description = "Activate or deactivate admin user")
    public void toggleActive(UUID id) {
        var adminUser = requireAdminUser(id);
        adminMutationAuthorizer.requireCanModifyAdmin(adminUser);
        if (adminUser.isActive() && isCurrentActor(adminUser)) {
            throw new InvalidStateException("An administrator cannot deactivate their own account.");
        }
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

        var adminUser = requireAdminUser(adminUserId);
        var adminRole = adminRoleRepository.findById(adminRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole", "id", adminRoleId));
        if (!adminRole.isActive()) {
            throw new InvalidStateException("Inactive admin roles cannot be assigned.");
        }
        adminMutationAuthorizer.requireCanModifyAdmin(adminUser);
        adminMutationAuthorizer.requireCanManageRole(adminRoleId, "assign");

        AdminUserRole aur = new AdminUserRole();
        aur.setAdminUser(adminUser);
        aur.setAdminRole(adminRole);
        adminUserRoleRepository.save(aur);
    }

    @Override
    @Transactional
    @PermissionNode(key = "remove_role", description = "Remove admin role from admin user")
    public void removeRole(UUID adminUserId, UUID adminRoleId) {
        var adminUser = requireAdminUser(adminUserId);
        adminRoleRepository.findById(adminRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole", "id", adminRoleId));
        adminMutationAuthorizer.requireCanModifyAdmin(adminUser);
        adminMutationAuthorizer.requireCanManageRole(adminRoleId, "remove");
        adminUserRoleRepository.deleteByAdminUserIdAndAdminRoleId(adminUserId, adminRoleId);
    }

    // ── No @PermissionNode — internal bootstrap endpoint, no sieve needed ──

    @Override
    @Transactional(readOnly = true)
    public AdminMeDto getAdminDetails(UUID userId) {
        var admin = adminUserRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "userId", userId));

        Set<String> permissions;
        if (admin.isSuperAdmin()) {
            permissions = permissionRepository.findAll()
                    .stream()
                    .filter(permission -> {
                        try {
                            permissionGrantValidator.requirePlatformAdminRoleGrantable(permission.getCode());
                            return true;
                        } catch (InvalidPermissionGrantException ignored) {
                            return false;
                        }
                    })
                    .map(p -> p.getCode())
                    .collect(Collectors.toSet());
        } else {
            permissions = new HashSet<>(adminUserRepository.findAllPermissionCodes(admin.getId()));
        }

        return new AdminMeDto(
                admin.getId(),
                admin.getUser().getEmail(),
                admin.isSuperAdmin(),
                admin.isActive(),
                permissions
        );
    }

    private boolean isCurrentActor(AdminUser target) {
        var context = HiveAppContextHolder.getContext();
        return context != null
                && context.actorUserId() != null
                && target.getUser() != null
                && context.actorUserId().equals(target.getUser().getId());
    }

    private AdminUser requireAdminUser(UUID id) {
        return adminUserRepository.findWithUserById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "id", id));
    }

    private AdminUserResponseDto toResponse(
            AdminUser admin,
            List<AdminUserRole> assignments
    ) {
        return new AdminUserResponseDto(
                admin.getId(),
                admin.getUser().getId(),
                admin.getUser().getEmail(),
                admin.isSuperAdmin(),
                admin.isActive(),
                assignments.stream()
                        .map(AdminUserRole::getAdminRole)
                        .map(role -> new AdminRoleSummaryDto(
                                role.getId(), role.getName(), role.getDescription(), role.isActive()))
                        .toList());
    }

}
