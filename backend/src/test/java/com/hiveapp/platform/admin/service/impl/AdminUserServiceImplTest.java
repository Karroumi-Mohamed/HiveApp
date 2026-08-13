package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.entity.AdminUserRole;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private AdminRoleRepository adminRoleRepository;
    @Mock private AdminUserRoleRepository adminUserRoleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionGrantValidator permissionGrantValidator;
    @Mock private IdentityService identityService;
    @Mock private AdminMutationAuthorizer adminMutationAuthorizer;
    @InjectMocks private AdminUserServiceImpl adminUserService;

    @Test
    void accessOverviewReportsOperationalBreakdown() {
        when(adminUserRepository.count()).thenReturn(12L);
        when(adminUserRepository.countByIsActiveTrue()).thenReturn(9L);
        when(adminUserRepository.countByIsActiveFalse()).thenReturn(3L);
        when(adminUserRepository.countByIsSuperAdminTrue()).thenReturn(2L);
        when(adminRoleRepository.count()).thenReturn(5L);
        when(adminRoleRepository.countByIsActiveTrue()).thenReturn(4L);
        when(adminRoleRepository.countByIsActiveFalse()).thenReturn(1L);

        var result = adminUserService.getAccessOverview();

        assertThat(result.totalOperators()).isEqualTo(12);
        assertThat(result.activeOperators()).isEqualTo(9);
        assertThat(result.inactiveOperators()).isEqualTo(3);
        assertThat(result.superAdmins()).isEqualTo(2);
        assertThat(result.totalRoles()).isEqualTo(5);
        assertThat(result.activeRoles()).isEqualTo(4);
        assertThat(result.inactiveRoles()).isEqualTo(1);
    }

    @Test
    void paginatedUserReadLoadsAllRoleAssignmentsInOneBulkQuery() {
        AdminUser first = adminUser("first@example.com");
        AdminUser second = adminUser("second@example.com");
        AdminRole role = new AdminRole();
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        role.setName("Support");
        role.setActive(true);
        AdminUserRole assignment = new AdminUserRole();
        assignment.setAdminUser(first);
        assignment.setAdminRole(role);
        PageRequest page = PageRequest.of(0, 20);
        when(adminUserRepository.searchPageWithUser(null, null, page))
                .thenReturn(new PageImpl<>(List.of(first, second), page, 2));
        when(adminUserRoleRepository.findAllWithRoleByAdminUserIdIn(anyCollection()))
                .thenReturn(List.of(assignment));

        var result = adminUserService.getAdminUsers(page);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(dto -> dto.roles().size())
                .containsExactly(1, 0);
        verify(adminUserRoleRepository).findAllWithRoleByAdminUserIdIn(anyCollection());
        verify(adminUserRoleRepository, never()).findAllByAdminUserId(first.getId());
        verify(adminUserRoleRepository, never()).findAllByAdminUserId(second.getId());
    }

    private static AdminUser adminUser(String email) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail(email);
        user.setUsername(email.substring(0, email.indexOf('@')));
        user.setFirstName("Admin");
        user.setLastName("User");
        AdminUser admin = new AdminUser();
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        admin.setUser(user);
        admin.setActive(true);
        return admin;
    }
}
