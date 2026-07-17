package com.hiveapp.platform.client.role.service.impl;

import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.client.role.domain.constant.RoleChangeType;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.client.role.domain.repository.RolePermissionRepository;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.role.dto.RoleImpactDto;
import com.hiveapp.platform.client.role.dto.RoleImpactScopeDto;
import com.hiveapp.platform.client.role.service.RoleService;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.WorkspaceRolesFeature;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.dto.PermissionPickerModuleDto;
import com.hiveapp.platform.registry.service.PermissionPickerCatalogService;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.OperationBlockedException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = WorkspaceRolesFeature.KEY, description = "Role Management", guard = PermissionNode.Guard.ON)
public class RoleServiceImpl extends ClientWorkspaceFeatureService implements RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final PermissionPickerCatalogService permissionPickerCatalogService;
    private final PlanEntitlementService planEntitlementService;

    @Override
    protected FeatureDefinition featureDefinition() {
        return WorkspaceRolesFeature.definition();
    }

    @Override
    @PermissionNode(key = WorkspaceRolesFeature.READ, description = "View role details")
    public Role getRole(UUID id) {
        UUID accountId = currentAccountId();
        var role = roleRepository.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
        requireB2bRoleScope(role);
        return role;
    }

    @Override
    @PermissionNode(key = WorkspaceRolesFeature.VIEW, description = "View roles")
    public List<Role> getAccountRoles(UUID accountId) {
        requireCurrentAccount(accountId);
        if (isB2bContext()) {
            throw new ForbiddenException("B2B role access must use the collaboration Company scope");
        }
        return roleRepository.findAllByAccountId(accountId);
    }

    @Override
    @PermissionNode(key = WorkspaceRolesFeature.VIEW_COMPANY, description = "View company-scoped roles")
    public List<Role> getCompanyRoles(UUID companyId) {
        UUID accountId = currentAccountId();
        var company = companyRepository.findByIdAndAccountId(companyId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        requireB2bTargetCompany(companyId);
        return roleRepository.findAllByCompanyId(companyId);
    }

    @Override
    @Transactional
    @PermissionNode(key = WorkspaceRolesFeature.CREATE, description = "Create custom role")
    public Role createRole(UUID accountId, UUID companyId, String name, String description) {
        requireCurrentAccount(accountId);
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var company = companyId != null
                ? companyRepository.findByIdAndAccountId(companyId, accountId)
                        .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId))
                : null;
        if (isB2bContext()) {
            if (companyId == null) {
                throw new ForbiddenException("B2B access cannot create Account-wide roles");
            }
            requireB2bTargetCompany(companyId);
        }

        Role role = new Role();
        role.setAccount(account);
        role.setCompany(company);
        role.setName(normalizeRequired(name, "Role name"));
        role.setDescription(normalizeOptional(description));
        role.setStatus(RoleStatus.INACTIVE);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = WorkspaceRolesFeature.UPDATE, description = "Update role metadata")
    public Role updateRole(
            UUID roleId, String name, String description,
            Long expectedVersion, Long confirmedAssignmentCount) {
        Role role = requireLockedRole(roleId);
        requireMutableCustomRole(role);
        RoleImpactDto impact = impact(role, RoleChangeType.UPDATE, null);
        requireConfirmedImpact(impact, expectedVersion, confirmedAssignmentCount);
        role.setName(normalizeRequired(name, "Role name"));
        role.setDescription(normalizeOptional(description));
        return roleRepository.saveAndFlush(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = WorkspaceRolesFeature.DELETE, description = "Delete unused custom role")
    public void deleteRole(UUID roleId) {
        Role role = requireLockedRole(roleId);
        requireCustomRole(role, "deleted");
        List<MemberRole> assignments = memberRoleRepository.findAllByRoleId(roleId);
        if (role.isEverAssigned() || !assignments.isEmpty()) {
            throw new OperationBlockedException(
                    "Used roles cannot be deleted; deactivate or archive the role instead",
                    List.of(
                            "everAssigned: " + role.isEverAssigned(),
                            "currentAssignments: " + assignments.size()));
        }
        roleRepository.delete(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = WorkspaceRolesFeature.GRANT, description = "Grant permission brick to role")
    public Role addPermissionToRole(
            UUID roleId, String permissionCode,
            Long expectedVersion, Long confirmedAssignmentCount) {
        Role role = requireLockedRole(roleId);
        requireMutableCustomRole(role);
        if (role.getCompany() != null && !role.getCompany().isActive()) {
            throw new InvalidStateException("Permissions cannot be added to a role while its company is inactive");
        }

        if (rolePermissionRepository.existsByRoleIdAndPermissionCode(roleId, permissionCode)) {
            throw new DuplicateResourceException("RolePermission", "permissionCode", permissionCode);
        }

        var permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        permissionGrantValidator.requireClientRoleGrantable(permission);
        if (!planEntitlementService.isPermissionEntitled(role.getAccount().getId(), permissionCode)) {
            throw new InvalidPermissionGrantException(
                    "Permission " + permissionCode + " is not available in the current plan entitlement.");
        }

        RoleImpactDto impact = impact(role, RoleChangeType.ADD_PERMISSION, permissionCode);
        requireConfirmedImpact(impact, expectedVersion, confirmedAssignmentCount);

        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(permission);
        rolePermissionRepository.saveAndFlush(rp);
        role.getPermissions().add(rp);
        role.setDefinitionRevision(role.getDefinitionRevision() + 1);
        return roleRepository.saveAndFlush(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = WorkspaceRolesFeature.REVOKE, description = "Revoke permission brick from role")
    public Role removePermissionFromRole(
            UUID roleId, String permissionCode,
            Long expectedVersion, Long confirmedAssignmentCount) {
        Role role = requireLockedRole(roleId);
        requireMutableCustomRole(role);
        if (!rolePermissionRepository.existsByRoleIdAndPermissionCode(roleId, permissionCode)) {
            throw new ResourceNotFoundException("RolePermission", "permissionCode", permissionCode);
        }
        RoleImpactDto impact = impact(role, RoleChangeType.REMOVE_PERMISSION, permissionCode);
        requireConfirmedImpact(impact, expectedVersion, confirmedAssignmentCount);
        rolePermissionRepository.deleteByRoleIdAndPermissionCode(roleId, permissionCode);
        role.getPermissions().removeIf(rp -> permissionCode.equals(rp.getPermission().getCode()));
        role.setDefinitionRevision(role.getDefinitionRevision() + 1);
        return roleRepository.saveAndFlush(role);
    }

    @Override
    @PermissionNode(key = WorkspaceRolesFeature.IMPACT, description = "Preview role change impact")
    public RoleImpactDto previewRoleImpact(UUID roleId, RoleChangeType changeType, String permissionCode) {
        Role role = getRole(roleId);
        validateImpactRequest(role, changeType, permissionCode);
        return impact(role, changeType, permissionCode);
    }

    @Override
    @Transactional
    @PermissionNode(key = WorkspaceRolesFeature.ACTIVATE, description = "Activate custom role")
    public Role activateRole(UUID roleId, Long expectedVersion, Long confirmedAssignmentCount) {
        Role role = requireLockedRole(roleId);
        requireCustomRole(role, "activated");
        if (role.getStatus() == RoleStatus.ARCHIVED) {
            throw new InvalidStateException("Archived roles are terminal and cannot be activated");
        }
        if (role.getStatus() == RoleStatus.ACTIVE) return role;
        validateActivation(role);
        RoleImpactDto impact = impact(role, RoleChangeType.ACTIVATE, null);
        requireConfirmedImpact(impact, expectedVersion, confirmedAssignmentCount);
        role.setStatus(RoleStatus.ACTIVE);
        return roleRepository.saveAndFlush(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = WorkspaceRolesFeature.DEACTIVATE, description = "Deactivate custom role")
    public Role deactivateRole(UUID roleId, Long expectedVersion, Long confirmedAssignmentCount) {
        Role role = requireLockedRole(roleId);
        requireCustomRole(role, "deactivated");
        if (role.getStatus() == RoleStatus.ARCHIVED) {
            throw new InvalidStateException("Archived roles are terminal and cannot be deactivated");
        }
        if (role.getStatus() == RoleStatus.INACTIVE) return role;
        RoleImpactDto impact = impact(role, RoleChangeType.DEACTIVATE, null);
        requireConfirmedImpact(impact, expectedVersion, confirmedAssignmentCount);
        role.setStatus(RoleStatus.INACTIVE);
        return roleRepository.saveAndFlush(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = WorkspaceRolesFeature.ARCHIVE, description = "Archive custom role")
    public Role archiveRole(UUID roleId, Long expectedVersion, Long confirmedAssignmentCount) {
        Role role = requireLockedRole(roleId);
        requireCustomRole(role, "archived");
        if (role.getStatus() == RoleStatus.ARCHIVED) return role;
        RoleImpactDto impact = impact(role, RoleChangeType.ARCHIVE, null);
        requireConfirmedImpact(impact, expectedVersion, confirmedAssignmentCount);
        role.setStatus(RoleStatus.ARCHIVED);
        return roleRepository.saveAndFlush(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = WorkspaceRolesFeature.DUPLICATE, description = "Duplicate role for staged rollout")
    public Role duplicateRole(UUID roleId, String name, String description) {
        Role source = requireLockedRole(roleId);
        Role duplicate = new Role();
        duplicate.setAccount(source.getAccount());
        duplicate.setCompany(source.getCompany());
        duplicate.setName(normalizeRequired(name, "Role name"));
        duplicate.setDescription(normalizeOptional(description));
        duplicate.setStatus(RoleStatus.INACTIVE);
        duplicate = roleRepository.saveAndFlush(duplicate);
        for (RolePermission sourcePermission : source.getPermissions()) {
            RolePermission copy = new RolePermission();
            copy.setRole(duplicate);
            copy.setPermission(sourcePermission.getPermission());
            duplicate.getPermissions().add(rolePermissionRepository.save(copy));
        }
        rolePermissionRepository.flush();
        if (!duplicate.getPermissions().isEmpty()) {
            duplicate.setDefinitionRevision(1);
            duplicate = roleRepository.saveAndFlush(duplicate);
        }
        return duplicate;
    }

    @Override
    @PermissionNode(key = WorkspaceRolesFeature.PERMISSION_CATALOG, description = "View grantable role permissions")
    public List<PermissionPickerModuleDto> getPermissionCatalog(UUID accountId) {
        requireCurrentAccount(accountId);
        return permissionPickerCatalogService.clientRoleCatalog(accountId);
    }

    private void requireCurrentAccount(UUID accountId) {
        UUID currentAccountId = currentAccountId();
        if (!accountId.equals(currentAccountId)) {
            throw new ForbiddenException("Role does not belong to your account");
        }
    }

    private Role requireLockedRole(UUID roleId) {
        UUID accountId = currentAccountId();
        Role role = roleRepository.findByIdAndAccountIdForUpdate(roleId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));
        requireB2bRoleScope(role);
        return role;
    }

    private void requireCustomRole(Role role, String operation) {
        if (role.isSystemRole()) {
            throw new ForbiddenException("System roles cannot be " + operation);
        }
    }

    private void requireMutableCustomRole(Role role) {
        requireCustomRole(role, "modified");
        if (role.getStatus() == RoleStatus.ARCHIVED) {
            throw new InvalidStateException("Archived roles are read-only");
        }
    }

    private void validateActivation(Role role) {
        if (!role.getAccount().isActive()) {
            throw new InvalidStateException("Roles cannot be activated while the Account is suspended");
        }
        if (role.getCompany() != null && !role.getCompany().isActive()) {
            throw new InvalidStateException("Company-scoped roles cannot be activated while the Company is inactive");
        }
        if (role.getPermissions().isEmpty()) {
            throw new InvalidStateException("A role must contain at least one permission before activation");
        }
        for (RolePermission rolePermission : role.getPermissions()) {
            var permission = rolePermission.getPermission();
            permissionGrantValidator.requireClientRoleGrantable(permission);
            if (!planEntitlementService.isPermissionEntitled(role.getAccount().getId(), permission.getCode())) {
                throw new InvalidStateException(
                        "Role permission is unavailable in the current plan: " + permission.getCode());
            }
        }
    }

    private void validateImpactRequest(Role role, RoleChangeType changeType, String permissionCode) {
        if (changeType == null) throw new InvalidRequestException("Role change type is required");
        if (changeType == RoleChangeType.ADD_PERMISSION || changeType == RoleChangeType.REMOVE_PERMISSION) {
            if (permissionCode == null || permissionCode.isBlank()) {
                throw new InvalidRequestException("Permission code is required for permission impact preview");
            }
        }
        if (changeType == RoleChangeType.ADD_PERMISSION
                && rolePermissionRepository.existsByRoleIdAndPermissionCode(role.getId(), permissionCode)) {
            throw new DuplicateResourceException("RolePermission", "permissionCode", permissionCode);
        }
        if (changeType == RoleChangeType.ADD_PERMISSION) {
            var permission = permissionRepository.findByCode(permissionCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
            permissionGrantValidator.requireClientRoleGrantable(permission);
            if (!planEntitlementService.isPermissionEntitled(role.getAccount().getId(), permissionCode)) {
                throw new InvalidPermissionGrantException(
                        "Permission " + permissionCode + " is not available in the current plan entitlement.");
            }
        }
        if (changeType == RoleChangeType.REMOVE_PERMISSION
                && !rolePermissionRepository.existsByRoleIdAndPermissionCode(role.getId(), permissionCode)) {
            throw new ResourceNotFoundException("RolePermission", "permissionCode", permissionCode);
        }
    }

    private RoleImpactDto impact(Role role, RoleChangeType changeType, String permissionCode) {
        List<MemberRole> assignments = memberRoleRepository.findAllByRoleId(role.getId());
        List<String> currentPermissions = role.getPermissions().stream()
                .map(rp -> rp.getPermission().getCode())
                .sorted()
                .toList();
        List<String> granted = switch (changeType) {
            case ADD_PERMISSION -> List.of(permissionCode);
            case ACTIVATE -> role.isActive() ? List.of() : currentPermissions;
            default -> List.of();
        };
        List<String> lost = switch (changeType) {
            case REMOVE_PERMISSION -> List.of(permissionCode);
            case DEACTIVATE, ARCHIVE, DELETE -> role.isActive() ? currentPermissions : List.of();
            default -> List.of();
        };

        long accountAssignments = assignments.stream().filter(a -> a.getCompany() == null).count();
        Map<UUID, Long> companyAssignments = assignments.stream()
                .filter(a -> a.getCompany() != null)
                .collect(Collectors.groupingBy(a -> a.getCompany().getId(), Collectors.counting()));
        List<RoleImpactScopeDto> scopes = new ArrayList<>();
        if (accountAssignments > 0) {
            scopes.add(new RoleImpactScopeDto("ACCOUNT", null, accountAssignments));
        }
        companyAssignments.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RoleImpactScopeDto("COMPANY", entry.getKey(), entry.getValue()))
                .forEach(scopes::add);

        long affectedMembers = assignments.stream()
                .map(a -> a.getMember().getId()).distinct().count();
        long activeMembers = assignments.stream()
                .filter(a -> a.getMember().isActive())
                .map(a -> a.getMember().getId()).distinct().count();
        return new RoleImpactDto(
                role.getId(), role.getVersion(), role.getStatus(), changeType, permissionCode,
                assignments.size(), affectedMembers, activeMembers, List.copyOf(scopes),
                currentPermissions, granted, lost, !assignments.isEmpty());
    }

    private void requireConfirmedImpact(
            RoleImpactDto impact, Long expectedVersion, Long confirmedAssignmentCount) {
        if (!impact.confirmationRequired()) {
            if (expectedVersion != null && expectedVersion != impact.version()) {
                throw staleImpact(impact);
            }
            return;
        }
        if (expectedVersion == null || confirmedAssignmentCount == null) {
            throw new OperationBlockedException(
                    "Role change requires an explicit impact preview and confirmation",
                    impactDetails(impact));
        }
        if (expectedVersion != impact.version()
                || confirmedAssignmentCount != impact.assignmentCount()) {
            throw staleImpact(impact);
        }
    }

    private OperationBlockedException staleImpact(RoleImpactDto impact) {
        return new OperationBlockedException(
                "Role impact changed; preview the operation again before confirming",
                impactDetails(impact));
    }

    private List<String> impactDetails(RoleImpactDto impact) {
        return List.of(
                "roleVersion: " + impact.version(),
                "assignments: " + impact.assignmentCount(),
                "affectedMembers: " + impact.affectedMemberCount(),
                "permissionsGranted: " + String.join(",", impact.permissionsGranted()),
                "permissionsLost: " + String.join(",", impact.permissionsLost()));
    }

    private void requireB2bRoleScope(Role role) {
        if (isB2bContext()
                && (role.getCompany() == null
                || !Objects.equals(role.getCompany().getId(), HiveAppContextHolder.getContext().targetCompanyId()))) {
            throw new ForbiddenException("B2B role access is limited to the collaboration Company");
        }
    }

    private void requireB2bTargetCompany(UUID companyId) {
        if (isB2bContext()
                && !Objects.equals(companyId, HiveAppContextHolder.getContext().targetCompanyId())) {
            throw new ForbiddenException("B2B role access is limited to the collaboration Company");
        }
    }

    private boolean isB2bContext() {
        var context = HiveAppContextHolder.getContext();
        return context != null && context.isB2B();
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new InvalidRequestException(fieldName + " is required");
        return normalized.replaceAll("\\s+", " ");
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID currentAccountId() {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.currentAccountId() == null) {
            throw new ForbiddenException("An active workspace context is required");
        }
        return context.currentAccountId();
    }
}
