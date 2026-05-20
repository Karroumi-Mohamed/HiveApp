package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import com.hiveapp.shared.quota.QuotaSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Runs at startup (Order 1, before PermissionSeeder) to sync Module and Feature rows
 * from code-owned FeatureDefinition contributors.
 *
 * Module code is derived from the first segment of each feature code:
 *   "hr.employees" -> module "hr"
 *
 * quota_schema is always overwritten from feature definitions — code is source of truth.
 * Admin-managed values (status, sort_order, is_active) are never overwritten by the seeder.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureSeeder {

    private final ModuleRepository moduleRepository;
    private final FeatureRepository featureRepository;
    private final FeatureDefinitionCollector featureDefinitionCollector;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Transactional
    public void seedFeatures() {
        log.info("Starting Feature Seeder...");
        int modulesCreated = 0;
        int featuresCreated = 0;
        int featuresUpdated = 0;

        for (FeatureDefinition definition : featureDefinitionCollector.collect()) {
            SeedResult result = syncFeature(
                    definition.code(),
                    definition.moduleCode(),
                    definition.quotaSlots(),
                    definition.sortOrder(),
                    definition.publicCatalogVisible() ? FeatureStatus.PUBLIC : FeatureStatus.INTERNAL);
            modulesCreated += result.modulesCreated();
            featuresCreated += result.featuresCreated();
            featuresUpdated += result.featuresUpdated();
        }

        log.info("Feature Seeder complete — modules created: {}, features created: {}, quota schemas synced: {}",
                modulesCreated, featuresCreated, featuresUpdated);
    }

    private SeedResult syncFeature(String featureCode, String moduleCode, List<QuotaSlot> quotaSlots,
                                   int sortOrder, FeatureStatus initialStatus) {
        int modulesCreated = 0;
        int featuresCreated = 0;
        int featuresUpdated = 0;

        var moduleLookup = moduleRepository.findByCode(moduleCode);
        Module module = moduleLookup.orElseGet(() -> {
            Module m = new Module();
            m.setCode(moduleCode);
            return moduleRepository.save(m);
        });
        if (moduleLookup.isEmpty()) {
            modulesCreated++;
        }

        var existing = featureRepository.findByCode(featureCode);
        if (existing.isEmpty()) {
            Feature feature = new Feature();
            feature.setCode(featureCode);
            feature.setModule(module);
            feature.setStatus(initialStatus);
            feature.setQuotaSchema(quotaSlots);
            feature.setSortOrder(sortOrder);
            featureRepository.save(feature);
            featuresCreated++;
        } else {
            Feature feature = existing.get();
            feature.setQuotaSchema(quotaSlots);
            feature.setSortOrder(sortOrder);
            featureRepository.save(feature);
            featuresUpdated++;
        }

        return new SeedResult(modulesCreated, featuresCreated, featuresUpdated);
    }

    private record SeedResult(int modulesCreated, int featuresCreated, int featuresUpdated) {
    }
}
