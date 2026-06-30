package com.hiveapp.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiveapp.platform.admin.service.impl.AdminRoleServiceImpl;
import com.hiveapp.platform.admin.service.impl.AdminSubscriptionServiceImpl;
import com.hiveapp.platform.admin.service.impl.AdminUserServiceImpl;
import com.hiveapp.platform.admin.service.AdminSeeder;
import com.hiveapp.platform.client.account.service.impl.AccountShellServiceImpl;
import com.hiveapp.platform.client.collaboration.service.impl.CollaborationServiceImpl;
import com.hiveapp.platform.client.company.service.impl.CompanyServiceImpl;
import com.hiveapp.platform.client.invitation.service.impl.InvitationServiceImpl;
import com.hiveapp.platform.client.member.service.impl.MemberServiceImpl;
import com.hiveapp.platform.client.plan.service.impl.PlanAdminServiceImpl;
import com.hiveapp.platform.client.plan.service.impl.SubscriptionServiceImpl;
import com.hiveapp.platform.client.role.service.impl.RoleServiceImpl;
import com.hiveapp.platform.registry.service.impl.RegistryServiceImpl;
import dev.karroumi.permissionizer.PermissionNode;
import dev.karroumi.permissionizer.PermissionResolver;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PermissionGuardBoundaryTest {

    private static final List<Class<?>> PERMISSION_BEARING_SERVICES = List.of(
            AdminRoleServiceImpl.class,
            AdminSubscriptionServiceImpl.class,
            AdminUserServiceImpl.class,
            AccountShellServiceImpl.class,
            CollaborationServiceImpl.class,
            CompanyServiceImpl.class,
            InvitationServiceImpl.class,
            MemberServiceImpl.class,
            PlanAdminServiceImpl.class,
            SubscriptionServiceImpl.class,
            RoleServiceImpl.class,
            RegistryServiceImpl.class);

    @AfterEach
    void clearResolverCache() {
        PermissionResolver.clearCache();
    }

    @Test
    void platformInfrastructureDoesNotInheritAutomaticGuarding() throws Exception {
        Method bootstrap = AdminSeeder.class.getMethod("seedAdmin");

        assertThat(PermissionResolver.resolve(bootstrap).shouldCheck()).isFalse();
    }

    @Test
    void permissionBearingServiceExplicitlyEnablesItsGuard() throws Exception {
        Method listCompanies = CompanyServiceImpl.class.getMethod("getAccountCompanies", UUID.class);
        PermissionResolver.Result result = PermissionResolver.resolve(listCompanies);

        assertThat(result.shouldCheck()).isTrue();
        assertThat(result.path()).isEqualTo("platform.company.read_all");
    }

    @Test
    void everyCurrentPermissionBearingServiceExplicitlyEnablesGuarding() {
        assertThat(PERMISSION_BEARING_SERVICES)
                .hasSize(12)
                .allSatisfy(type -> {
                    assertThat(Arrays.stream(type.getDeclaredMethods()))
                            .as("%s must declare permission-bearing methods", type.getName())
                            .anyMatch(method -> method.isAnnotationPresent(PermissionNode.class));
                    PermissionNode annotation = type.getAnnotation(PermissionNode.class);
                    assertThat(annotation)
                            .as("%s must explicitly declare its guard boundary", type.getName())
                            .isNotNull();
                    assertThat(annotation.guard())
                            .as("%s must explicitly enable guarding", type.getName())
                            .isEqualTo(PermissionNode.Guard.ON);
                });
    }
}
