package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import java.util.List;
import java.util.UUID;

public interface RegistryService {
    // Admin Only
    List<Module> getFullInventory();
    Module createModule(String code, String name);
    Feature createFeature(UUID moduleId, String code, String name);
    void updateFeatureStatus(UUID featureId, FeatureStatus status);
    
    // Public Catalog
    List<Module> getPublicCatalog();
}
