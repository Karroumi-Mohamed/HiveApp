package com.hiveapp.platform.client.role.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.client.role.domain.repository.RolePermissionRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionGrantValidator permissionGrantValidator;

    @InjectMocks
    private RoleServiceImpl roleService;

    @AfterEach
    void clearContext() {
        HiveAppContextHolder.clearContext();
    }

    @Test
    void addPermissionToRoleRejectsPermissionsThatAreNotClientRoleGrantable() {
        UUID accountId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        String permissionCode = "platform.plans.create";
        setContext(accountId);

        Role role = role(roleId, accountId);
        Permission permission = permission(permissionCode);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.existsByRoleIdAndPermissionCode(roleId, permissionCode)).thenReturn(false);
        when(permissionRepository.findByCode(permissionCode)).thenReturn(Optional.of(permission));
        org.mockito.Mockito.doThrow(new InvalidPermissionGrantException("Permission cannot be granted to a client role."))
                .when(permissionGrantValidator).requireClientRoleGrantable(permission);

        assertThatThrownBy(() -> roleService.addPermissionToRole(roleId, permissionCode))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("client role");

        verify(rolePermissionRepository, never()).save(org.mockito.ArgumentMatchers.any(RolePermission.class));
    }

    private static void setContext(UUID accountId) {
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                UUID.randomUUID(),
                accountId,
                accountId,
                null,
                null,
                false
        ));
    }

    private static Role role(UUID roleId, UUID accountId) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", accountId);
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", roleId);
        role.setAccount(account);
        role.setName("Manager");
        return role;
    }

    private static Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setName(code);
        return permission;
    }
}
