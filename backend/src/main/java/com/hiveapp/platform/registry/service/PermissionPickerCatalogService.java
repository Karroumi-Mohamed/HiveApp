package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.dto.PermissionPickerFeatureDto;
import com.hiveapp.platform.registry.dto.PermissionPickerModuleDto;
import com.hiveapp.platform.registry.dto.PermissionPickerPermissionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionPickerCatalogService {

    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    private final PermissionRepository permissionRepository;
    private final PlanEntitlementService planEntitlementService;

    public List<PermissionPickerModuleDto> clientRoleCatalog(UUID accountId) {
        return catalog(
                accountId,
                FeatureDefinition::clientRoleGrantable,
                (definition, permission) -> true
        );
    }

    public List<PermissionPickerModuleDto> b2bDelegationCatalog(UUID providerAccountId) {
        return catalog(
                providerAccountId,
                FeatureDefinition::b2bDelegatable,
                (definition, permission) -> definition.isB2bDelegatablePermission(permission.getCode())
        );
    }

    private List<PermissionPickerModuleDto> catalog(
            UUID accountId,
            Predicate<FeatureDefinition> featureFilter,
            BiPredicate<FeatureDefinition, Permission> permissionFilter
    ) {
        Map<String, List<Permission>> permissionsByFeature = permissionRepository.findAll().stream()
                .filter(permission -> planEntitlementService.isPermissionEntitled(accountId, permission.getCode()))
                .collect(Collectors.groupingBy(permission -> featureCode(permission.getCode())));

        return featureDefinitionCollectorProvider.getObject().collect().stream()
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
