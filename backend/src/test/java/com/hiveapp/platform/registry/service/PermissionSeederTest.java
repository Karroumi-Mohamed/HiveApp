package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinitionException;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import dev.karroumi.permissionizer.CollectedPermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionSeederTest {

    @Mock private PermissionRepository permissionRepository;
    @Mock private FeatureRepository featureRepository;

    @Test
    void treatsRootAndFeatureNodesAsStructural() {
        assertThat(PermissionSeeder.isStructuralNode("platform")).isTrue();
        assertThat(PermissionSeeder.isStructuralNode("platform.registry")).isTrue();
        assertThat(PermissionSeeder.isStructuralNode("platform.registry.read")).isFalse();
    }

    @Test
    void acceptsOnlyModuleFeatureActionPermissionShape() {
        assertThat(PermissionSeeder.isStrictActionPermission("platform.registry.read")).isTrue();
        assertThat(PermissionSeeder.isStrictActionPermission("platform")).isFalse();
        assertThat(PermissionSeeder.isStrictActionPermission("platform.registry")).isFalse();
        assertThat(PermissionSeeder.isStrictActionPermission("platform.admin.roles.read")).isFalse();
    }

    @Test
    void extractsFeatureCodeFromActionPermission() {
        assertThat(PermissionSeeder.extractFeatureCode("platform.registry.read"))
                .isEqualTo("platform.registry");
        assertThat(PermissionSeeder.extractModuleCode("platform.registry.read"))
                .isEqualTo("platform");
    }

    @Test
    void persistsStrictActionPermissionLinkedToDeclaredFeature() {
        PermissionSeeder seeder = new PermissionSeeder(permissionRepository, featureRepository);
        Feature feature = feature("platform.registry", "platform");
        when(featureRepository.findByCode("platform.registry")).thenReturn(Optional.of(feature));
        when(permissionRepository.findByCode("platform.registry.read")).thenReturn(Optional.empty());

        seeder.seedPermissions(List.of(permission("read", "Read registry", "platform.registry")));

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("platform.registry.read");
        assertThat(captor.getValue().getResource()).isEqualTo("platform.registry");
        assertThat(captor.getValue().getAction()).isEqualTo("read");
        assertThat(captor.getValue().getFeature()).isEqualTo(feature);
    }

    @Test
    void structuralNodesAreIgnoredWithoutRegistryLookups() {
        PermissionSeeder seeder = new PermissionSeeder(permissionRepository, featureRepository);

        seeder.seedPermissions(List.of(
                permission("platform", "Platform", null),
                permission("registry", "Registry", "platform")));

        verify(featureRepository, never()).findByCode(org.mockito.ArgumentMatchers.anyString());
        verify(permissionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDeeperActionPathInsteadOfSkippingIt() {
        PermissionSeeder seeder = new PermissionSeeder(permissionRepository, featureRepository);

        assertThatThrownBy(() -> seeder.seedPermissions(List.of(
                permission("grant", "Grant", "platform.roles.permission"))))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("required shape");

        verify(permissionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsActionPermissionWhoseFeatureIsMissing() {
        PermissionSeeder seeder = new PermissionSeeder(permissionRepository, featureRepository);
        when(featureRepository.findByCode("platform.unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seeder.seedPermissions(List.of(
                permission("read", "Read", "platform.unknown"))))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("no matching Feature");
    }

    @Test
    void rejectsFeatureWhoseModuleDoesNotMatchPermissionPath() {
        PermissionSeeder seeder = new PermissionSeeder(permissionRepository, featureRepository);
        when(featureRepository.findByCode("platform.registry"))
                .thenReturn(Optional.of(feature("platform.registry", "billing")));

        assertThatThrownBy(() -> seeder.seedPermissions(List.of(
                permission("read", "Read", "platform.registry"))))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("outside its declared module");
    }

    @Test
    void rejectsPersistedPermissionLinkedToDifferentFeature() {
        PermissionSeeder seeder = new PermissionSeeder(permissionRepository, featureRepository);
        Feature declaredFeature = feature("platform.registry", "platform");
        Permission existing = new Permission();
        existing.setCode("platform.registry.read");
        existing.setFeature(feature("platform.company", "platform"));
        when(featureRepository.findByCode("platform.registry")).thenReturn(Optional.of(declaredFeature));
        when(permissionRepository.findByCode("platform.registry.read")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> seeder.seedPermissions(List.of(
                permission("read", "Read", "platform.registry"))))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("is not linked to Feature");
    }

    private CollectedPermission permission(String key, String description, String parentPath) {
        String path = parentPath == null ? key : parentPath + "." + key;
        return new CollectedPermission(path, description, parentPath);
    }

    private Feature feature(String featureCode, String moduleCode) {
        Module module = new Module();
        module.setCode(moduleCode);
        Feature feature = new Feature();
        feature.setCode(featureCode);
        feature.setModule(module);
        return feature;
    }
}
