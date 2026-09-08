package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;
import com.hiveapp.platform.admin.domain.constant.AdminRoleAction;
import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.dto.AdminPermissionSummaryDto;
import com.hiveapp.platform.admin.dto.AdminRoleImpactDto;
import com.hiveapp.platform.admin.dto.AdminRoleHistoryEntryDto;
import com.hiveapp.platform.admin.dto.AdminRolePresetDto;
import com.hiveapp.platform.admin.dto.AdminRoleResponseDto;
import com.hiveapp.platform.admin.dto.BulkOperationResult;
import com.hiveapp.platform.admin.dto.RoleHolderDto;
import com.hiveapp.platform.admin.service.AdminBulkExecutor;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.admin.service.AdminRoleAssignmentAudit;
import com.hiveapp.platform.admin.service.AdminRoleName;
import com.hiveapp.platform.admin.service.AdminRoleNameConflictException;
import com.hiveapp.platform.admin.service.AdminRolePresetCatalog;
import com.hiveapp.platform.admin.service.AdminRoleService;
import com.hiveapp.platform.admin.service.StaleAdminRoleImpactException;
import com.hiveapp.platform.registry.definition.AdminRolesFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.OperationBlockedException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = AdminRolesFeature.KEY, description = "Admin Role Management", guard = PermissionNode.Guard.ON)
public class AdminRoleServiceImpl extends PlatformControlFeatureService implements AdminRoleService {

    private final AdminRoleRepository adminRoleRepository;
    private final PermissionRepository permissionRepository;
    private final AdminRolePermissionRepository adminRolePermissionRepository;
    private final AdminUserRoleRepository adminUserRoleRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminBulkExecutor adminBulkExecutor;
    private final PermissionGrantValidator permissionGrantValidator;
    private final AdminMutationAuthorizer adminMutationAuthorizer;
    private final AdminRolePresetCatalog presetCatalog;
    private final AuditLogRepository auditLogRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    protected FeatureDefinition featureDefinition() {
        return AdminRolesFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_detail", description = "Read an admin role")
    public AdminRoleResponseDto getAdminRole(UUID id) {
        AdminRole role = requireAdminRole(id);
        List<AdminRolePermission> grants =
                adminRolePermissionRepository.findAllWithPermissionByAdminRoleIdIn(List.of(id));
        return toResponse(
                role,
                grants,
                assignedOperatorCount(id),
                adminMutationAuthorizer.currentActorGrantCeiling());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "List all admin roles")
    public Page<AdminRoleResponseDto> getAdminRoles(
            String search, AdminRoleStatus status, Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        org.springframework.data.domain.Sort.Order assignmentSort =
                pageable.getSort().getOrderFor("assignedOperatorCount");
        Page<AdminRole> roles;
        if (assignmentSort == null) {
            roles = adminRoleRepository.search(normalizedSearch, status, pageable);
        } else {
            Pageable unsortedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            roles = assignmentSort.isAscending()
                    ? adminRoleRepository.searchOrderByAssignmentCountAsc(normalizedSearch, status, unsortedPage)
                    : adminRoleRepository.searchOrderByAssignmentCountDesc(normalizedSearch, status, unsortedPage);
        }
        AdminMutationAuthorizer.GrantCeiling ceiling = adminMutationAuthorizer.currentActorGrantCeiling();
        if (roles.isEmpty()) {
            return roles.map(role -> toResponse(role, List.of(), 0L, ceiling));
        }
        List<UUID> roleIds = roles.stream().map(AdminRole::getId).toList();
        Map<UUID, List<AdminRolePermission>> grants = adminRolePermissionRepository
                .findAllWithPermissionByAdminRoleIdIn(roleIds)
                .stream()
                .collect(Collectors.groupingBy(grant -> grant.getAdminRole().getId()));
        Map<UUID, Long> assignmentCounts = adminUserRoleRepository
                .countByAdminRoleIdIn(roleIds)
                .stream()
                .collect(Collectors.toMap(
                        AdminUserRoleRepository.RoleAssignmentCount::getRoleId,
                        AdminUserRoleRepository.RoleAssignmentCount::getTotal));
        return roles.map(role -> toResponse(
                role,
                grants.getOrDefault(role.getId(), List.of()),
                assignmentCounts.getOrDefault(role.getId(), 0L),
                ceiling));
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create admin role")
    public AdminRoleResponseDto createAdminRole(
            String name, String description, List<UUID> permissionIds) {
        List<Permission> permissions = resolveGrantablePermissions(permissionIds);
        return createIndependentRole(name, description, permissions);
    }

    @Override
    @Transactional
    @PermissionNode(key = "create_from_preset", description = "Create admin role from preset")
    public AdminRoleResponseDto createAdminRoleFromPreset(
            String presetCode, String name, String description, List<UUID> permissionIds) {
        AdminRolePresetDto preset = presetCatalog.requireAvailable(presetCode);
        String resolvedDescription = description == null ? preset.description() : description;
        List<UUID> resolvedPermissionIds = permissionIds == null
                ? preset.permissions().stream().map(AdminPermissionSummaryDto::id).toList()
                : permissionIds;
        return createIndependentRole(
                name,
                resolvedDescription,
                resolveGrantablePermissions(resolvedPermissionIds));
    }

    @Override
    @Transactional
    @PermissionNode(key = "duplicate", description = "Duplicate an admin role")
    public AdminRoleResponseDto duplicateAdminRole(
            UUID sourceRoleId, String name, String description) {
        AdminRole source = requireAdminRole(sourceRoleId);
        adminMutationAuthorizer.requireCanManageRole(sourceRoleId, "duplicate");
        List<Permission> permissions = adminRolePermissionRepository
                .findAllWithPermissionByAdminRoleId(sourceRoleId)
                .stream()
                .map(AdminRolePermission::getPermission)
                .toList();
        AdminMutationAuthorizer.GrantCeiling ceiling = adminMutationAuthorizer.currentActorGrantCeiling();
        permissionGrantValidator.requirePlatformAdminRoleGrantablePermissions(
                permissions.stream().map(Permission::getCode).toList());
        permissions.forEach(permission -> {
            adminMutationAuthorizer.requireCanManagePermission(ceiling, permission.getCode(), "copy");
        });
        return createIndependentRole(
                name,
                description == null ? source.getDescription() : description,
                permissions);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update", description = "Update admin role metadata")
    public AdminRoleResponseDto updateAdminRole(
            UUID id, String name, String description, Long expectedVersion) {
        AdminRole role = requireAdminRole(id);
        adminMutationAuthorizer.requireCanManageRole(id, "update");
        requireVersion(role, expectedVersion);
        applyName(role, name);
        role.setDescription(normalizeDescription(description));
        markUpdated(role);
        saveRole(role);
        return currentResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list_presets", description = "List available admin role presets")
    public List<AdminRolePresetDto> getAvailablePresets() {
        return presetCatalog.availablePresets();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list_grantable_permissions", description = "List permissions grantable by the actor")
    public List<AdminPermissionSummaryDto> getGrantablePermissions() {
        AdminMutationAuthorizer.GrantCeiling ceiling = adminMutationAuthorizer.currentActorGrantCeiling();
        return permissionRepository.findAll().stream()
                .filter(permission -> permissionGrantValidator
                        .isPlatformAdminRoleGrantable(permission.getCode()))
                .filter(permission -> ceiling.allows(permission.getCode()))
                .sorted(Comparator.comparing(Permission::getCode))
                .map(AdminRoleServiceImpl::toPermissionSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_impact", description = "Preview an admin role change")
    public AdminRoleImpactDto previewImpact(
            UUID roleId, List<UUID> proposedPermissionIds, AdminRoleStatus proposedStatus) {
        AdminRole role = requireAdminRole(roleId);
        adminMutationAuthorizer.requireCanManageRole(roleId, "change");
        Set<String> current = currentPermissionCodes(roleId);
        Set<String> proposed = proposedPermissionIds == null
                ? current
                : resolveGrantablePermissions(proposedPermissionIds).stream()
                        .map(Permission::getCode)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        AdminRoleStatus targetStatus = proposedStatus == null ? role.getStatus() : proposedStatus;
        return impact(role, current, proposed, targetStatus);
    }

    @Override
    @Transactional
    @PermissionNode(key = "replace_permissions", description = "Replace an admin role permission set")
    public AdminRoleResponseDto replacePermissions(
            UUID roleId,
            List<UUID> permissionIds,
            Long expectedVersion,
            Long confirmedAssignmentCount) {
        AdminRole role = requireAdminRole(roleId);
        adminMutationAuthorizer.requireCanManageRole(roleId, "modify");
        List<Permission> proposedPermissions = resolveGrantablePermissions(permissionIds);
        Set<String> proposed = proposedPermissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        AdminRoleImpactDto preview = impact(
                role, currentPermissionCodes(roleId), proposed, role.getStatus());
        requireConfirmedImpact(preview, expectedVersion, confirmedAssignmentCount);
        if (preview.permissionsAdded().isEmpty() && preview.permissionsRemoved().isEmpty()) {
            return currentResponse(role);
        }

        adminRolePermissionRepository.deleteAllByAdminRoleId(roleId);
        adminRolePermissionRepository.flush();
        savePermissionAssignments(role, proposedPermissions);
        role.setPermissionRevision(role.getPermissionRevision() + 1);
        markUpdated(role);
        saveRole(role);
        return currentResponse(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "transition_status", description = "Transition an admin role lifecycle state")
    public AdminRoleResponseDto transitionStatus(
            UUID roleId,
            AdminRoleStatus status,
            Long expectedVersion,
            Long confirmedAssignmentCount) {
        AdminRole role = requireAdminRole(roleId);
        adminMutationAuthorizer.requireCanManageRole(roleId, "change status of");
        requireAllowedTransition(role.getStatus(), status);
        Set<String> currentPermissions = currentPermissionCodes(roleId);
        AdminRoleImpactDto preview = impact(role, currentPermissions, currentPermissions, status);
        requireConfirmedImpact(preview, expectedVersion, confirmedAssignmentCount);
        if (role.getStatus() != status) {
            role.setStatus(status);
            markUpdated(role);
            saveRole(role);
        }
        return currentResponse(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete", description = "Delete an unused admin role")
    public void deleteRole(UUID roleId) {
        AdminRole role = requireAdminRole(roleId);
        adminMutationAuthorizer.requireCanManageRole(roleId, "delete");
        if (role.isActive() || role.isEverAssigned() || adminUserRoleRepository.existsByAdminRoleId(roleId)) {
            throw new OperationBlockedException(
                    "Only an inactive role that has never been assigned can be deleted.",
                    List.of("Archive this role instead to retain its security history."));
        }
        adminRolePermissionRepository.deleteAllByAdminRoleId(roleId);
        adminRoleRepository.delete(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "toggle_active", description = "Activate or deactivate admin role")
    public void toggleActive(UUID id) {
        AdminRole role = requireAdminRole(id);
        adminMutationAuthorizer.requireCanManageRole(id, "activate or deactivate");
        if (assignedOperatorCount(id) > 0) {
            throw new OperationBlockedException(
                    "Roles with assigned operators require an impact preview before changing status.",
                    List.of("Use the explicit role status transition flow."));
        }
        if (role.getStatus() == AdminRoleStatus.ARCHIVED) {
            throw new InvalidStateException("Restore an archived role to inactive before activating it.");
        }
        role.setStatus(role.isActive() ? AdminRoleStatus.INACTIVE : AdminRoleStatus.ACTIVE);
        markUpdated(role);
        saveRole(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "grant_permission", description = "Grant admin permission to admin role")
    public void grantPermission(UUID adminRoleId, UUID permissionId) {
        if (adminRolePermissionRepository.existsByAdminRoleIdAndPermissionId(adminRoleId, permissionId)) {
            throw new DuplicateResourceException("AdminRolePermission", "permissionId", permissionId);
        }
        AdminRole role = requireAdminRole(adminRoleId);
        requireUnusedForLegacyPermissionMutation(adminRoleId);
        Permission permission = requirePermission(permissionId);
        permissionGrantValidator.requirePlatformAdminRoleGrantable(permission.getCode());
        adminMutationAuthorizer.requireCanManageRole(adminRoleId, "modify");
        adminMutationAuthorizer.requireCanManagePermission(permission.getCode(), "grant");
        savePermissionAssignments(role, List.of(permission));
        role.setPermissionRevision(role.getPermissionRevision() + 1);
        markUpdated(role);
        saveRole(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke_permission", description = "Revoke admin permission from admin role")
    public void revokePermission(UUID adminRoleId, UUID permissionId) {
        AdminRole role = requireAdminRole(adminRoleId);
        requireUnusedForLegacyPermissionMutation(adminRoleId);
        Permission permission = requirePermission(permissionId);
        adminMutationAuthorizer.requireCanManageRole(adminRoleId, "modify");
        adminMutationAuthorizer.requireCanManagePermission(permission.getCode(), "revoke");
        adminRolePermissionRepository.deleteByAdminRoleIdAndPermissionId(adminRoleId, permissionId);
        role.setPermissionRevision(role.getPermissionRevision() + 1);
        markUpdated(role);
        saveRole(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "bulk_set_active", description = "Activate or deactivate several admin roles")
    public BulkOperationResult setActiveBulk(List<UUID> ids, boolean active) {
        return adminBulkExecutor.run(ids, id -> {
            AdminRole role = requireAdminRole(id);
            adminMutationAuthorizer.requireCanManageRole(id, "activate or deactivate");
            if (assignedOperatorCount(id) > 0) {
                throw new OperationBlockedException(
                        "Assigned roles require a single-role impact preview.", List.of());
            }
            if (role.getStatus() == AdminRoleStatus.ARCHIVED) {
                throw new InvalidStateException("Archived roles cannot be changed by a bulk activation action.");
            }
            AdminRoleStatus status = active ? AdminRoleStatus.ACTIVE : AdminRoleStatus.INACTIVE;
            if (role.getStatus() != status) {
                role.setStatus(status);
                markUpdated(role);
                saveRole(role);
            }
        });
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
                .sorted(Comparator.comparing(RoleHolderDto::email, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_history", description = "Read admin role mutation history")
    public List<AdminRoleHistoryEntryDto> getRoleHistory(UUID id) {
        requireAdminRole(id);
        var logs = auditLogRepository
                .findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc("ADMIN_ROLE", id.toString());
        Set<UUID> actorUserIds = logs.stream()
                .map(com.hiveapp.shared.audit.domain.AuditLog::getActorUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> actorEmails = actorUserIds.isEmpty()
                ? Map.of()
                : adminUserRepository.findAllWithUserByUserIdIn(actorUserIds).stream()
                        .collect(Collectors.toMap(
                                admin -> admin.getUser().getId(),
                                admin -> admin.getUser().getEmail()));
        Map<UUID, UUID> subjectAdminUserIds = new HashMap<>();
        for (var log : logs) {
            UUID subjectId = assignmentSubject(log);
            if (subjectId != null) {
                subjectAdminUserIds.put(log.getId(), subjectId);
            }
        }
        Map<UUID, String> subjectEmails = subjectAdminUserIds.isEmpty()
                ? Map.of()
                : adminUserRepository.findAllWithUserByIdIn(Set.copyOf(subjectAdminUserIds.values())).stream()
                        .collect(Collectors.toMap(
                                com.hiveapp.platform.admin.domain.entity.AdminUser::getId,
                                admin -> admin.getUser().getEmail()));
        return logs.stream()
                .map(log -> {
                    UUID subjectId = subjectAdminUserIds.get(log.getId());
                    return new AdminRoleHistoryEntryDto(
                            log.getId(),
                            log.getOccurredAt(),
                            log.getActorUserId(),
                            actorEmails.get(log.getActorUserId()),
                            log.getAction(),
                            // The raw id is the honest fallback for an unresolvable operator; it
                            // still identifies who the event concerned.
                            subjectId == null
                                    ? null
                                    : subjectEmails.getOrDefault(subjectId, subjectId.toString()),
                            log.getOutcome(),
                            log.getFailureType());
                })
                .toList();
    }

    /**
     * The assigned or removed operator recorded inside an assignment audit payload, or null for
     * events without one. Payloads written before this contract existed simply resolve to null.
     */
    private UUID assignmentSubject(com.hiveapp.shared.audit.domain.AuditLog log) {
        if (!AdminRoleAssignmentAudit.ASSIGN_ACTION.equals(log.getAction())
                && !AdminRoleAssignmentAudit.REMOVE_ACTION.equals(log.getAction())) {
            return null;
        }
        if (log.getResultData() == null) {
            return null;
        }
        try {
            var subjectNode = objectMapper.readTree(log.getResultData())
                    .path(AdminRoleAssignmentAudit.AFTER_NODE)
                    .path(AdminRoleAssignmentAudit.ADMIN_USER_ID_KEY);
            return subjectNode.isTextual() ? UUID.fromString(subjectNode.asText()) : null;
        } catch (com.fasterxml.jackson.core.JacksonException | IllegalArgumentException unreadable) {
            return null;
        }
    }

    private AdminRoleResponseDto createIndependentRole(
            String name, String description, List<Permission> permissions) {
        AdminRole role = new AdminRole();
        applyName(role, name);
        role.setDescription(normalizeDescription(description));
        role.setStatus(AdminRoleStatus.INACTIVE);
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        role.setCreatedByUserId(actorUserId);
        role.setUpdatedByUserId(actorUserId);
        saveRole(role);
        savePermissionAssignments(role, permissions);
        if (!permissions.isEmpty()) {
            role.setPermissionRevision(role.getPermissionRevision() + 1);
            saveRole(role);
        }
        return currentResponse(role);
    }

    private void savePermissionAssignments(AdminRole role, Collection<Permission> permissions) {
        List<AdminRolePermission> assignments = permissions.stream()
                .map(permission -> {
                    AdminRolePermission assignment = new AdminRolePermission();
                    assignment.setAdminRole(role);
                    assignment.setPermission(permission);
                    return assignment;
                })
                .toList();
        adminRolePermissionRepository.saveAll(assignments);
    }

    private List<Permission> resolveGrantablePermissions(List<UUID> permissionIds) {
        Set<UUID> uniqueIds = new LinkedHashSet<>(permissionIds == null ? List.of() : permissionIds);
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Permission> byId = permissionRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        AdminMutationAuthorizer.GrantCeiling ceiling = adminMutationAuthorizer.currentActorGrantCeiling();
        List<Permission> permissions = uniqueIds.stream()
                .map(id -> {
                    Permission permission = byId.get(id);
                    if (permission == null) {
                        throw new ResourceNotFoundException("Permission", "id", id);
                    }
                    adminMutationAuthorizer.requireCanManagePermission(ceiling, permission.getCode(), "grant");
                    return permission;
                })
                .toList();
        permissionGrantValidator.requirePlatformAdminRoleGrantablePermissions(
                permissions.stream().map(Permission::getCode).toList());
        return permissions;
    }

    private AdminRoleImpactDto impact(
            AdminRole role,
            Set<String> currentPermissions,
            Set<String> proposedPermissions,
            AdminRoleStatus proposedStatus) {
        Set<String> addedCodes = proposedPermissions.stream()
                .filter(code -> !currentPermissions.contains(code))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> removedCodes = currentPermissions.stream()
                .filter(code -> !proposedPermissions.contains(code))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> effectiveLossCodes = new LinkedHashSet<>();

        if (role.getStatus() == AdminRoleStatus.ACTIVE && proposedStatus != AdminRoleStatus.ACTIVE) {
            removedCodes.addAll(currentPermissions);
            effectiveLossCodes.addAll(currentPermissions);
        } else if (role.getStatus() == AdminRoleStatus.ACTIVE) {
            effectiveLossCodes.addAll(removedCodes);
        } else if (role.getStatus() != AdminRoleStatus.ACTIVE && proposedStatus == AdminRoleStatus.ACTIVE) {
            addedCodes.addAll(proposedPermissions);
        }

        List<String> added = addedCodes.stream().sorted().toList();
        List<String> removed = removedCodes.stream().sorted().toList();
        long assignmentCount = assignedOperatorCount(role.getId());
        AccessLoss accessLoss = accessLoss(role.getId(), effectiveLossCodes);
        boolean changed = role.getStatus() != proposedStatus
                || !currentPermissions.equals(proposedPermissions);
        return new AdminRoleImpactDto(
                role.getId(),
                role.getVersion(),
                role.getStatus(),
                proposedStatus,
                assignmentCount,
                added,
                removed,
                accessLoss.operatorCount(),
                accessLoss.actorAffected(),
                changed);
    }

    private AccessLoss accessLoss(UUID roleId, Set<String> removedPermissionCodes) {
        if (removedPermissionCodes.isEmpty()) {
            return AccessLoss.NONE;
        }
        List<UUID> holderIds = adminUserRoleRepository.findAllAdminUserIdsByAdminRoleId(roleId);
        if (holderIds.isEmpty()) {
            return AccessLoss.NONE;
        }

        List<com.hiveapp.platform.admin.domain.entity.AdminUserRole> otherAssignments =
                adminUserRoleRepository.findAllWithRoleByAdminUserIdIn(holderIds).stream()
                        .filter(assignment -> !assignment.getAdminRole().getId().equals(roleId))
                        .filter(assignment -> assignment.getAdminRole().isActive())
                        .toList();
        Set<UUID> otherRoleIds = otherAssignments.stream()
                .map(assignment -> assignment.getAdminRole().getId())
                .collect(Collectors.toSet());
        Map<UUID, Set<String>> permissionsByRole = new HashMap<>();
        if (!otherRoleIds.isEmpty()) {
            adminRolePermissionRepository.findAllWithPermissionByAdminRoleIdIn(otherRoleIds)
                    .forEach(grant -> permissionsByRole
                            .computeIfAbsent(grant.getAdminRole().getId(), ignored -> new LinkedHashSet<>())
                            .add(grant.getPermission().getCode()));
        }

        Map<UUID, Set<String>> otherEffectivePermissions = new HashMap<>();
        for (var assignment : otherAssignments) {
            otherEffectivePermissions
                    .computeIfAbsent(assignment.getAdminUser().getId(), ignored -> new LinkedHashSet<>())
                    .addAll(permissionsByRole.getOrDefault(assignment.getAdminRole().getId(), Set.of()));
        }

        List<UUID> affected = new ArrayList<>();
        for (UUID holderId : holderIds) {
            Set<String> alternatives = otherEffectivePermissions.getOrDefault(holderId, Set.of());
            if (removedPermissionCodes.stream().anyMatch(code -> !alternatives.contains(code))) {
                affected.add(holderId);
            }
        }
        UUID actorAdminUserId = adminMutationAuthorizer.currentActorAdminUserId();
        return new AccessLoss(affected.size(), affected.contains(actorAdminUserId));
    }

    private record AccessLoss(long operatorCount, boolean actorAffected) {
        private static final AccessLoss NONE = new AccessLoss(0, false);
    }

    private void requireConfirmedImpact(
            AdminRoleImpactDto preview, Long expectedVersion, Long confirmedAssignmentCount) {
        if (!preview.confirmationRequired()) {
            return;
        }
        if (expectedVersion == null
                || confirmedAssignmentCount == null
                || expectedVersion != preview.version()
                || confirmedAssignmentCount != preview.assignmentCount()) {
            throw new StaleAdminRoleImpactException();
        }
    }

    private void requireAllowedTransition(AdminRoleStatus current, AdminRoleStatus proposed) {
        if (current == proposed) {
            return;
        }
        boolean allowed = switch (current) {
            case INACTIVE -> proposed == AdminRoleStatus.ACTIVE || proposed == AdminRoleStatus.ARCHIVED;
            case ACTIVE -> proposed == AdminRoleStatus.INACTIVE || proposed == AdminRoleStatus.ARCHIVED;
            case ARCHIVED -> proposed == AdminRoleStatus.INACTIVE;
        };
        if (!allowed) {
            throw new InvalidStateException(
                    "Admin role cannot transition from " + current + " to " + proposed + ".");
        }
    }

    private void requireVersion(AdminRole role, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion != role.getVersion()) {
            throw new StaleAdminRoleImpactException();
        }
    }

    private void requireUnusedForLegacyPermissionMutation(UUID roleId) {
        if (assignedOperatorCount(roleId) > 0) {
            throw new OperationBlockedException(
                    "Assigned roles require an impact preview before permissions change.",
                    List.of("Use the permission-set replacement flow."));
        }
    }

    private Set<String> currentPermissionCodes(UUID roleId) {
        return adminRolePermissionRepository.findAllWithPermissionByAdminRoleId(roleId).stream()
                .map(AdminRolePermission::getPermission)
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AdminRole requireAdminRole(UUID id) {
        return adminRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole", "id", id));
    }

    private Permission requirePermission(UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));
    }

    private void applyName(AdminRole role, String value) {
        AdminRoleName name = AdminRoleName.of(value);
        boolean collision = role.getId() == null
                ? adminRoleRepository.existsByNormalizedName(name.normalized())
                : adminRoleRepository.existsByNormalizedNameAndIdNot(name.normalized(), role.getId());
        if (collision) {
            throw new AdminRoleNameConflictException(name.display());
        }
        role.setName(name.display());
        role.setNormalizedName(name.normalized());
    }

    private void saveRole(AdminRole role) {
        try {
            adminRoleRepository.saveAndFlush(role);
        } catch (DataIntegrityViolationException conflict) {
            throw new AdminRoleNameConflictException(role.getName());
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException stale) {
            throw new StaleAdminRoleImpactException();
        }
    }

    private void markUpdated(AdminRole role) {
        role.setUpdatedByUserId(adminMutationAuthorizer.currentActorUserId());
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private long assignedOperatorCount(UUID roleId) {
        return adminUserRoleRepository.countByAdminRoleId(roleId);
    }

    private AdminRoleResponseDto currentResponse(AdminRole role) {
        return toResponse(
                role,
                adminRolePermissionRepository.findAllWithPermissionByAdminRoleIdIn(List.of(role.getId())),
                assignedOperatorCount(role.getId()),
                adminMutationAuthorizer.currentActorGrantCeiling());
    }

    private AdminRoleResponseDto toResponse(
            AdminRole role,
            List<AdminRolePermission> grants,
            long assignedOperatorCount,
            AdminMutationAuthorizer.GrantCeiling ceiling) {
        boolean deletable = !role.isActive() && !role.isEverAssigned() && assignedOperatorCount == 0;
        return new AdminRoleResponseDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getStatus(),
                role.isActive(),
                role.getVersion(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                deletable,
                assignedOperatorCount,
                grants.stream()
                        .map(AdminRolePermission::getPermission)
                        .sorted(Comparator.comparing(Permission::getCode))
                        .map(AdminRoleServiceImpl::toPermissionSummary)
                        .toList(),
                availableActions(role, grants, deletable, ceiling));
    }

    private Set<AdminRoleAction> availableActions(
            AdminRole role,
            List<AdminRolePermission> grants,
            boolean deletable,
            AdminMutationAuthorizer.GrantCeiling ceiling) {
        EnumSet<AdminRoleAction> actions = EnumSet.noneOf(AdminRoleAction.class);
        boolean manageable = grants.stream()
                .map(AdminRolePermission::getPermission)
                .map(Permission::getCode)
                .allMatch(ceiling::allows);
        addIf(actions, AdminRoleAction.READ_DETAIL, ceiling.allows("platform.roles.read_detail"));
        addIf(actions, AdminRoleAction.READ_OPERATORS, ceiling.allows("platform.roles.read_holders"));
        addIf(actions, AdminRoleAction.READ_HISTORY, ceiling.allows("platform.roles.read_history"));
        addIf(actions, AdminRoleAction.DUPLICATE, manageable && ceiling.allows("platform.roles.duplicate"));
        addIf(actions,
                AdminRoleAction.ASSIGN_TO_OPERATOR,
                manageable
                        && role.getStatus() == AdminRoleStatus.ACTIVE
                        && (ceiling.allows("platform.admin_users.assign_role")
                                || ceiling.allows("platform.admin_users.bulk_assign_role")));
        addIf(actions,
                AdminRoleAction.EDIT_METADATA,
                manageable && role.getStatus() != AdminRoleStatus.ARCHIVED
                        && ceiling.allows("platform.roles.update"));
        addIf(actions,
                AdminRoleAction.EDIT_PERMISSIONS,
                manageable && role.getStatus() != AdminRoleStatus.ARCHIVED
                        && ceiling.allows("platform.roles.preview_impact")
                        && ceiling.allows("platform.roles.replace_permissions"));
        addIf(actions,
                AdminRoleAction.TRANSITION_STATUS,
                manageable
                        && ceiling.allows("platform.roles.preview_impact")
                        && ceiling.allows("platform.roles.transition_status"));
        addIf(actions,
                AdminRoleAction.DELETE,
                manageable && deletable && ceiling.allows("platform.roles.delete"));
        return Set.copyOf(actions);
    }

    private static void addIf(Set<AdminRoleAction> actions, AdminRoleAction action, boolean condition) {
        if (condition) {
            actions.add(action);
        }
    }

    private static AdminPermissionSummaryDto toPermissionSummary(Permission permission) {
        return new AdminPermissionSummaryDto(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getAction(),
                permission.getResource());
    }
}
