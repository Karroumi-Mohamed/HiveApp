package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.entity.AdminUserRole;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.service.AdminUserService;
import com.hiveapp.platform.admin.dto.AdminMeDto;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = AdminUsersFeature.KEY, description = "Admin User Management")
public class AdminUserServiceImpl extends PlatformControlFeatureService implements AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserRoleRepository adminUserRoleRepository;
    private final AdminRolePermissionRepository adminRolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final IdentityService identityService;

    @Override
    protected FeatureDefinition featureDefinition() {
        return AdminUsersFeature.definition();
    }

    @Override
    @PermissionNode(key = "read_detail", description = "Read an admin user")
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
        if (isSuperAdmin && !currentActorIsSuperAdmin()) {
            throw new InvalidPermissionGrantException("Only a SuperAdmin can create another SuperAdmin.");
        }
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

        var adminUser = getAdminUser(adminUserId);
        var adminRole = adminRoleRepository.findById(adminRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole", "id", adminRoleId));
        if (!adminRole.isActive()) {
            throw new InvalidStateException("Inactive admin roles cannot be assigned.");
        }
        requireActorCanAssignRole(adminRoleId);

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

    private boolean currentActorIsSuperAdmin() {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.actorUserId() == null) {
            return false;
        }
        return adminUserRepository.findByUserId(context.actorUserId())
                .filter(AdminUser::isActive)
                .map(AdminUser::isSuperAdmin)
                .orElse(false);
    }

    private void requireActorCanAssignRole(UUID adminRoleId) {
        if (currentActorIsSuperAdmin()) {
            return;
        }
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.actorUserId() == null) {
            throw new InvalidPermissionGrantException("An authenticated platform administrator is required to assign admin roles.");
        }
        var actor = adminUserRepository.findByUserId(context.actorUserId())
                .orElseThrow(() -> new InvalidPermissionGrantException("The acting user is not a platform administrator."));

        boolean exceedsActorPermissions = adminRolePermissionRepository.findAllByAdminRoleId(adminRoleId).stream()
                .anyMatch(grant -> !adminUserRepository.hasPermission(actor.getId(), grant.getPermission().getCode()));
        if (exceedsActorPermissions) {
            throw new InvalidPermissionGrantException(
                    "A platform administrator cannot assign a role containing permissions they do not hold.");
        }
    }
}
