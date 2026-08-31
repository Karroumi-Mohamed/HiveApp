package com.hiveapp.platform.registry.definition;

import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.service.CurrentRegistrySnapshot;
import com.hiveapp.platform.registry.service.RegistrySnapshot;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionGrantValidatorTest {

    private final FeatureDefinitionCollector collector =
            new FeatureDefinitionCollector(List.of(() -> List.of(
                    FeatureDefinition.clientWorkspace("platform.company")
                            .displayName("Companies")
                            .ownerOnlyActions("delete")
                            .b2bDelegatableActions("read")
                            .build(),
                    FeatureDefinition.platformControl("platform.plans")
                            .displayName("Plans")
                            .build()
            )));
    private final CurrentRegistrySnapshot snapshot = snapshot();
    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private PermissionGrantValidator validator;

    @BeforeEach
    void setUp() {
        when(featureRepository.findByCode("platform.company"))
                .thenReturn(java.util.Optional.of(feature("platform.company")));
        when(featureRepository.findByCode("platform.plans"))
                .thenReturn(java.util.Optional.of(feature("platform.plans")));
        validator = new PermissionGrantValidator(provider(collector), snapshot, featureRepository);
    }

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
    void rejectsOwnerOnlyActionForOrdinaryClientRole() {
        assertThatThrownBy(() -> validator.requireClientRoleGrantable(
                permission("platform.company.delete")))
                .isInstanceOf(InvalidPermissionGrantException.class);
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

    private static Feature feature(String code) {
        Feature feature = new Feature();
        feature.setCode(code);
        feature.setNewGrantsEnabled(true);
        feature.setRuntimeEnabled(true);
        return feature;
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
