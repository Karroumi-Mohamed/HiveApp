package com.hiveapp.platform.registry.service.impl;

import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.service.RegistryService;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistryServiceImpl implements RegistryService {

    private final ModuleRepository moduleRepository;
    private final FeatureRepository featureRepository;

    @Override
    public List<Module> getAllModules() {
        return moduleRepository.findAll();
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
        return featureRepository.save(feature);
    }

    @Override
    @Transactional
    public void toggleModule(UUID id, boolean active) {
        var module = moduleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Module", "id", id));
        module.setActive(active);
        moduleRepository.save(module);
    }
}
