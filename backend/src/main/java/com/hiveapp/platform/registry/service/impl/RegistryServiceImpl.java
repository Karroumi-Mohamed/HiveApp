package com.hiveapp.platform.registry.service.impl;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.RegistryFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.dto.FeatureCatalogAudience;
import com.hiveapp.platform.registry.dto.PermissionCatalogAudience;
import com.hiveapp.platform.registry.dto.RegistryFeatureReadModelDto;
import com.hiveapp.platform.registry.dto.RegistryModuleReadModelDto;
import com.hiveapp.platform.registry.dto.RegistryPermissionDto;
import com.hiveapp.platform.registry.service.RegistryService;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = RegistryFeature.KEY, description = "Platform Registry Management")
public class RegistryServiceImpl extends PlatformControlFeatureService implements RegistryService {

    private final ModuleRepository moduleRepository;
    private final FeatureRepository featureRepository;
    private final PermissionRepository permissionRepository;
    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;

    @Override
    protected FeatureDefinition featureDefinition() {
        return RegistryFeature.definition();
    }

    @Override
    @PermissionNode(key = "read", description = "View full registry inventory including INTERNAL features")
    public List<Module> getFullInventory() {
        return moduleRepository.findAll();
    }

    @Override
    @PermissionNode(key = "catalog", description = "View public catalog of modules and features")
    public List<Module> getPublicCatalog() {
        return moduleRepository.findAll().stream()
                .filter(Module::isActive)
                .filter(m -> m.getFeatures().stream()
                        .anyMatch(f -> (f.getStatus() == FeatureStatus.PUBLIC || f.getStatus() == FeatureStatus.BETA) && f.isActive()))
                .peek(m -> {
                    // Filter features within the module to only PUBLIC and BETA
                    m.setFeatures(m.getFeatures().stream()
                            .filter(f -> (f.getStatus() == FeatureStatus.PUBLIC || f.getStatus() == FeatureStatus.BETA) && f.isActive())
                            .toList());
                })
                .toList();
    }

    @Override
    @PermissionNode(key = "feature_catalog", description = "View feature catalog read models")
    @Transactional(readOnly = true)
    public List<RegistryModuleReadModelDto> getFeatureCatalog(FeatureCatalogAudience audience) {
        FeatureCatalogAudience resolvedAudience = audience != null ? audience : FeatureCatalogAudience.ALL;
        Predicate<FeatureDefinition> featureFilter = featureFilter(resolvedAudience);
        Map<String, Feature> featuresByCode = featureRepository.findAll().stream()
                .collect(Collectors.toMap(Feature::getCode, java.util.function.Function.identity()));
        Map<String, List<Permission>> permissionsByFeatureCode = permissionsByFeatureCode(permissionRepository.findAll());

        return buildCatalog(
                featureFilter,
                definition -> true,
                featuresByCode,
                permissionsByFeatureCode
        );
    }

    @Override
    @PermissionNode(key = "permission_catalog", description = "View permission picker read models")
    @Transactional(readOnly = true)
    public List<RegistryModuleReadModelDto> getPermissionCatalog(PermissionCatalogAudience audience) {
        PermissionCatalogAudience resolvedAudience = audience != null ? audience : PermissionCatalogAudience.ALL;
        Map<String, FeatureDefinition> definitionsByCode = featureDefinitionCollectorProvider.getObject().collectByCode();
        Predicate<FeatureDefinition> featureFilter = permissionFeatureFilter(resolvedAudience);
        Predicate<Permission> permissionFilter = permissionFilter(resolvedAudience, definitionsByCode);
        Map<String, Feature> featuresByCode = featureRepository.findAll().stream()
                .collect(Collectors.toMap(Feature::getCode, java.util.function.Function.identity()));
        Map<String, List<Permission>> permissionsByFeatureCode = permissionsByFeatureCode(permissionRepository.findAll());

        return buildCatalog(
                featureFilter,
                permissionFilter,
                featuresByCode,
                permissionsByFeatureCode
        ).stream()
                .map(module -> new RegistryModuleReadModelDto(
                        module.code(),
                        module.features().stream()
                                .filter(feature -> !feature.permissions().isEmpty())
                                .toList()))
                .filter(module -> !module.features().isEmpty())
                .toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_active", description = "Activate or deactivate code-declared operations-toggleable features")
    public void updateFeatureActive(UUID featureId, boolean active) {
        var feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));
        var definition = featureDefinitionCollectorProvider.getObject().collectByCode().get(feature.getCode());
        if (definition == null) {
            throw new BusinessException("Feature " + feature.getCode() + " is not backed by a code definition.");
        }
        if (!definition.operationsActivationToggleable()) {
            throw new BusinessException("Feature " + feature.getCode() + " is code-owned and cannot be activated or deactivated through registry controls.");
        }
        feature.setActive(active);
        featureRepository.save(feature);
    }

    private List<RegistryModuleReadModelDto> buildCatalog(
            Predicate<FeatureDefinition> featureFilter,
            Predicate<Permission> permissionFilter,
            Map<String, Feature> featuresByCode,
            Map<String, List<Permission>> permissionsByFeatureCode
    ) {
        return featureDefinitionCollectorProvider.getObject().collect().stream()
                .filter(featureFilter)
                .collect(Collectors.groupingBy(
                        FeatureDefinition::moduleCode,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new RegistryModuleReadModelDto(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(definition -> toFeatureReadModel(
                                        definition,
                                        featuresByCode.get(definition.code()),
                                        permissionsByFeatureCode.getOrDefault(definition.code(), List.of()).stream()
                                                .filter(permission -> definition.ownsPermission(permission.getCode()))
                                                .filter(permissionFilter)
                                                .toList()))
                                .toList()))
                .filter(module -> !module.features().isEmpty())
                .toList();
    }

    private RegistryFeatureReadModelDto toFeatureReadModel(
            FeatureDefinition definition,
            Feature feature,
            List<Permission> permissions
    ) {
        boolean registryPresent = feature != null;
        return new RegistryFeatureReadModelDto(
                registryPresent ? feature.getId() : null,
                definition.code(),
                definition.moduleCode(),
                definition.featureKey(),
                definition.displayName(),
                definition.description(),
                definition.surface(),
                registryPresent ? feature.getStatus() : null,
                registryPresent && feature.isActive(),
                registryPresent,
                definition.planAssignable(),
                definition.clientRoleGrantable(),
                definition.platformAdminRoleGrantable(),
                definition.b2bDelegatable(),
                definition.publicCatalogVisible(),
                definition.operationsActivationToggleable(),
                definition.sortOrder(),
                definition.quotaSlots(),
                permissions.stream()
                        .map(this::toPermissionDto)
                        .toList()
        );
    }

    private RegistryPermissionDto toPermissionDto(Permission permission) {
        return new RegistryPermissionDto(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getAction(),
                permission.getResource()
        );
    }

    private Map<String, List<Permission>> permissionsByFeatureCode(List<Permission> permissions) {
        return permissions.stream()
                .filter(permission -> featureCode(permission.getCode()) != null)
                .collect(Collectors.groupingBy(permission -> Objects.requireNonNull(featureCode(permission.getCode()))));
    }

    private Predicate<FeatureDefinition> featureFilter(FeatureCatalogAudience audience) {
        return switch (audience) {
            case ALL -> definition -> true;
            case PLAN_ASSIGNABLE -> FeatureDefinition::planAssignable;
            case PUBLIC_CATALOG -> definition -> definition.publicCatalogVisible();
        };
    }

    private Predicate<FeatureDefinition> permissionFeatureFilter(PermissionCatalogAudience audience) {
        return switch (audience) {
            case ALL -> definition -> true;
            case CLIENT_ROLE_GRANTABLE -> FeatureDefinition::clientRoleGrantable;
            case PLATFORM_ADMIN_ROLE_GRANTABLE -> FeatureDefinition::platformAdminRoleGrantable;
            case B2B_DELEGATABLE -> FeatureDefinition::b2bDelegatable;
        };
    }

    private Predicate<Permission> permissionFilter(
            PermissionCatalogAudience audience,
            Map<String, FeatureDefinition> definitionsByCode
    ) {
        return switch (audience) {
            case ALL, CLIENT_ROLE_GRANTABLE, PLATFORM_ADMIN_ROLE_GRANTABLE -> permission -> true;
            case B2B_DELEGATABLE -> permission -> {
                FeatureDefinition definition = definitionsByCode.get(featureCode(permission.getCode()));
                return definition != null && definition.isB2bDelegatablePermission(permission.getCode());
            };
        };
    }

    private static String featureCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return null;
        }
        int lastDot = permissionCode.lastIndexOf('.');
        if (lastDot < 1) {
            return null;
        }
        return permissionCode.substring(0, lastDot);
    }
}
