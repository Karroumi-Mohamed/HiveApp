package com.hiveapp.platform.registry.service.impl;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
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
    public List<Module> getFullInventory() {
        return moduleRepository.findAll();
    }

    @Override
    public List<Module> getPublicCatalog() {
        return moduleRepository.findAll().stream()
                .filter(Module::isActive)
                .filter(m -> m.getFeatures().stream()
                        .anyMatch(f -> f.getStatus() == FeatureStatus.PUBLIC && f.isActive()))
                .toList();
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
