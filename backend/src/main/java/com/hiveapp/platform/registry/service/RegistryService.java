package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Module;
import java.util.List;
import java.util.UUID;

/**
 * Registry operations available to Platform Admins.
 * Modules and Features are seeded from code — admins cannot create them via API.
 * Admin responsibilities: visibility lifecycle and plan composition.
 */
public interface RegistryService {
    List<Module> getFullInventory();
    List<Module> getPublicCatalog();
    void updateFeatureStatus(UUID featureId, FeatureStatus status);
}
