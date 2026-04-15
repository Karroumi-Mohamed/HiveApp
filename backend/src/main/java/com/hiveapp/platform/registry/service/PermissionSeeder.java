package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Permission;
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
 * Links each permission to its Feature by naming convention:
 *   Drop the last segment of the permission code to get the feature code.
 *   "hr.employees.read" -> feature code "hr.employees"
 *
 * Permissions with no matching Feature are skipped with a warning —
 * this means a developer added a @PermissionNode without declaring an AppFeature enum for it.
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
        List<CollectedPermission> collected = PermissionCollector.collect();

        int created = 0;
        int skipped = 0;

        for (CollectedPermission cp : collected) {
            if (permissionRepository.findByCode(cp.path()).isPresent()) continue;

            String featureCode = extractFeatureCode(cp.path());
            Feature feature = featureRepository.findByCode(featureCode).orElse(null);

            if (feature == null) {
                log.warn("Permission '{}' has no matching Feature '{}' — skipping. " +
                         "Declare an AppFeature enum with code '{}' to fix this.",
                         cp.path(), featureCode, featureCode);
                skipped++;
                continue;
            }

            Permission p = new Permission();
            p.setCode(cp.path());
            p.setName(cp.path());
            p.setDescription(cp.description());
            p.setFeature(feature);
            permissionRepository.save(p);
            created++;
        }

        log.info("Permission Seeder complete — created: {}, skipped (no feature): {}", created, skipped);
    }

    private String extractFeatureCode(String permissionCode) {
        int lastDot = permissionCode.lastIndexOf('.');
        if (lastDot == -1) return permissionCode;
        return permissionCode.substring(0, lastDot);
    }
}
