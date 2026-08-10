package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class PermissionPickerCatalogServiceTest {

    @Test
    void pickerExcludesStaleDatabaseActionsBeforeEntitlementEvaluation() {
        FeatureDefinition definition = FeatureDefinition.clientWorkspace("platform.company")
                .displayName("Companies")
                .build();
        FeatureDefinitionCollector collector =
                new FeatureDefinitionCollector(List.of(() -> List.of(definition)));
        Permission currentPermission = permission("platform.company.read");
        Permission stalePermission = permission("platform.company.removed_action");
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        PlanEntitlementService entitlementService = mock(PlanEntitlementService.class);
        RegistryCatalogVersionService versionService = mock(RegistryCatalogVersionService.class);
        CurrentRegistrySnapshot current = new CurrentRegistrySnapshot();
        current.install(new RegistrySnapshot(
                List.of(definition), List.of(), Set.of(currentPermission.getCode()), "test"));
        UUID accountId = UUID.randomUUID();
        when(permissionRepository.findAll()).thenReturn(List.of(currentPermission, stalePermission));
        Feature feature = new Feature();
        feature.setCode(definition.code());
        feature.setNewGrantsEnabled(true);
        feature.setRuntimeEnabled(true);
        when(featureRepository.findAll()).thenReturn(List.of(feature));
        when(entitlementService.entitledFeatureCodes(accountId)).thenReturn(Set.of(definition.code()));
        when(versionService.currentVersion()).thenReturn("snapshot:1");

        PermissionPickerCatalogService service = new PermissionPickerCatalogService(
                provider(collector), permissionRepository, featureRepository,
                entitlementService, current, versionService);

        var result = service.clientRoleCatalog(
                accountId, Set.of(currentPermission.getCode(), stalePermission.getCode()));

        assertThat(result.registryVersion()).isEqualTo("snapshot:1");
        assertThat(result.availableChoices()).hasSize(1);
        assertThat(result.availableChoices().get(0).features().get(0).permissions())
                .extracting(permission -> permission.code())
                .containsExactly(currentPermission.getCode());
        assertThat(result.currentSelections()).anySatisfy(selection -> {
            assertThat(selection.permissionCode()).isEqualTo(stalePermission.getCode());
            assertThat(selection.available()).isFalse();
            assertThat(selection.unavailableReason().name()).isEqualTo("NOT_IN_CURRENT_REGISTRY");
        });
        verify(entitlementService).entitledFeatureCodes(accountId);
        verify(entitlementService, never()).isPermissionEntitled(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    private static Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setName(code);
        permission.setDescription(code);
        permission.setAction(code.substring(code.lastIndexOf('.') + 1));
        permission.setResource(code.substring(0, code.lastIndexOf('.')));
        return permission;
    }

    private static org.springframework.beans.factory.ObjectProvider<FeatureDefinitionCollector> provider(
            FeatureDefinitionCollector collector) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("featureDefinitionCollector", collector);
        return beanFactory.getBeanProvider(FeatureDefinitionCollector.class);
    }
}
