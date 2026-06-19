package com.hiveapp.shared.security.policy;

import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class B2bCollaborationPolicyTest {

    private final CollaborationPermissionRepository collaborationPermissionRepository =
            mock(CollaborationPermissionRepository.class);
    private final PlanEntitlementService planEntitlementService = mock(PlanEntitlementService.class);
    private final B2bCollaborationPolicy policy = new B2bCollaborationPolicy(
            collaborationPermissionRepository,
            planEntitlementService);

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

        when(collaborationPermissionRepository.existsByCollaborationIdAndPermissionCode(
                collaborationId, permissionCode)).thenReturn(true);
        when(planEntitlementService.isPermissionEntitled(providerAccountId, permissionCode)).thenReturn(true);

        assertThat(policy.evaluate(new Permission(permissionCode), context))
                .isEqualTo(PermissionPolicy.Decision.GRANTED);
        verify(collaborationPermissionRepository)
                .existsByCollaborationIdAndPermissionCode(collaborationId, permissionCode);
        verify(planEntitlementService).isPermissionEntitled(providerAccountId, permissionCode);
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

        when(collaborationPermissionRepository.existsByCollaborationIdAndPermissionCode(
                collaborationId, permissionCode)).thenReturn(true);
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
}
