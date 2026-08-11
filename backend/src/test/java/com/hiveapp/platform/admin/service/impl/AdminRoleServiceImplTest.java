package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRoleServiceImplTest {

    @Mock private AdminRoleRepository adminRoleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private AdminRolePermissionRepository adminRolePermissionRepository;
    @Mock private PermissionGrantValidator permissionGrantValidator;
    @Mock private AdminMutationAuthorizer adminMutationAuthorizer;

    @InjectMocks
    private AdminRoleServiceImpl adminRoleService;

    @Test
    void grantPermissionRejectsPermissionsThatAreNotPlatformAdminRoleGrantable() {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        String permissionCode = "platform.company.read_single";

        when(adminRolePermissionRepository.existsByAdminRoleIdAndPermissionId(roleId, permissionId)).thenReturn(false);
        when(adminRoleRepository.findById(roleId)).thenReturn(Optional.of(adminRole(roleId)));
        Permission permission = permission(permissionId, permissionCode);
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));
        org.mockito.Mockito.doThrow(new InvalidPermissionGrantException("Permission cannot be granted to a platform admin role."))
                .when(permissionGrantValidator).requirePlatformAdminRoleGrantable(permissionCode);

        assertThatThrownBy(() -> adminRoleService.grantPermission(roleId, permissionId))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("platform admin role");

        verify(adminRolePermissionRepository, never())
                .save(org.mockito.ArgumentMatchers.any(AdminRolePermission.class));
    }

    @Test
    void paginatedRoleReadLoadsAllPermissionGrantsInOneBulkQuery() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        AdminRole first = adminRole(firstId);
        AdminRole second = adminRole(secondId);
        second.setName("Operations");
        Permission permission = permission(UUID.randomUUID(), "platform.admin-users.read");
        AdminRolePermission grant = new AdminRolePermission();
        grant.setAdminRole(first);
        grant.setPermission(permission);
        PageRequest page = PageRequest.of(0, 20);
        when(adminRoleRepository.findAll(page))
                .thenReturn(new PageImpl<>(List.of(first, second), page, 2));
        when(adminRolePermissionRepository.findAllWithPermissionByAdminRoleIdIn(
                org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(grant));

        var result = adminRoleService.getAdminRoles(page);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(dto -> dto.permissions().size())
                .containsExactly(1, 0);
        verify(adminRolePermissionRepository)
                .findAllWithPermissionByAdminRoleIdIn(org.mockito.ArgumentMatchers.anyCollection());
        verify(adminRolePermissionRepository, never()).findAllByAdminRoleId(firstId);
        verify(adminRolePermissionRepository, never()).findAllByAdminRoleId(secondId);
    }

    private static AdminRole adminRole(UUID id) {
        AdminRole role = new AdminRole();
        ReflectionTestUtils.setField(role, "id", id);
        role.setName("Support");
        return role;
    }

    private static Permission permission(UUID id, String code) {
        Permission permission = new Permission();
        ReflectionTestUtils.setField(permission, "id", id);
        permission.setCode(code);
        permission.setName(code);
        return permission;
    }
}
