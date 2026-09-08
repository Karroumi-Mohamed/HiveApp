package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminPermissionResolverTest {
    private final AdminUserRepository admins = mock(AdminUserRepository.class);
    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final PermissionGrantValidator validator = mock(PermissionGrantValidator.class);
    private final AdminPermissionResolver resolver = new AdminPermissionResolver(admins, permissions, validator);

    @Test
    void superAdminProfileUsesCodeProjectionAndOneBulkEvaluation() {
        var admin = new AdminUser();
        admin.setSuperAdmin(true);
        var codes = List.of("platform.plans.list", "platform.company.create");
        when(permissions.findAllCodes()).thenReturn(codes);
        when(validator.platformAdminRoleGrantableCodes(codes)).thenReturn(Set.of("platform.plans.list"));
        assertThat(resolver.resolve(admin)).containsExactly("platform.plans.list");
        verify(permissions).findAllCodes();
        verify(validator).platformAdminRoleGrantableCodes(codes);
        verifyNoMoreInteractions(permissions, validator);
    }

    @Test
    void ordinaryAdminPermissionsAreNeverRetainedAfterRevocation() {
        var admin = new AdminUser();
        when(admins.findAllPermissionCodes(admin.getId()))
                .thenReturn(List.of("platform.plans.list"))
                .thenReturn(List.of());
        assertThat(resolver.resolve(admin)).containsExactly("platform.plans.list");
        assertThat(resolver.resolve(admin)).isEmpty();
        verify(admins, times(2)).findAllPermissionCodes(admin.getId());
        verifyNoInteractions(permissions, validator);
    }
}
