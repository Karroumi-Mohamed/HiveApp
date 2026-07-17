package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.definition.FeatureDefinitionException;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import dev.karroumi.permissionizer.CollectedPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Internal writer used by RegistryStartupSynchronizer inside one registry transaction.
 *
 * Only concrete action permissions are persisted:
 *   "<module>.<feature>.<action>"
 *
 * Module and feature root nodes collected from Permissionizer are structural and
 * intentionally skipped. Deeper paths are invalid for HiveApp's feature model
 * and fail startup so the permission tree cannot diverge from the business registry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionSeeder {

    private final PermissionRepository permissionRepository;
    private final FeatureRepository featureRepository;

    SeedResult seedPermissions(List<CollectedPermission> collected) {
        log.info("Starting Permission Seeder...");
        int created = 0;
        int updated = 0;
        int skippedStructural = 0;

        for (CollectedPermission cp : collected) {
            if (isStructuralNode(cp.path())) {
                skippedStructural++;
                continue;
            }

            if (!isStrictActionPermission(cp.path())) {
                throw invalid("Permission '" + cp.path()
                        + "' does not match required shape '<module>.<feature>.<action>'. "
                        + "Move this action under a two-segment FeatureDefinition.");
            }

            String featureCode = extractFeatureCode(cp.path());
            Feature feature = featureRepository.findByCode(featureCode)
                    .orElseThrow(() -> invalid("Permission '" + cp.path()
                            + "' has no matching Feature '" + featureCode + "'. "
                            + "Declare a FeatureDefinition with code '" + featureCode + "'."));
            String moduleCode = extractModuleCode(cp.path());
            if (feature.getModule() == null || !moduleCode.equals(feature.getModule().getCode())) {
                throw invalid("Permission '" + cp.path() + "' maps to Feature '" + featureCode
                        + "' outside its declared module '" + moduleCode + "'.");
            }

            var existing = permissionRepository.findByCode(cp.path());
            if (existing.isPresent()) {
                Permission permission = existing.get();
                boolean changed = permission.getFeature() == null
                        || !featureCode.equals(permission.getFeature().getCode())
                        || !Objects.equals(permission.getName(), cp.path())
                        || !Objects.equals(permission.getDescription(), cp.description())
                        || !Objects.equals(permission.getResource(), featureCode)
                        || !Objects.equals(permission.getAction(), cp.key());
                if (changed) {
                    permission.setName(cp.path());
                    permission.setDescription(cp.description());
                    permission.setResource(featureCode);
                    permission.setAction(cp.key());
                    permission.setFeature(feature);
                    permissionRepository.save(permission);
                    updated++;
                }
                continue;
            }

            Permission p = new Permission();
            p.setCode(cp.path());
            p.setName(cp.path());
            p.setDescription(cp.description());
            p.setResource(featureCode);
            p.setAction(cp.key());
            p.setFeature(feature);
            permissionRepository.save(p);
            created++;
        }

        Set<String> currentActionCodes = collected.stream()
                .filter(permission -> isStrictActionPermission(permission.path()))
                .map(CollectedPermission::path)
                .collect(Collectors.toUnmodifiableSet());
        List<String> orphanedPermissionCodes = permissionRepository.findAll().stream()
                .map(Permission::getCode)
                .filter(code -> !currentActionCodes.contains(code))
                .sorted()
                .toList();

        log.info("Permission Seeder complete — created: {}, updated: {}, orphaned: {}",
                created, updated, orphanedPermissionCodes.size());
        return new SeedResult(created, updated, skippedStructural, orphanedPermissionCodes);
    }

    static boolean isStructuralNode(String permissionCode) {
        return dotCount(permissionCode) < 2;
    }

    static boolean isStrictActionPermission(String permissionCode) {
        return dotCount(permissionCode) == 2;
    }

    static String extractFeatureCode(String permissionCode) {
        int lastDot = permissionCode.lastIndexOf('.');
        if (lastDot == -1) return permissionCode;
        return permissionCode.substring(0, lastDot);
    }

    static String extractModuleCode(String permissionCode) {
        int firstDot = permissionCode.indexOf('.');
        if (firstDot == -1) return permissionCode;
        return permissionCode.substring(0, firstDot);
    }

    private FeatureDefinitionException invalid(String message) {
        return new FeatureDefinitionException(message);
    }

    private static long dotCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return value.chars().filter(c -> c == '.').count();
    }

    public record SeedResult(
            int permissionsCreated,
            int permissionsUpdated,
            int structuralNodesSkipped,
            List<String> orphanedPermissionCodes) {
        public SeedResult(int permissionsCreated, int permissionsUpdated, int structuralNodesSkipped) {
            this(permissionsCreated, permissionsUpdated, structuralNodesSkipped, List.of());
        }

        public SeedResult {
            orphanedPermissionCodes = List.copyOf(orphanedPermissionCodes);
        }
    }
}
