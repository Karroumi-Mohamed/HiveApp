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
import com.hiveapp.shared.security.policy.FeatureRuntimePolicy;
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
    private final FeatureRuntimePolicy featureRuntimePolicy = mock(FeatureRuntimePolicy.class);
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
        when(featureRuntimePolicy.evaluate(requested, context))
                .thenReturn(PermissionPolicy.Decision.ABSTAIN);
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

        InOrder order = inOrder(featureRuntimePolicy, adminPolicy, b2bPolicy, planPolicy, userRolePolicy);
        order.verify(featureRuntimePolicy).evaluate(requested, context);
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
    void emergencyRuntimeDenialStopsEvenAdminPolicy() {
        when(featureRuntimePolicy.evaluate(requested, context))
                .thenReturn(PermissionPolicy.Decision.DENIED);

        assertThat(PermissionGuard.has(requested, context)).isFalse();
        verifyNoInteractions(adminPolicy, b2bPolicy, planPolicy, userRolePolicy);
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
        return securityConfig(featureRuntimePolicy, planPolicy);
    }

    private SecurityConfig securityConfig(FeatureRuntimePolicy runtime, PlanPolicy plan) {
        return new SecurityConfig(
                mock(JwtTokenProvider.class),
                mock(TokenSessionService.class),
                mock(UserDetailsServiceImpl.class),
                mock(com.hiveapp.platform.admin.infrastructure.security.AdminUserDetailsServiceImpl.class),
                mock(AuthEntryPoint.class),
                mock(AccessDeniedHandler.class),
                adminPolicy,
                runtime,
                b2bPolicy,
                plan,
                userRolePolicy,
                mock(ContextDetectionFilter.class),
                () -> context,
                mock(PermissionInterceptor.class));
    }

    @Test
    void realMandatoryRestrictionsStillVetoAnOtherwiseGrantedClientPermission() {
        var features = mock(com.hiveapp.platform.registry.domain.repository.FeatureRepository.class);
        var registry = new com.hiveapp.platform.registry.service.CurrentRegistrySnapshot();
        registry.install(new com.hiveapp.platform.registry.service.RegistrySnapshot(
                List.of(), List.of(), java.util.Set.of(requested.path()), "real-chain"));
        var feature = new com.hiveapp.platform.registry.domain.entity.Feature();
        feature.setCode("platform.company");
        feature.setRuntimeEnabled(false);
        when(features.findByCode(feature.getCode())).thenReturn(java.util.Optional.of(feature));
        var entitlements = mock(com.hiveapp.platform.client.plan.service.PlanEntitlementService.class);
        securityConfig(new FeatureRuntimePolicy(features, registry), new PlanPolicy(entitlements))
                .permissionsLoader();
        when(adminPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(b2bPolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.ABSTAIN);
        when(userRolePolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.GRANTED);
        authenticateWithRequestedAuthority();

        assertThat(PermissionGuard.has(requested, context)).isFalse();
        verifyNoInteractions(entitlements, adminPolicy, b2bPolicy, userRolePolicy);
        feature.setRuntimeEnabled(true);
        assertThat(PermissionGuard.has(requested, context)).isFalse();
        verifyNoInteractions(userRolePolicy);

        when(entitlements.isPermissionEntitled(context.currentAccountId(), requested.path())).thenReturn(true);
        assertThat(PermissionGuard.has(requested, context)).isTrue();
        when(userRolePolicy.evaluate(requested, context)).thenReturn(PermissionPolicy.Decision.DENIED);
        assertThat(PermissionGuard.has(requested, context)).isFalse();
        assertThat(PermissionGuard.has(new Permission("platform.company.removed"), context)).isFalse();
    }

    private void authenticateWithRequestedAuthority() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "test-user",
                "not-used",
                List.of(new SimpleGrantedAuthority(requested.path())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
