package com.hiveapp.platform.registry.service.impl;

import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.PlansFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.dto.FeatureCatalogAudience;
import com.hiveapp.platform.registry.dto.PermissionCatalogAudience;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistryServiceImplTest {

    @Mock private ModuleRepository moduleRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private PermissionRepository permissionRepository;

    @Test
    void planAssignableCatalogExposesOnlyPlanAssignableWorkspaceFeatures() {
        RegistryServiceImpl service = service();
        when(featureRepository.findAll()).thenReturn(List.of(
                feature(CompanyFeature.CODE, FeatureStatus.PUBLIC),
                feature(PlansFeature.CODE, FeatureStatus.INTERNAL)
        ));
        when(permissionRepository.findAll()).thenReturn(List.of(
                permission("platform.company.create"),
                permission("platform.plans.create")
        ));

        var catalog = service.getFeatureCatalog(FeatureCatalogAudience.PLAN_ASSIGNABLE);

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).features())
                .extracting(feature -> feature.code())
                .containsExactly(CompanyFeature.CODE);
        assertThat(catalog.get(0).features().get(0).permissions())
                .extracting(permission -> permission.code())
                .containsExactly("platform.company.create");
    }

    @Test
    void platformAdminPermissionCatalogExposesOnlyControlPlanePermissions() {
        RegistryServiceImpl service = service();
        when(featureRepository.findAll()).thenReturn(List.of(
                feature(CompanyFeature.CODE, FeatureStatus.PUBLIC),
                feature(PlansFeature.CODE, FeatureStatus.INTERNAL)
        ));
        when(permissionRepository.findAll()).thenReturn(List.of(
                permission("platform.company.create"),
                permission("platform.plans.create")
        ));

        var catalog = service.getPermissionCatalog(PermissionCatalogAudience.PLATFORM_ADMIN_ROLE_GRANTABLE);

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).features())
                .extracting(feature -> feature.code())
                .containsExactly(PlansFeature.CODE);
        assertThat(catalog.get(0).features().get(0).permissions())
                .extracting(permission -> permission.code())
                .containsExactly("platform.plans.create");
    }

    @Test
    void b2bPermissionCatalogExposesOnlyExplicitDelegatableActions() {
        RegistryServiceImpl service = service();
        when(featureRepository.findAll()).thenReturn(List.of(feature(CompanyFeature.CODE, FeatureStatus.PUBLIC)));
        when(permissionRepository.findAll()).thenReturn(List.of(
                permission("platform.company.create"),
                permission("platform.company.read_single"),
                permission("platform.company.delete")
        ));

        var catalog = service.getPermissionCatalog(PermissionCatalogAudience.B2B_DELEGATABLE);

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).features()).hasSize(1);
        assertThat(catalog.get(0).features().get(0).permissions())
                .extracting(permission -> permission.code())
                .containsExactly("platform.company.read_single");
    }

    private RegistryServiceImpl service() {
        FeatureDefinitionCollector collector = new FeatureDefinitionCollector(
                List.of(() -> List.of(CompanyFeature.definition(), PlansFeature.definition())));
        return new RegistryServiceImpl(moduleRepository, featureRepository, permissionRepository, provider(collector));
    }

    private static ObjectProvider<FeatureDefinitionCollector> provider(FeatureDefinitionCollector collector) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("featureDefinitionCollector", collector);
        return beanFactory.getBeanProvider(FeatureDefinitionCollector.class);
    }

    private static Feature feature(String code, FeatureStatus status) {
        Feature feature = new Feature();
        feature.setCode(code);
        feature.setStatus(status);
        feature.setActive(true);
        return feature;
    }

    private static Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setName(code);
        permission.setAction(code.substring(code.lastIndexOf('.') + 1));
        permission.setResource(code.substring(0, code.lastIndexOf('.')));
        return permission;
    }
}
