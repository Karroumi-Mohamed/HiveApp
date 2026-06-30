package com.hiveapp.platform.client.role.service.impl;

import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.client.role.domain.repository.RolePermissionRepository;
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
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = WorkspaceRolesFeature.KEY, description = "Role Management", guard = PermissionNode.Guard.ON)
public class RoleServiceImpl extends ClientWorkspaceFeatureService implements RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
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
    @PermissionNode(key = "read", description = "View role details")
    public Role getRole(UUID id) {
        var role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
        requireRoleInCurrentAccount(role);
        return role;
    }

    @Override
    @PermissionNode(key = "view", description = "View roles")
    public List<Role> getAccountRoles(UUID accountId) {
        requireCurrentAccount(accountId);
        return roleRepository.findAllByAccountId(accountId);
    }

    @Override
    @PermissionNode(key = "view_company", description = "View company-scoped roles")
    public List<Role> getCompanyRoles(UUID companyId) {
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        requireCurrentAccount(company.getAccount().getId());
        return roleRepository.findAllByCompanyId(companyId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create custom role")
    public Role createRole(UUID accountId, UUID companyId, String name, String description) {
        requireCurrentAccount(accountId);
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var company = companyId != null
                ? companyRepository.findById(companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId))
                : null;

        if (company != null && !company.getAccount().getId().equals(accountId)) {
            throw new ForbiddenException("Company does not belong to your account");
        }

        Role role = new Role();
        role.setAccount(account);
        role.setCompany(company);
        role.setName(name);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update", description = "Update role metadata")
    public Role updateRole(UUID roleId, String name, String description) {
        var role = getRole(roleId);

        if (role.isSystemRole()) {
            throw new ForbiddenException("System roles cannot be modified");
        }

        role.setName(name);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete", description = "Delete custom role")
    public void deleteRole(UUID roleId) {
        var role = getRole(roleId);

        if (role.isSystemRole()) {
            throw new ForbiddenException("System roles cannot be deleted");
        }

        roleRepository.delete(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "grant", description = "Grant permission brick to role")
    public void addPermissionToRole(UUID roleId, String permissionCode) {
        var role = getRole(roleId);

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

        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(permission);
        rolePermissionRepository.save(rp);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke", description = "Revoke permission brick from role")
    public void removePermissionFromRole(UUID roleId, String permissionCode) {
        var role = getRole(roleId);

        rolePermissionRepository.deleteByRoleIdAndPermissionCode(roleId, permissionCode);
    }

    @Override
    @PermissionNode(key = "permission_catalog", description = "View grantable role permissions")
    public List<PermissionPickerModuleDto> getPermissionCatalog(UUID accountId) {
        requireCurrentAccount(accountId);
        return permissionPickerCatalogService.clientRoleCatalog(accountId);
    }

    private void requireRoleInCurrentAccount(Role role) {
        requireCurrentAccount(role.getAccount().getId());
    }

    private void requireCurrentAccount(UUID accountId) {
        UUID currentAccountId = HiveAppContextHolder.getContext().currentAccountId();
        if (!accountId.equals(currentAccountId)) {
            throw new ForbiddenException("Role does not belong to your account");
        }
    }
}
