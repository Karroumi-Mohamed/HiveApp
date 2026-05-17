package com.hiveapp.platform.registry.service.impl;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.RegistryFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import com.hiveapp.platform.registry.service.RegistryService;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = RegistryFeature.KEY, description = "Platform Registry Management")
public class RegistryServiceImpl extends PlatformControlFeatureService implements RegistryService {

    private final ModuleRepository moduleRepository;
    private final FeatureRepository featureRepository;

    @Override
    protected FeatureDefinition featureDefinition() {
        return RegistryFeature.definition();
    }

    @Override
    @PermissionNode(key = "read", description = "View full registry inventory including INTERNAL features")
    public List<Module> getFullInventory() {
        return moduleRepository.findAll();
    }

    @Override
    @PermissionNode(key = "catalog", description = "View public catalog of modules and features")
    public List<Module> getPublicCatalog() {
        return moduleRepository.findAll().stream()
                .filter(Module::isActive)
                .filter(m -> m.getFeatures().stream()
                        .anyMatch(f -> (f.getStatus() == FeatureStatus.PUBLIC || f.getStatus() == FeatureStatus.BETA) && f.isActive()))
                .peek(m -> {
                    // Filter features within the module to only PUBLIC and BETA
                    m.setFeatures(m.getFeatures().stream()
                            .filter(f -> (f.getStatus() == FeatureStatus.PUBLIC || f.getStatus() == FeatureStatus.BETA) && f.isActive())
                            .toList());
                })
                .toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_status", description = "Update feature visibility status")
    public void updateFeatureStatus(UUID featureId, FeatureStatus status) {
        var feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));
        feature.setStatus(status);
        featureRepository.save(feature);
    }
}
