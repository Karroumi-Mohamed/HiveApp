package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import dev.karroumi.permissionizer.PermissionCollector;
import dev.karroumi.permissionizer.CollectedPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionSeeder {

    private final PermissionRepository permissionRepository;
    private final FeatureRepository featureRepository;
    private final ModuleRepository moduleRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedPermissions() {
        log.info("Starting Permission Seeder...");
        List<CollectedPermission> collected = PermissionCollector.collect();

        // Very basic seeding: Create default Module and Feature to attach permissions
        Module defaultModule = moduleRepository.findAll().stream().findFirst().orElseGet(() -> {
            Module m = new Module();
            m.setCode("CORE");
            m.setName("Core Platform");
            return moduleRepository.save(m);
        });

        Feature defaultFeature = featureRepository.findAll().stream().findFirst().orElseGet(() -> {
            Feature f = new Feature();
            f.setCode("CORE_FEATURE");
            f.setName("Core Feature");
            f.setModule(defaultModule);
            return featureRepository.save(f);
        });

        int newCount = 0;
        for (CollectedPermission cp : collected) {
            if (permissionRepository.findByCode(cp.path()).isEmpty()) {
                Permission p = new Permission();
                p.setCode(cp.path());
                p.setName(cp.path());
                p.setDescription(cp.description());
                p.setFeature(defaultFeature);
                p.setAction("MANAGED");
                p.setResource("RESOURCE");
                permissionRepository.save(p);
                newCount++;
            }
        }
        log.info("Seeded {} new permissions.", newCount);
    }
}
