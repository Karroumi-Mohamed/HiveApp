package com.hiveapp.platform.client.role.service.impl;

import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.client.role.domain.repository.RolePermissionRepository;
import com.hiveapp.platform.client.role.service.RoleService;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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
    public List<Role> getAccountRoles(UUID accountId) {
        return roleRepository.findAllByAccountId(accountId);
    }

    @Override
    public List<Role> getCompanyRoles(UUID companyId) {
        return roleRepository.findAllByCompanyId(companyId);
    }

    @Override
    @Transactional
    public Role createRole(UUID accountId, UUID companyId, String name, String description) {
        var account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var company = companyId != null ? companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId)) : null;

        Role role = new Role();
        role.setAccount(account);
        role.setCompany(company);
        role.setName(name);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public void addPermissionToRole(UUID roleId, String permissionCode) {
        var role = getRole(roleId);
        var permission = permissionRepository.findByCode(permissionCode)
            .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));

        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(permission);
        rolePermissionRepository.save(rp);
    }
}
