package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.definition.FeatureDefinitionException;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import dev.karroumi.permissionizer.CollectedPermission;
import dev.karroumi.permissionizer.PermissionCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Runs at startup (Order 2, after FeatureSeeder) to sync Permission rows
 * collected from @PermissionNode annotations via Permissionizer.
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

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    @Transactional
    public void seedPermissions() {
        log.info("Starting Permission Seeder...");
        seedPermissions(PermissionCollector.collect());
    }

    void seedPermissions(List<CollectedPermission> collected) {
        int created = 0;
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
                if (existing.get().getFeature() == null
                        || !featureCode.equals(existing.get().getFeature().getCode())) {
                    throw invalid("Persisted permission '" + cp.path()
                            + "' is not linked to Feature '" + featureCode + "'.");
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

        log.info("Permission Seeder complete — created: {}, skipped structural: {}",
                created, skippedStructural);
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
}
