package com.hiveapp.platform.registry.definition;

import com.hiveapp.shared.quota.QuotaSlot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeatureDefinitionTest {

    @Test
    void derivesModuleAndFeatureKeyFromStrictCode() {
        FeatureDefinition definition = FeatureDefinition.clientWorkspace("platform.staff")
                .displayName("Staff")
                .description("Workspace staff management")
                .sortOrder(20)
                .quota(QuotaSlot.count("members", "persons"))
                .build();

        assertThat(definition.moduleCode()).isEqualTo("platform");
        assertThat(definition.featureKey()).isEqualTo("staff");
        assertThat(definition.surface()).isEqualTo(FeatureSurface.CLIENT_WORKSPACE);
        assertThat(definition.planAssignable()).isTrue();
        assertThat(definition.clientRoleGrantable()).isTrue();
        assertThat(definition.platformAdminRoleGrantable()).isFalse();
        assertThat(definition.publicCatalogVisible()).isTrue();
        assertThat(definition.quotaSlots()).hasSize(1);
    }

    @Test
    void b2bActionsMakeOnlyThosePermissionsDelegatable() {
        FeatureDefinition definition = FeatureDefinition.clientWorkspace("platform.company")
                .displayName("Companies")
                .b2bDelegatableActions("read_single")
                .build();

        assertThat(definition.b2bDelegatable()).isTrue();
        assertThat(definition.isB2bDelegatablePermission("platform.company.read_single")).isTrue();
        assertThat(definition.isB2bDelegatablePermission("platform.company.delete")).isFalse();
    }

    @Test
    void rejectsCodesThatAreNotModuleAndFeatureOnly() {
        assertThatThrownBy(() -> FeatureDefinition.clientWorkspace("platform.staff.members")
                .displayName("Staff")
                .build())
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("<module>.<feature>");
    }

    @Test
    void rejectsB2bDelegationOutsideClientWorkspaceSurface() {
        assertThatThrownBy(() -> FeatureDefinition.platformControl("platform.registry")
                .displayName("Registry")
                .b2bDelegatableActions("read")
                .build())
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("cannot be B2B-delegatable");
    }

    @Test
    void rejectsDuplicateQuotaResources() {
        assertThatThrownBy(() -> FeatureDefinition.clientWorkspace("platform.workspace")
                .displayName("Workspace")
                .quotas(List.of(
                        QuotaSlot.count("members", "persons"),
                        QuotaSlot.count("members", "persons")
                ))
                .build())
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("Duplicate quota resource");
    }

    @Test
    void ownsOnlyDirectActionPermissions() {
        FeatureDefinition definition = FeatureDefinition.platformControl("platform.registry")
                .displayName("Registry")
                .build();

        assertThat(definition.ownsPermission("platform.registry.read")).isTrue();
        assertThat(definition.ownsPermission("platform.registry.read.full")).isFalse();
        assertThat(definition.ownsPermission("platform.plans.read")).isFalse();
    }
}
