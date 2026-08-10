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
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal writer used by RegistryStartupSynchronizer inside one registry transaction.
 *
 * Module code is derived from the first segment of each feature code:
 *   "hr.employees" -> module "hr"
 *
 * quota_schema, lifecycle status, and sort_order are always overwritten from feature definitions — code is source of truth.
 * Admin-managed visibility, sale, grant, and runtime controls are never overwritten by the seeder.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureSeeder {

    private final ModuleRepository moduleRepository;
    private final FeatureRepository featureRepository;
    private final FeatureDefinitionCollector featureDefinitionCollector;

    public SeedResult seedFeatures() {
        return synchronize(featureDefinitionCollector.collect());
    }

    SeedResult synchronize(List<FeatureDefinition> definitions) {
        log.info("Starting Feature Seeder...");
        int modulesCreated = 0;
        int featuresCreated = 0;
        int featuresUpdated = 0;
        Map<String, Module> modules = new HashMap<>();

        for (FeatureDefinition definition : definitions) {
            SeedResult result = syncFeature(
                    definition.code(),
                    definition.moduleCode(),
                    definition.lifecycleStatus(),
                    definition.quotaSlots(),
                    definition.sortOrder(),
                    modules);
            modulesCreated += result.modulesCreated();
            featuresCreated += result.featuresCreated();
            featuresUpdated += result.featuresUpdated();
        }

        log.info("Feature Seeder complete — modules created: {}, features created: {}, quota schemas synced: {}",
                modulesCreated, featuresCreated, featuresUpdated);
        return new SeedResult(modulesCreated, featuresCreated, featuresUpdated);
    }

    private SeedResult syncFeature(String featureCode, String moduleCode, FeatureStatus lifecycleStatus,
                                   List<QuotaSlot> quotaSlots, int sortOrder,
                                   Map<String, Module> modules) {
        int modulesCreated = 0;
        int featuresCreated = 0;
        int featuresUpdated = 0;

        Module module = modules.get(moduleCode);
        if (module == null) {
            var moduleLookup = moduleRepository.findByCode(moduleCode);
            module = moduleLookup.orElseGet(() -> {
                Module created = new Module();
                created.setCode(moduleCode);
                return moduleRepository.save(created);
            });
            modules.put(moduleCode, module);
            if (moduleLookup.isEmpty()) {
                modulesCreated++;
            }
        }

        var existing = featureRepository.findByCode(featureCode);
        if (existing.isEmpty()) {
            Feature feature = new Feature();
            feature.setCode(featureCode);
            feature.setModule(module);
            feature.setStatus(lifecycleStatus);
            feature.setQuotaSchema(quotaSlots);
            feature.setSortOrder(sortOrder);
            featureRepository.save(feature);
            featuresCreated++;
        } else {
            Feature feature = existing.get();
            boolean changed = feature.getModule() == null
                    || !moduleCode.equals(feature.getModule().getCode())
                    || feature.getStatus() != lifecycleStatus
                    || !Objects.equals(feature.getQuotaSchema(), quotaSlots)
                    || feature.getSortOrder() != sortOrder;
            if (changed) {
                feature.setModule(module);
                feature.setStatus(lifecycleStatus);
                feature.setQuotaSchema(quotaSlots);
                feature.setSortOrder(sortOrder);
                featureRepository.save(feature);
                featuresUpdated++;
            }
        }

        return new SeedResult(modulesCreated, featuresCreated, featuresUpdated);
    }

    public record SeedResult(int modulesCreated, int featuresCreated, int featuresUpdated) {
    }
}
