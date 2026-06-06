package com.hiveapp.shared.security.policy;

import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import dev.karroumi.permissionizer.PermissionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanPolicyTest {

    @Mock private PlanEntitlementService planEntitlementService;

    private PlanPolicy policy;
    private UUID accountId;
    private HiveAppPermissionContext context;
    private dev.karroumi.permissionizer.Permission requested;

    @BeforeEach
    void setUp() {
        policy = new PlanPolicy(planEntitlementService);
        accountId = UUID.randomUUID();
        context = new HiveAppPermissionContext(UUID.randomUUID(), accountId, accountId, null, null, false);
        requested = new dev.karroumi.permissionizer.Permission("platform.company.create");
    }

    @Test
    void entitledPermissionLetsUserPolicyContinue() {
        when(planEntitlementService.isPermissionEntitled(accountId, requested.path())).thenReturn(true);

        assertThat(policy.evaluate(requested, context)).isEqualTo(PermissionPolicy.Decision.ABSTAIN);
    }

    @Test
    void missingEntitlementDeniesPermissionBeforeRolePolicy() {
        when(planEntitlementService.isPermissionEntitled(accountId, requested.path())).thenReturn(false);

        assertThat(policy.evaluate(requested, context)).isEqualTo(PermissionPolicy.Decision.DENIED);
    }

    @Test
    void missingHiveAppContextAbstains() {
        assertThat(policy.evaluate(requested, null)).isEqualTo(PermissionPolicy.Decision.ABSTAIN);
        verifyNoInteractions(planEntitlementService);
    }
}
