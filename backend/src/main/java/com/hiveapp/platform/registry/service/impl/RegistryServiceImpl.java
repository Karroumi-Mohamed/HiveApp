package com.hiveapp.platform.registry.service.impl;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.constant.FeatureOperationalControl;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.entity.FeatureOperationalChange;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.RegistryFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncRunRepository;
import com.hiveapp.platform.registry.domain.repository.FeatureOperationalChangeRepository;
import com.hiveapp.platform.registry.dto.FeatureCatalogAudience;
import com.hiveapp.platform.registry.dto.PermissionCatalogAudience;
import com.hiveapp.platform.registry.dto.RegistryFeatureReadModelDto;
import com.hiveapp.platform.registry.dto.RegistryModuleReadModelDto;
import com.hiveapp.platform.registry.dto.RegistryPermissionDto;
import com.hiveapp.platform.registry.dto.RegistrySyncRunDto;
import com.hiveapp.platform.registry.dto.FeatureOperationalChangeDto;
import com.hiveapp.platform.registry.service.RegistryService;
import com.hiveapp.platform.registry.service.CurrentRegistrySnapshot;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
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
@PermissionNode(key = RegistryFeature.KEY, description = "Platform Registry Management", guard = PermissionNode.Guard.ON)
public class RegistryServiceImpl extends PlatformControlFeatureService implements RegistryService {

    private final FeatureRepository featureRepository;
    private final PermissionRepository permissionRepository;
    private final RegistrySyncRunRepository registrySyncRunRepository;
    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    private final CurrentRegistrySnapshot currentRegistrySnapshot;
    private final FeatureOperationalChangeRepository operationalChangeRepository;
    private final RegistryCatalogVersionService catalogVersionService;

    @Override
    protected FeatureDefinition featureDefinition() {
        return RegistryFeature.definition();
    }

    @Override
    @PermissionNode(key = "read", description = "View full registry inventory read models")
    @Transactional(readOnly = true)
    public List<RegistryModuleReadModelDto> getFullInventory() {
        return getFeatureCatalog(FeatureCatalogAudience.ALL);
    }

    @Override
    @PermissionNode(key = "feature_catalog", description = "View feature catalog read models")
    @Transactional(readOnly = true)
    public List<RegistryModuleReadModelDto> getFeatureCatalog(FeatureCatalogAudience audience) {
        FeatureCatalogAudience resolvedAudience = audience != null ? audience : FeatureCatalogAudience.ALL;
        Predicate<FeatureDefinition> featureFilter = featureFilter(resolvedAudience);
        Map<String, Feature> featuresByCode = featureRepository.findAll().stream()
                .collect(Collectors.toMap(Feature::getCode, java.util.function.Function.identity()));
        featureFilter = featureFilter.and(definition -> featureAvailableForAudience(
                featuresByCode.get(definition.code()), resolvedAudience));
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
        featureFilter = featureFilter.and(definition -> permissionFeatureAvailable(
                featuresByCode.get(definition.code()), resolvedAudience));
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
    @PermissionNode(key = "sync_status", description = "View the latest registry synchronization summary")
    @Transactional(readOnly = true)
    public RegistrySyncRunDto getLatestSynchronizationRun() {
        var run = registrySyncRunRepository.findFirstByOrderByStartedAtDesc()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RegistrySyncRun", "latest", "synchronization"));
        return new RegistrySyncRunDto(
                run.getId(),
                run.getBuildVersion(),
                run.getSnapshotHash(),
                run.getStatus(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getDiscoveredModules(),
                run.getDiscoveredFeatures(),
                run.getDiscoveredPermissions(),
                run.getCreatedModules(),
                run.getCreatedFeatures(),
                run.getUpdatedFeatures(),
                run.getCreatedPermissions(),
                run.getUpdatedPermissions(),
                run.getOrphanedPermissions(),
                run.getDetails());
    }

    @Override
    @PermissionNode(key = "control_history", description = "View feature operational control history")
    @Transactional(readOnly = true)
    public List<FeatureOperationalChangeDto> getFeatureControlHistory(UUID featureId) {
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));
        return operationalChangeRepository.findAllByFeatureIdOrderByCreatedAtDesc(featureId).stream()
                .map(change -> new FeatureOperationalChangeDto(
                        change.getId(),
                        feature.getCode(),
                        change.getControl(),
                        change.getActorUserId(),
                        change.isPreviousValue(),
                        change.isNewValue(),
                        change.getReason(),
                        change.isImpactConfirmed(),
                        change.isCommunicationConfirmed(),
                        change.getEffectiveTiming(),
                        change.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_public_visibility", description = "Change public feature visibility")
    public void updatePublicVisibility(UUID featureId, boolean enabled, String reason) {
        updateControl(featureId, FeatureOperationalControl.PUBLIC_VISIBILITY, enabled, reason, false, false);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_new_sales", description = "Change availability for new sales")
    public void updateNewSales(UUID featureId, boolean enabled, String reason) {
        updateControl(featureId, FeatureOperationalControl.NEW_SALES, enabled, reason, false, false);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_new_grants", description = "Change availability for new grants")
    public void updateNewGrants(UUID featureId, boolean enabled, String reason) {
        updateControl(featureId, FeatureOperationalControl.NEW_GRANTS, enabled, reason, false, false);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_emergency_runtime", description = "Emergency-stop or restore feature runtime")
    public void updateEmergencyRuntime(UUID featureId, boolean enabled, String reason,
                                       boolean impactConfirmed, boolean communicationConfirmed) {
        if (!impactConfirmed || !communicationConfirmed) {
            throw new BusinessException("Emergency runtime changes require impact and communication confirmation.");
        }
        updateControl(featureId, FeatureOperationalControl.EMERGENCY_RUNTIME, enabled, reason,
                impactConfirmed, communicationConfirmed);
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
                registryPresent && feature.isPublicVisible(),
                registryPresent && feature.isNewSalesEnabled(),
                registryPresent && feature.isNewGrantsEnabled(),
                registryPresent && feature.isRuntimeEnabled(),
                registryPresent,
                definition.planAssignable(),
                definition.clientRoleGrantable(),
                definition.platformAdminRoleGrantable(),
                definition.b2bDelegatable(),
                definition.publicCatalogVisible(),
                definition.publicCatalogVisible(),
                definition.planAssignable(),
                definition.clientRoleGrantable() || definition.platformAdminRoleGrantable() || definition.b2bDelegatable(),
                definition.surface() == com.hiveapp.platform.registry.definition.FeatureSurface.CLIENT_WORKSPACE,
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
                .filter(permission -> currentRegistrySnapshot.containsAction(permission.getCode()))
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
            case ALL -> permission -> true;
            case CLIENT_ROLE_GRANTABLE -> permission -> {
                FeatureDefinition definition = definitionsByCode.get(featureCode(permission.getCode()));
                return definition != null && definition.isClientRoleGrantablePermission(permission.getCode());
            };
            case PLATFORM_ADMIN_ROLE_GRANTABLE -> permission -> {
                FeatureDefinition definition = definitionsByCode.get(featureCode(permission.getCode()));
                return definition != null && definition.isPlatformAdminRoleGrantablePermission(permission.getCode());
            };
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

    private boolean featureAvailableForAudience(Feature feature, FeatureCatalogAudience audience) {
        if (feature == null) {
            return audience == FeatureCatalogAudience.ALL;
        }
        return switch (audience) {
            case ALL -> true;
            case PLAN_ASSIGNABLE -> feature.isNewSalesEnabled() && feature.isRuntimeEnabled();
            case PUBLIC_CATALOG -> feature.getModule() != null && feature.getModule().isActive()
                    && feature.isPublicVisible() && feature.isNewSalesEnabled() && feature.isRuntimeEnabled()
                    && (feature.getStatus() == FeatureStatus.PUBLIC || feature.getStatus() == FeatureStatus.BETA);
        };
    }

    private boolean permissionFeatureAvailable(Feature feature, PermissionCatalogAudience audience) {
        if (feature == null) {
            return audience == PermissionCatalogAudience.ALL;
        }
        return audience == PermissionCatalogAudience.ALL
                || (feature.isNewGrantsEnabled() && feature.isRuntimeEnabled());
    }

    private void updateControl(
            UUID featureId,
            FeatureOperationalControl control,
            boolean enabled,
            String reason,
            boolean impactConfirmed,
            boolean communicationConfirmed) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("A reason is required for registry control changes.");
        }

        var catalogLock = catalogVersionService.lockForMutation();
        Feature feature = featureRepository.findByIdForUpdate(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));
        FeatureDefinition definition = featureDefinitionCollectorProvider.getObject()
                .collectByCode().get(feature.getCode());
        if (definition == null) {
            throw new BusinessException("Feature " + feature.getCode() + " is not backed by a current code definition.");
        }
        requireControlEligibility(definition, control);

        boolean previous = controlValue(feature, control);
        if (previous == enabled) {
            return;
        }
        setControlValue(feature, control, enabled);
        featureRepository.save(feature);

        var context = HiveAppContextHolder.getContext();
        if (context == null || context.actorUserId() == null) {
            throw new ForbiddenException("An authenticated platform operator is required.");
        }
        FeatureOperationalChange change = new FeatureOperationalChange();
        change.setFeature(feature);
        change.setControl(control);
        change.setActorUserId(context.actorUserId());
        change.setPreviousValue(previous);
        change.setNewValue(enabled);
        change.setReason(reason.trim());
        change.setImpactConfirmed(impactConfirmed);
        change.setCommunicationConfirmed(communicationConfirmed);
        operationalChangeRepository.save(change);
        catalogVersionService.bump(catalogLock);
    }

    private void requireControlEligibility(FeatureDefinition definition, FeatureOperationalControl control) {
        boolean eligible = switch (control) {
            case PUBLIC_VISIBILITY -> definition.publicCatalogVisible();
            case NEW_SALES -> definition.planAssignable();
            case NEW_GRANTS -> definition.clientRoleGrantable()
                    || definition.platformAdminRoleGrantable() || definition.b2bDelegatable();
            case EMERGENCY_RUNTIME -> definition.surface()
                    == com.hiveapp.platform.registry.definition.FeatureSurface.CLIENT_WORKSPACE;
        };
        if (!eligible) {
            throw new BusinessException("Feature " + definition.code() + " does not expose the "
                    + control + " operator control.");
        }
    }

    private boolean controlValue(Feature feature, FeatureOperationalControl control) {
        return switch (control) {
            case PUBLIC_VISIBILITY -> feature.isPublicVisible();
            case NEW_SALES -> feature.isNewSalesEnabled();
            case NEW_GRANTS -> feature.isNewGrantsEnabled();
            case EMERGENCY_RUNTIME -> feature.isRuntimeEnabled();
        };
    }

    private void setControlValue(Feature feature, FeatureOperationalControl control, boolean enabled) {
        switch (control) {
            case PUBLIC_VISIBILITY -> feature.setPublicVisible(enabled);
            case NEW_SALES -> feature.setNewSalesEnabled(enabled);
            case NEW_GRANTS -> feature.setNewGrantsEnabled(enabled);
            case EMERGENCY_RUNTIME -> feature.setRuntimeEnabled(enabled);
        }
    }
}
