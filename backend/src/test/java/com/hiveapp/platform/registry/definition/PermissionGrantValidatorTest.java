package com.hiveapp.platform.registry.definition;

import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.service.CurrentRegistrySnapshot;
import com.hiveapp.platform.registry.service.RegistrySnapshot;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionGrantValidatorTest {

    private final FeatureDefinitionCollector collector =
            new FeatureDefinitionCollector(List.of(() -> List.of(
                    FeatureDefinition.clientWorkspace("platform.company")
                            .displayName("Companies")
                            .b2bDelegatableActions("read")
                            .build(),
                    FeatureDefinition.platformControl("platform.plans")
                            .displayName("Plans")
                            .build()
            )));
    private final CurrentRegistrySnapshot snapshot = snapshot();
    private final PermissionGrantValidator validator =
            new PermissionGrantValidator(provider(collector), snapshot);

    @Test
    void allowsClientWorkspacePermissionsForClientRoles() {
        assertThat(validator.isClientRoleGrantable(permission("platform.company.read"))).isTrue();
    }

    @Test
    void rejectsPlatformControlPermissionsForClientRoles() {
        assertThatThrownBy(() -> validator.requireClientRoleGrantable(permission("platform.plans.create")))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("client role");
    }

    @Test
    void onlyExplicitClientWorkspaceFeaturesCanBeB2bDelegated() {
        validator.requireB2bDelegatable(permission("platform.company.read"));

        assertThatThrownBy(() -> validator.requireB2bDelegatable(permission("platform.plans.create")))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("B2B collaboration");
    }

    @Test
    void rejectsClientWorkspaceActionsThatAreNotB2bDelegatable() {
        assertThatThrownBy(() -> validator.requireB2bDelegatable(permission("platform.company.delete")))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("B2B collaboration");
    }

    @Test
    void allowsPlatformControlPermissionsForPlatformAdminRoles() {
        validator.requirePlatformAdminRoleGrantable("platform.plans.create");
    }

    @Test
    void rejectsStaleDatabaseActionMissingFromCurrentPermissionizerSnapshot() {
        assertThatThrownBy(() -> validator.requireClientRoleGrantable(
                permission("platform.company.removed_action")))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("client role");
    }

    private static Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        return permission;
    }

    private static ObjectProvider<FeatureDefinitionCollector> provider(FeatureDefinitionCollector collector) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("featureDefinitionCollector", collector);
        return beanFactory.getBeanProvider(FeatureDefinitionCollector.class);
    }

    private CurrentRegistrySnapshot snapshot() {
        CurrentRegistrySnapshot current = new CurrentRegistrySnapshot();
        current.install(new RegistrySnapshot(
                collector.collect(),
                List.of(),
                java.util.Set.of(
                        "platform.company.read",
                        "platform.company.delete",
                        "platform.plans.create"),
                "test-snapshot"));
        return current;
    }
}
