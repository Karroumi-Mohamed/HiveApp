package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.dto.admin.PermissionCatalogAudience;
import com.hiveapp.platform.registry.dto.picker.PermissionPickerCatalogDto;
import com.hiveapp.platform.registry.dto.picker.PermissionPickerFeatureDto;
import com.hiveapp.platform.registry.dto.picker.PermissionPickerModuleDto;
import com.hiveapp.platform.registry.dto.picker.PermissionPickerPermissionDto;
import com.hiveapp.platform.registry.dto.picker.PermissionPickerSelectionDto;
import com.hiveapp.platform.registry.dto.picker.PermissionUnavailableReason;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionPickerCatalogService {

    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    private final PermissionRepository permissionRepository;
    private final FeatureRepository featureRepository;
    private final PlanEntitlementService planEntitlementService;
    private final CurrentRegistrySnapshot currentRegistrySnapshot;
    private final RegistryCatalogVersionService catalogVersionService;

    public PermissionPickerCatalogDto clientRoleCatalog(UUID accountId, Set<String> currentSelections) {
        return catalog(
                accountId,
                PermissionCatalogAudience.CLIENT_ROLE_GRANTABLE,
                currentSelections,
                FeatureDefinition::clientRoleGrantable,
                (definition, permission) -> definition.isClientRoleGrantablePermission(permission.getCode())
        );
    }

    public PermissionPickerCatalogDto b2bDelegationCatalog(
            UUID providerAccountId, Set<String> currentSelections) {
        return catalog(
                providerAccountId,
                PermissionCatalogAudience.B2B_DELEGATABLE,
                currentSelections,
                FeatureDefinition::b2bDelegatable,
                (definition, permission) -> definition.isB2bDelegatablePermission(permission.getCode())
        );
    }

    private PermissionPickerCatalogDto catalog(
            UUID accountId,
            PermissionCatalogAudience audience,
            Set<String> currentSelections,
            Predicate<FeatureDefinition> featureFilter,
            BiPredicate<FeatureDefinition, Permission> permissionFilter
    ) {
        List<FeatureDefinition> definitions = featureDefinitionCollectorProvider.getObject().collect();
        Map<String, FeatureDefinition> definitionsByCode = definitions.stream()
                .collect(Collectors.toMap(FeatureDefinition::code, Function.identity()));
        Map<String, Feature> featuresByCode = featureRepository.findAll().stream()
                .collect(Collectors.toMap(Feature::getCode, Function.identity()));
        Set<String> entitledFeatureCodes = planEntitlementService.entitledFeatureCodes(accountId);
        List<Permission> currentPermissions = permissionRepository.findAll().stream()
                .filter(permission -> currentRegistrySnapshot.containsAction(permission.getCode()))
                .toList();
        Map<String, List<Permission>> permissionsByFeature = currentPermissions.stream()
                .filter(permission -> entitledFeatureCodes.contains(featureCode(permission.getCode())))
                .filter(permission -> featureAllowsNewGrant(
                        featuresByCode.get(featureCode(permission.getCode()))))
                .collect(Collectors.groupingBy(permission -> featureCode(permission.getCode())));

        List<PermissionPickerModuleDto> availableChoices = definitions.stream()
                .filter(featureFilter)
                .sorted(Comparator.comparing(FeatureDefinition::moduleCode)
                        .thenComparing(FeatureDefinition::sortOrder)
                        .thenComparing(FeatureDefinition::code))
                .collect(Collectors.groupingBy(
                        FeatureDefinition::moduleCode,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new PermissionPickerModuleDto(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(definition -> toFeatureDto(
                                        definition,
                                        permissionsByFeature.getOrDefault(definition.code(), List.of()).stream()
                                                .filter(permission -> definition.ownsPermission(permission.getCode()))
                                                .filter(permission -> permissionFilter.test(definition, permission))
                                                .sorted(Comparator.comparing(Permission::getCode))
                                                .toList()))
                                .filter(feature -> !feature.permissions().isEmpty())
                                .toList()))
                .filter(module -> !module.features().isEmpty())
                .toList();

        Set<String> availableCodes = availableChoices.stream()
                .flatMap(module -> module.features().stream())
                .flatMap(feature -> feature.permissions().stream())
                .map(PermissionPickerPermissionDto::code)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> selections = currentSelections == null ? Set.of() : Set.copyOf(currentSelections);
        List<PermissionPickerSelectionDto> selectionDtos = selections.stream()
                .sorted()
                .map(code -> selection(code, availableCodes, definitionsByCode, featuresByCode,
                        entitledFeatureCodes, featureFilter, permissionFilter, currentPermissions))
                .toList();

        return new PermissionPickerCatalogDto(
                catalogVersionService.currentVersion(), audience, availableChoices, selectionDtos);
    }

    private PermissionPickerSelectionDto selection(
            String code,
            Set<String> availableCodes,
            Map<String, FeatureDefinition> definitionsByCode,
            Map<String, Feature> featuresByCode,
            Set<String> entitledFeatureCodes,
            Predicate<FeatureDefinition> featureFilter,
            BiPredicate<FeatureDefinition, Permission> permissionFilter,
            List<Permission> currentPermissions) {
        if (availableCodes.contains(code)) {
            return new PermissionPickerSelectionDto(code, true, null, null);
        }
        PermissionUnavailableReason reason;
        String featureCode = featureCode(code);
        FeatureDefinition definition = definitionsByCode.get(featureCode);
        Feature feature = featuresByCode.get(featureCode);
        Permission permission = currentPermissions.stream()
                .filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (!currentRegistrySnapshot.containsAction(code) || definition == null || permission == null) {
            reason = PermissionUnavailableReason.NOT_IN_CURRENT_REGISTRY;
        } else if (feature == null || !feature.isRuntimeEnabled()) {
            reason = PermissionUnavailableReason.EMERGENCY_RUNTIME_DISABLED;
        } else if (!entitledFeatureCodes.contains(featureCode)) {
            reason = PermissionUnavailableReason.NOT_ENTITLED;
        } else if (!feature.isNewGrantsEnabled()) {
            reason = PermissionUnavailableReason.NEW_GRANTS_PAUSED;
        } else if (!featureFilter.test(definition) || !permissionFilter.test(definition, permission)) {
            reason = PermissionUnavailableReason.NOT_GRANTABLE_FOR_AUDIENCE;
        } else {
            reason = PermissionUnavailableReason.NOT_IN_CURRENT_REGISTRY;
        }
        return new PermissionPickerSelectionDto(code, false, reason, explanation(reason));
    }

    private boolean featureAllowsNewGrant(Feature feature) {
        return feature != null && feature.isNewGrantsEnabled() && feature.isRuntimeEnabled();
    }

    private String explanation(PermissionUnavailableReason reason) {
        return switch (reason) {
            case NOT_IN_CURRENT_REGISTRY -> "This permission is no longer present in the current registry.";
            case NOT_ENTITLED -> "The account's current subscription does not include this feature.";
            case NOT_GRANTABLE_FOR_AUDIENCE -> "This action cannot be newly granted to this audience.";
            case NEW_GRANTS_PAUSED -> "New grants for this feature are currently paused.";
            case EMERGENCY_RUNTIME_DISABLED -> "This feature is currently disabled at runtime.";
        };
    }

    private PermissionPickerFeatureDto toFeatureDto(FeatureDefinition definition, List<Permission> permissions) {
        return new PermissionPickerFeatureDto(
                definition.code(),
                definition.displayName(),
                definition.description(),
                permissions.stream()
                        .map(this::toPermissionDto)
                        .toList()
        );
    }

    private PermissionPickerPermissionDto toPermissionDto(Permission permission) {
        return new PermissionPickerPermissionDto(
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getAction(),
                permission.getResource()
        );
    }

    private static String featureCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return "";
        }
        int lastDot = permissionCode.lastIndexOf('.');
        if (lastDot < 1) {
            return "";
        }
        return permissionCode.substring(0, lastDot);
    }
}
