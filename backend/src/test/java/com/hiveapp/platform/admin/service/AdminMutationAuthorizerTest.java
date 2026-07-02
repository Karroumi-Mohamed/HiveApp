package com.hiveapp.platform.admin.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminMutationAuthorizerTest {

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private AdminRolePermissionRepository adminRolePermissionRepository;

    @InjectMocks
    private AdminMutationAuthorizer authorizer;

    private UUID actorUserId;
    private UUID actorAdminId;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        actorAdminId = UUID.randomUUID();
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                actorUserId, null, null, null, null, false));
    }

    @AfterEach
    void tearDown() {
        HiveAppContextHolder.clearContext();
    }

    @Test
    void delegatedAdminCannotModifySuperAdmin() {
        when(adminUserRepository.findByUserId(actorUserId)).thenReturn(Optional.of(actor(false, true)));
        AdminUser target = new AdminUser();
        target.setSuperAdmin(true);

        assertThatThrownBy(() -> authorizer.requireCanModifyAdmin(target))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only a SuperAdmin can modify another SuperAdmin.");
    }

    @Test
    void superAdminCanModifyAnotherSuperAdmin() {
        when(adminUserRepository.findByUserId(actorUserId)).thenReturn(Optional.of(actor(true, true)));
        AdminUser target = new AdminUser();
        target.setSuperAdmin(true);

        assertThatCode(() -> authorizer.requireCanModifyAdmin(target)).doesNotThrowAnyException();
    }

    @Test
    void delegatedAdminCannotManageRoleContainingPermissionTheyDoNotHold() {
        UUID roleId = UUID.randomUUID();
        String permissionCode = "platform.registry.read";
        when(adminUserRepository.findByUserId(actorUserId)).thenReturn(Optional.of(actor(false, true)));
        when(adminRolePermissionRepository.findAllByAdminRoleId(roleId))
                .thenReturn(List.of(grant(permissionCode)));
        when(adminUserRepository.hasPermission(actorAdminId, permissionCode)).thenReturn(false);

        assertThatThrownBy(() -> authorizer.requireCanManageRole(roleId, "modify"))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessage("A platform administrator cannot modify a role containing permissions they do not hold.");
    }

    @Test
    void inactiveActorCannotUseMutationAuthorizer() {
        when(adminUserRepository.findByUserId(actorUserId)).thenReturn(Optional.of(actor(false, false)));

        assertThatThrownBy(authorizer::currentActorIsSuperAdmin)
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("The acting user is not an active platform administrator.");
    }

    private AdminUser actor(boolean superAdmin, boolean active) {
        AdminUser actor = new AdminUser();
        ReflectionTestUtils.setField(actor, "id", actorAdminId);
        actor.setSuperAdmin(superAdmin);
        actor.setActive(active);
        return actor;
    }

    private static AdminRolePermission grant(String permissionCode) {
        Permission permission = new Permission();
        permission.setCode(permissionCode);
        AdminRolePermission grant = new AdminRolePermission();
        grant.setPermission(permission);
        return grant;
    }
}
