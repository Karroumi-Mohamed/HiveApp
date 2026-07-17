package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        PlanEntitlementService entitlementService = mock(PlanEntitlementService.class);
        CurrentRegistrySnapshot current = new CurrentRegistrySnapshot();
        current.install(new RegistrySnapshot(
                List.of(definition), List.of(), Set.of(currentPermission.getCode()), "test"));
        UUID accountId = UUID.randomUUID();
        when(permissionRepository.findAll()).thenReturn(List.of(currentPermission, stalePermission));
        when(entitlementService.isPermissionEntitled(accountId, currentPermission.getCode())).thenReturn(true);

        PermissionPickerCatalogService service = new PermissionPickerCatalogService(
                provider(collector), permissionRepository, entitlementService, current);

        var result = service.clientRoleCatalog(accountId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).features().get(0).permissions())
                .extracting(permission -> permission.code())
                .containsExactly(currentPermission.getCode());
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
