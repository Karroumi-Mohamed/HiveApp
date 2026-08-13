package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.entity.AdminUserRole;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.admin.service.AdminUserService;
import com.hiveapp.platform.admin.service.AdminBulkExecutor;
import com.hiveapp.platform.admin.service.AdminPermissionResolver;
import com.hiveapp.platform.admin.dto.AdminMeDto;
import com.hiveapp.platform.admin.dto.AdminAccessOverviewDto;
import com.hiveapp.platform.admin.dto.AdminRoleSummaryDto;
import com.hiveapp.platform.admin.dto.AdminUserResponseDto;
import com.hiveapp.platform.admin.dto.AdminUserCreationResponse;
import com.hiveapp.platform.admin.dto.AdminOperatorAccessResponse;
import com.hiveapp.platform.admin.dto.BulkOperationResult;
import com.hiveapp.identity.domain.constant.IdentityKind;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.identity.service.MemberCredentialService;
import com.hiveapp.identity.service.NewUserCommand;
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
import java.util.Locale;
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
    private final MemberCredentialService memberCredentialService;
    private final AdminMutationAuthorizer adminMutationAuthorizer;
    private final AdminPermissionResolver adminPermissionResolver;
    private final AdminBulkExecutor adminBulkExecutor;

    @Override
    protected FeatureDefinition featureDefinition() {
        return AdminUsersFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "overview", description = "View platform access overview")
    public AdminAccessOverviewDto getAccessOverview() {
        return new AdminAccessOverviewDto(
                adminUserRepository.count(),
                adminUserRepository.countByIsActiveTrue(),
                adminUserRepository.countByIsActiveFalse(),
                adminUserRepository.countByIsSuperAdminTrue(),
                adminRoleRepository.count(),
                adminRoleRepository.countByIsActiveTrue(),
                adminRoleRepository.countByIsActiveFalse());
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
    public Page<AdminUserResponseDto> getAdminUsers(String search, Boolean active, Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Page<AdminUser> admins = adminUserRepository.searchPageWithUser(normalizedSearch, active, pageable);
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

    /**
     * Creates the operator's identity and their platform grant in one transaction. Operators are
     * never promoted out of the client user pool, so there is no candidate to look up: if the
     * email is already in use — by a client member or another operator — identity rejects it and
     * the whole creation rolls back.
     */
    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create admin user")
    public AdminUserCreationResponse createAdminUser(
            String firstName, String lastName, String email,
            InitialAccessMethod initialAccessMethod,
            boolean isSuperAdmin) {
        if (isSuperAdmin && !adminMutationAuthorizer.currentActorIsSuperAdmin()) {
            throw new InvalidPermissionGrantException("Only a SuperAdmin can create another SuperAdmin.");
        }

        // Entity door: the managed row is needed to own the AdminUser @OneToOne relationship.
        User user = identityService.createUser(NewUserCommand.platformOperator(
                generateOperatorUsername(),
                email.trim().toLowerCase(Locale.ROOT),
                firstName.trim(),
                lastName.trim()));
        var credentials = switch (initialAccessMethod) {
            case EMAIL_LINK -> memberCredentialService.initializeForOperator(user);
            case TEMPORARY_PASSWORD -> memberCredentialService.generateOperatorTemporaryAccess(user);
        };

        // Holds by construction today. Asserted so that any future promotion path has to
        // confront the rule rather than quietly bypass it.
        if (user.getKind() != IdentityKind.PLATFORM) {
            throw new InvalidStateException(
                    "Only a platform identity can hold platform administration.");
        }

        AdminUser adminUser = new AdminUser();
        adminUser.setUser(user);
        adminUser.setSuperAdmin(isSuperAdmin);
        adminUser.setActive(true);
        return AdminUserCreationResponse.of(
                toResponse(adminUserRepository.save(adminUser), List.of()), credentials);
    }

    /**
     * The operator's resolved authority, not merely the roles attached to them. Assigning a role
     * is only meaningful if you can see what it actually grants; the screen that assigns roles
     * previously had no way to show that.
     */
    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_permissions", description = "View an operator's effective permissions")
    public List<String> getEffectivePermissions(UUID id) {
        return adminPermissionResolver.resolve(requireAdminUser(id)).stream().sorted().toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = "bulk_set_active", description = "Activate or deactivate several operators")
    public BulkOperationResult setActiveBulk(List<UUID> ids, boolean active) {
        return runBulk(ids, id -> {
            AdminUser adminUser = requireAdminUser(id);
            adminMutationAuthorizer.requireCanModifyAdmin(adminUser);
            if (adminUser.isActive() && !active && isCurrentActor(adminUser)) {
                throw new InvalidStateException("An administrator cannot deactivate their own account.");
            }
            if (adminUser.isActive() != active) {
                adminUser.setActive(active);
                adminUserRepository.save(adminUser);
            }
        });
    }

    @Override
    @Transactional
    @PermissionNode(key = "bulk_assign_role", description = "Assign one admin role to several operators")
    public BulkOperationResult assignRoleBulk(List<UUID> ids, UUID adminRoleId) {
        return runBulk(ids, id -> assignRole(id, adminRoleId));
    }

    @Override
    @Transactional
    @PermissionNode(key = "bulk_resend_activation",
            description = "Resend activation to several operators")
    public BulkOperationResult resendActivationBulk(List<UUID> ids) {
        return runBulk(ids, this::resendActivation);
    }

    /**
     * Applies an operation to every id, isolating each so one rejection cannot undo the rest, and
     * collecting the reasons instead of failing the whole request on the first one.
     */
    private BulkOperationResult runBulk(List<UUID> ids, java.util.function.Consumer<UUID> operation) {
        return adminBulkExecutor.run(ids, operation);
    }

    /**
     * Corrects an operator's name. Deliberately single-target: a name identifies one person, so
     * there is no coherent bulk form of this operation.
     */
    @Override
    @Transactional
    @PermissionNode(key = "rename", description = "Correct an operator's name")
    public AdminUserResponseDto renameOperator(UUID id, String firstName, String lastName) {
        AdminUser adminUser = requireAdminUser(id);
        adminMutationAuthorizer.requireCanModifyAdmin(adminUser);
        identityService.renameUser(adminUser.getUser().getId(), firstName, lastName);
        return toResponse(adminUser, adminUserRoleRepository.findAllByAdminUserId(id));
    }

    /**
     * Re-sends the activation email. Separate permission from creation: handing someone the
     * ability to re-trigger delivery is not the same as letting them mint new operators.
     */
    @Override
    @Transactional
    @PermissionNode(key = "resend_activation", description = "Resend operator activation email")
    public AdminOperatorAccessResponse resendActivation(UUID id) {
        AdminUser adminUser = requireAdminUser(id);
        adminMutationAuthorizer.requireCanModifyAdmin(adminUser);
        return AdminOperatorAccessResponse.of(
                memberCredentialService.resendOperatorActivation(adminUser.getUser()));
    }

    /**
     * Explicit fallback for when email delivery fails. Its own permission because the password
     * is shown to the acting administrator and has to travel out of band — a strictly more
     * sensitive act than resending a link to the operator's own inbox.
     */
    @Override
    @Transactional
    @PermissionNode(key = "generate_temporary_access",
            description = "Issue a temporary password for an operator")
    public AdminOperatorAccessResponse generateTemporaryAccess(UUID id) {
        AdminUser adminUser = requireAdminUser(id);
        adminMutationAuthorizer.requireCanModifyAdmin(adminUser);
        return AdminOperatorAccessResponse.of(
                memberCredentialService.generateOperatorTemporaryAccess(adminUser.getUser()));
    }

    /**
     * Operators supply no username. Mirrors the bootstrap seeder's scheme rather than inventing a
     * second one, and stays inside the 50-character column.
     */
    private static String generateOperatorUsername() {
        return "op-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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
                admin.getUser().getFirstName(),
                admin.getUser().getLastName(),
                admin.isSuperAdmin(),
                admin.isActive(),
                admin.getUser().getCredentialState(),
                assignments.stream()
                        .map(AdminUserRole::getAdminRole)
                        .map(role -> new AdminRoleSummaryDto(
                                role.getId(), role.getName(), role.getDescription(), role.isActive()))
                        .toList());
    }

}
