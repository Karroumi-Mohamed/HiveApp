package com.hiveapp.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hiveapp.identity.infrastructure.security.UserDetailsServiceImpl;
import com.hiveapp.shared.security.context.ContextDetectionFilter;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.shared.security.policy.AdminPermissionPolicy;
import com.hiveapp.shared.security.policy.B2bCollaborationPolicy;
import com.hiveapp.shared.security.policy.PlanPolicy;
import com.hiveapp.shared.security.policy.UserRolePolicy;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionPolicy;
import dev.karroumi.permissionizer.spring.PermissionInterceptor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PermissionPolicyOrderTest {

    private final AdminPermissionPolicy adminPolicy = mock(AdminPermissionPolicy.class);
    private final B2bCollaborationPolicy b2bPolicy = mock(B2bCollaborationPolicy.class);
    private final PlanPolicy planPolicy = mock(PlanPolicy.class);
    private final UserRolePolicy userRolePolicy = mock(UserRolePolicy.class);

    private Permission requested;
    private HiveAppPermissionContext context;

    @BeforeEach
    void configureHiveAppPolicyChain() {
        requested = new Permission("platform.company.read_single");
        context = new HiveAppPermissionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null, false);

        PermissionGuard.resetConfiguration();
        PermissionGuard.registerSpringInterceptor();
        securityConfig().permissionsLoader();
    }

    @AfterEach
    void clearGlobalSecurityState() {
        PermissionGuard.resetConfiguration();
        SecurityContextHolder.clearContext();
    }

    @Test
    void configuredPoliciesExecuteInTheDeclaredOrder() {
        when(adminPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(b2bPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(planPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(userRolePolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);

        assertThat(PermissionGuard.has(requested, context)).isFalse();

        InOrder order = inOrder(adminPolicy, b2bPolicy, planPolicy, userRolePolicy);
        order.verify(adminPolicy).evaluate(requested, context);
        order.verify(b2bPolicy).evaluate(requested, context);
        order.verify(planPolicy).evaluate(requested, context);
        order.verify(userRolePolicy).evaluate(requested, context);
    }

    @Test
    void adminGrantShortCircuitsEveryClientPolicy() {
        when(adminPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.GRANTED);

        assertThat(PermissionGuard.has(requested, context)).isTrue();
        verifyNoInteractions(b2bPolicy, planPolicy, userRolePolicy);
    }

    @Test
    void b2bDenialRunsAfterAdminAndStopsPlanAndRolePolicies() {
        when(adminPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(b2bPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.DENIED);

        assertThat(PermissionGuard.has(requested, context)).isFalse();
        verifyNoInteractions(planPolicy, userRolePolicy);
    }

    @Test
    void planDenialRunsAfterAdminAndB2bAndStopsRolePolicy() {
        when(adminPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(b2bPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(planPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.DENIED);

        assertThat(PermissionGuard.has(requested, context)).isFalse();
        verifyNoInteractions(userRolePolicy);
    }

    @Test
    void userRoleGrantRunsOnlyAfterEveryEarlierPolicyAbstains() {
        when(adminPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(b2bPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(planPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(userRolePolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.GRANTED);

        assertThat(PermissionGuard.has(requested, context)).isTrue();
    }

    @Test
    void springAuthorityFallbackRunsAfterAllDomainPoliciesAbstain() {
        when(adminPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(b2bPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(planPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(userRolePolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        authenticateWithRequestedAuthority();

        assertThat(PermissionGuard.has(requested, context)).isTrue();
    }

    @Test
    void springAuthorityFallbackCannotOverrideUserRoleDenial() {
        when(adminPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(b2bPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(planPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(userRolePolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.DENIED);
        authenticateWithRequestedAuthority();

        assertThat(PermissionGuard.has(requested, context)).isFalse();
    }

    private SecurityConfig securityConfig() {
        return new SecurityConfig(
                mock(JwtTokenProvider.class),
                mock(UserDetailsServiceImpl.class),
                mock(com.hiveapp.platform.admin.infrastructure.security.AdminUserDetailsServiceImpl.class),
                mock(AuthEntryPoint.class),
                mock(AccessDeniedHandler.class),
                adminPolicy,
                b2bPolicy,
                planPolicy,
                userRolePolicy,
                mock(ContextDetectionFilter.class),
                () -> context,
                mock(PermissionInterceptor.class));
    }

    private void authenticateWithRequestedAuthority() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "test-user",
                "not-used",
                List.of(new SimpleGrantedAuthority(requested.path())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
