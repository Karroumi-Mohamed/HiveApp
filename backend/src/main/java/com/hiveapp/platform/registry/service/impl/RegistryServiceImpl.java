package com.hiveapp.platform.registry.service.impl;

import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.service.RegistryService;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistryServiceImpl implements RegistryService {

    private final ModuleRepository moduleRepository;
    private final FeatureRepository featureRepository;

    @Override
    public List<Module> getFullInventory() {
        return moduleRepository.findAll();
    }

    @Override
    public List<Module> getPublicCatalog() {
        // Business Rule: Return only modules that have at least one PUBLIC feature
        return moduleRepository.findAll().stream()
            .filter(m -> m.isActive())
            // This is a simplified filter for the demo
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Module createModule(String code, String name) {
        Module module = new Module();
        module.setCode(code);
        module.setName(name);
        return moduleRepository.save(module);
    }

    @Override
    @Transactional
    public Feature createFeature(UUID moduleId, String code, String name) {
        var module = moduleRepository.findById(moduleId)
            .orElseThrow(() -> new ResourceNotFoundException("Module", "id", moduleId));
        Feature feature = new Feature();
        feature.setModule(module);
        feature.setCode(code);
        feature.setName(name);
        feature.setStatus(FeatureStatus.INTERNAL); // Default to internal for safety
        return featureRepository.save(feature);
    }

    @Override
    @Transactional
    public void updateFeatureStatus(UUID featureId, FeatureStatus status) {
        var feature = featureRepository.findById(featureId)
            .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));
        feature.setStatus(status);
        featureRepository.save(feature);
    }
}
