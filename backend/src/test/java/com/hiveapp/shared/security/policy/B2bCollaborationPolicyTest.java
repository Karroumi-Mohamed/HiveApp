package com.hiveapp.shared.security.policy;

import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class B2bCollaborationPolicyTest {

    private final CollaborationPermissionRepository collaborationPermissionRepository =
            mock(CollaborationPermissionRepository.class);
    private final PlanEntitlementService planEntitlementService = mock(PlanEntitlementService.class);
    private final PermissionGrantValidator permissionGrantValidator = mock(PermissionGrantValidator.class);
    private final UserRolePolicy userRolePolicy = mock(UserRolePolicy.class);
    private final B2bCollaborationPolicy policy = new B2bCollaborationPolicy(
            collaborationPermissionRepository,
            planEntitlementService,
            permissionGrantValidator,
            userRolePolicy);

    @Test
    void checksDelegatedPermissionAgainstExactCollaborationFromContext() {
        UUID collaborationId = UUID.randomUUID();
        String permissionCode = "platform.company.read_single";
        UUID providerAccountId = UUID.randomUUID();
        HiveAppPermissionContext context = new HiveAppPermissionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                providerAccountId,
                UUID.randomUUID(),
                collaborationId,
                true
        );

        when(collaborationPermissionRepository.existsActiveByCollaborationIdAndPermissionCode(
                collaborationId, permissionCode)).thenReturn(true);
        when(permissionGrantValidator.isB2bRuntimeEligible(permissionCode)).thenReturn(true);
        when(planEntitlementService.isPermissionEntitled(providerAccountId, permissionCode)).thenReturn(true);
        when(userRolePolicy.evaluate(eq(new Permission(permissionCode)), any(HiveAppPermissionContext.class)))
                .thenReturn(PermissionPolicy.Decision.GRANTED);

        assertThat(policy.evaluate(new Permission(permissionCode), context))
                .isEqualTo(PermissionPolicy.Decision.GRANTED);
        verify(collaborationPermissionRepository)
                .existsActiveByCollaborationIdAndPermissionCode(collaborationId, permissionCode);
        verify(planEntitlementService).isPermissionEntitled(providerAccountId, permissionCode);
        var operatorContext = org.mockito.ArgumentCaptor.forClass(HiveAppPermissionContext.class);
        verify(userRolePolicy).evaluate(eq(new Permission(permissionCode)), operatorContext.capture());
        assertThat(operatorContext.getValue().currentAccountId()).isEqualTo(context.clientAccountId());
        assertThat(operatorContext.getValue().clientAccountId()).isEqualTo(context.clientAccountId());
        assertThat(operatorContext.getValue().targetCompanyId()).isNull();
        assertThat(operatorContext.getValue().isB2B()).isFalse();
    }

    @Test
    void deniesDelegatedPermissionWhenProviderIsNotEntitledAtRuntime() {
        UUID collaborationId = UUID.randomUUID();
        UUID providerAccountId = UUID.randomUUID();
        String permissionCode = "platform.company.read_single";
        HiveAppPermissionContext context = new HiveAppPermissionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                providerAccountId,
                UUID.randomUUID(),
                collaborationId,
                true
        );

        when(collaborationPermissionRepository.existsActiveByCollaborationIdAndPermissionCode(
                collaborationId, permissionCode)).thenReturn(true);
        when(permissionGrantValidator.isB2bRuntimeEligible(permissionCode)).thenReturn(true);
        when(planEntitlementService.isPermissionEntitled(providerAccountId, permissionCode)).thenReturn(false);

        assertThat(policy.evaluate(new Permission(permissionCode), context))
                .isEqualTo(PermissionPolicy.Decision.DENIED);
    }

    @Test
    void deniesB2bContextWithoutCollaborationId() {
        HiveAppPermissionContext context = new HiveAppPermissionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                true
        );

        assertThat(policy.evaluate(new Permission("platform.company.read_single"), context))
                .isEqualTo(PermissionPolicy.Decision.DENIED);
    }

    @Test
    void deniesWhenExternalActorHasNoAccountScopedOperatorPermission() {
        UUID collaborationId = UUID.randomUUID();
        UUID providerAccountId = UUID.randomUUID();
        String permissionCode = "platform.company.read_single";
        HiveAppPermissionContext context = new HiveAppPermissionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                providerAccountId,
                UUID.randomUUID(),
                collaborationId,
                true);

        when(permissionGrantValidator.isB2bRuntimeEligible(permissionCode)).thenReturn(true);
        when(collaborationPermissionRepository.existsActiveByCollaborationIdAndPermissionCode(
                collaborationId, permissionCode)).thenReturn(true);
        when(planEntitlementService.isPermissionEntitled(providerAccountId, permissionCode)).thenReturn(true);
        when(userRolePolicy.evaluate(eq(new Permission(permissionCode)), any(HiveAppPermissionContext.class)))
                .thenReturn(PermissionPolicy.Decision.ABSTAIN);

        assertThat(policy.evaluate(new Permission(permissionCode), context))
                .isEqualTo(PermissionPolicy.Decision.DENIED);
    }

    @Test
    void deniesPersistedGrantWhenPermissionIsNoLongerB2bEligibleInCode() {
        String permissionCode = "platform.company.read_single";
        HiveAppPermissionContext context = new HiveAppPermissionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                true);
        when(permissionGrantValidator.isB2bRuntimeEligible(permissionCode)).thenReturn(false);

        assertThat(policy.evaluate(new Permission(permissionCode), context))
                .isEqualTo(PermissionPolicy.Decision.DENIED);
        verifyNoInteractions(collaborationPermissionRepository, planEntitlementService, userRolePolicy);
    }
}
