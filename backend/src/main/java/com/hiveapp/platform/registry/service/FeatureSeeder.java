package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import com.hiveapp.shared.quota.AppFeature;
import com.hiveapp.shared.quota.FeatureProvider;
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
 * from all registered AppFeature enums.
 *
 * Module code is derived from the first segment of each feature code:
 *   "hr.employees" -> module "hr"
 *
 * quota_schema is always overwritten from enum declarations — code is source of truth.
 * Admin-managed values (status, sort_order, is_active) are never overwritten by the seeder.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureSeeder {

    private final ModuleRepository moduleRepository;
    private final FeatureRepository featureRepository;
    private final List<FeatureProvider> featureProviders;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Transactional
    public void seedFeatures() {
        log.info("Starting Feature Seeder...");
        int modulesCreated = 0;
        int featuresCreated = 0;
        int featuresUpdated = 0;

        for (FeatureProvider provider : featureProviders) {
            for (AppFeature appFeature : provider.features()) {
                String featureCode = appFeature.code();
                String moduleCode  = extractModuleCode(featureCode);

                // Ensure Module exists
                Module module = moduleRepository.findByCode(moduleCode)
                        .orElseGet(() -> {
                            Module m = new Module();
                            m.setCode(moduleCode);
                            return moduleRepository.save(m);
                        });
                if (module.getId() == null || moduleRepository.findByCode(moduleCode).isEmpty()) {
                    modulesCreated++;
                }

                // Ensure Feature exists and sync quota_schema
                var existing = featureRepository.findByCode(featureCode);
                if (existing.isEmpty()) {
                    Feature f = new Feature();
                    f.setCode(featureCode);
                    f.setModule(module);
                    f.setStatus(FeatureStatus.INTERNAL);
                    f.setQuotaSchema(appFeature.quotaSlots());
                    featureRepository.save(f);
                    featuresCreated++;
                } else {
                    // Always sync quota_schema — code is source of truth
                    Feature f = existing.get();
                    f.setQuotaSchema(appFeature.quotaSlots());
                    featureRepository.save(f);
                    featuresUpdated++;
                }
            }
        }

        log.info("Feature Seeder complete — modules created: {}, features created: {}, quota schemas synced: {}",
                modulesCreated, featuresCreated, featuresUpdated);
    }

    private String extractModuleCode(String featureCode) {
        int dot = featureCode.indexOf('.');
        if (dot == -1) return featureCode;
        return featureCode.substring(0, dot);
    }
}
