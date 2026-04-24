package com.hiveapp.platform.client.role.service.impl;

import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.client.role.domain.repository.RolePermissionRepository;
import com.hiveapp.platform.client.role.service.RoleService;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.UnauthorizedException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = "rbac", description = "Role Management")
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final PermissionRepository permissionRepository;

    @Override
    public Role getRole(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
    }

    @Override
    @PermissionNode(key = "view", description = "View roles")
    public List<Role> getAccountRoles(UUID accountId) {
        return roleRepository.findAllByAccountId(accountId);
    }

    @Override
    @PermissionNode(key = "view_company", description = "View company-scoped roles")
    public List<Role> getCompanyRoles(UUID companyId) {
        return roleRepository.findAllByCompanyId(companyId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create custom role")
    public Role createRole(UUID accountId, UUID companyId, String name, String description) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var company = companyId != null
                ? companyRepository.findById(companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId))
                : null;

        if (company != null && !company.getAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Company does not belong to your account");
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
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var role = getRole(roleId);

        if (!role.getAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Role does not belong to your account");
        }
        if (role.isSystemRole()) {
            throw new UnauthorizedException("System roles cannot be modified");
        }

        role.setName(name);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete", description = "Delete custom role")
    public void deleteRole(UUID roleId) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var role = getRole(roleId);

        if (!role.getAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Role does not belong to your account");
        }
        if (role.isSystemRole()) {
            throw new UnauthorizedException("System roles cannot be deleted");
        }

        roleRepository.delete(role);
    }

    @Override
    @Transactional
    @PermissionNode(key = "grant", description = "Grant permission brick to role")
    public void addPermissionToRole(UUID roleId, String permissionCode) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var role = getRole(roleId);

        if (!role.getAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Role does not belong to your account");
        }
        if (rolePermissionRepository.existsByRoleIdAndPermissionCode(roleId, permissionCode)) {
            throw new DuplicateResourceException("RolePermission", "permissionCode", permissionCode);
        }

        var permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));

        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(permission);
        rolePermissionRepository.save(rp);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke", description = "Revoke permission brick from role")
    public void removePermissionFromRole(UUID roleId, String permissionCode) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var role = getRole(roleId);

        if (!role.getAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Role does not belong to your account");
        }

        rolePermissionRepository.deleteByRoleIdAndPermissionCode(roleId, permissionCode);
    }
}
