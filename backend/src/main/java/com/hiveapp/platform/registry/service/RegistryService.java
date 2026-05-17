package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.dto.FeatureCatalogAudience;
import com.hiveapp.platform.registry.dto.PermissionCatalogAudience;
import com.hiveapp.platform.registry.dto.RegistryModuleReadModelDto;
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
    List<RegistryModuleReadModelDto> getFeatureCatalog(FeatureCatalogAudience audience);
    List<RegistryModuleReadModelDto> getPermissionCatalog(PermissionCatalogAudience audience);
    void updateFeatureStatus(UUID featureId, FeatureStatus status);
}
