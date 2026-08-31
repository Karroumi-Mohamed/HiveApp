package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.dto.admin.FeatureCatalogAudience;
import com.hiveapp.platform.registry.dto.admin.PermissionCatalogAudience;
import com.hiveapp.platform.registry.dto.admin.RegistryModuleReadModelDto;
import com.hiveapp.platform.registry.dto.admin.RegistrySyncRunDto;
import com.hiveapp.platform.registry.dto.admin.FeatureOperationalChangeDto;
import java.util.List;
import java.util.UUID;

/**
 * Registry operations available to Platform Admins.
 * Modules and Features are seeded from code — admins cannot create them via API.
 * Admin responsibilities: catalog activation and plan composition.
 */
public interface RegistryService {
    List<RegistryModuleReadModelDto> getFullInventory();
    List<RegistryModuleReadModelDto> getFeatureCatalog(FeatureCatalogAudience audience);
    List<RegistryModuleReadModelDto> getPermissionCatalog(PermissionCatalogAudience audience);
    RegistrySyncRunDto getLatestSynchronizationRun();
    List<FeatureOperationalChangeDto> getFeatureControlHistory(UUID featureId);
    void updatePublicVisibility(UUID featureId, boolean enabled, String reason);
    void updateNewSales(UUID featureId, boolean enabled, String reason);
    void updateNewGrants(UUID featureId, boolean enabled, String reason);
    void updateEmergencyRuntime(UUID featureId, boolean enabled, String reason,
                                boolean impactConfirmed, boolean communicationConfirmed);
}
