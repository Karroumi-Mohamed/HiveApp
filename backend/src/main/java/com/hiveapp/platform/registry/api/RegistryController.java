package com.hiveapp.platform.registry.api;

import com.hiveapp.platform.registry.dto.EmergencyRuntimeUpdateRequest;
import com.hiveapp.platform.registry.dto.FeatureControlUpdateRequest;
import com.hiveapp.platform.registry.dto.FeatureOperationalChangeDto;
import jakarta.validation.Valid;
import com.hiveapp.platform.registry.dto.FeatureCatalogAudience;
import com.hiveapp.platform.registry.dto.PermissionCatalogAudience;
import com.hiveapp.platform.registry.dto.RegistryModuleReadModelDto;
import com.hiveapp.platform.registry.dto.RegistrySyncRunDto;
import com.hiveapp.platform.registry.service.RegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/registry")
@RequiredArgsConstructor
public class RegistryController {

    private final RegistryService registryService;

    @GetMapping("/inventory")
    public ResponseEntity<List<RegistryModuleReadModelDto>> getInventory() {
        return ResponseEntity.ok(registryService.getFullInventory());
    }

    @GetMapping("/feature-catalog")
    public ResponseEntity<List<RegistryModuleReadModelDto>> getFeatureCatalog(
            @RequestParam(defaultValue = "ALL") FeatureCatalogAudience audience
    ) {
        return ResponseEntity.ok(registryService.getFeatureCatalog(audience));
    }

    @GetMapping("/permission-catalog")
    public ResponseEntity<List<RegistryModuleReadModelDto>> getPermissionCatalog(
            @RequestParam(defaultValue = "ALL") PermissionCatalogAudience audience
    ) {
        return ResponseEntity.ok(registryService.getPermissionCatalog(audience));
    }

    @GetMapping("/synchronization/latest")
    public ResponseEntity<RegistrySyncRunDto> getLatestSynchronizationRun() {
        return ResponseEntity.ok(registryService.getLatestSynchronizationRun());
    }

    @GetMapping("/features/{id}/control-history")
    public ResponseEntity<List<FeatureOperationalChangeDto>> getFeatureControlHistory(
            @PathVariable UUID id) {
        return ResponseEntity.ok(registryService.getFeatureControlHistory(id));
    }

    @PatchMapping("/features/{id}/public-visibility")
    public ResponseEntity<Void> updatePublicVisibility(
            @PathVariable UUID id, @Valid @RequestBody FeatureControlUpdateRequest request) {
        registryService.updatePublicVisibility(id, request.enabled(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/features/{id}/new-sales")
    public ResponseEntity<Void> updateNewSales(
            @PathVariable UUID id, @Valid @RequestBody FeatureControlUpdateRequest request) {
        registryService.updateNewSales(id, request.enabled(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/features/{id}/new-grants")
    public ResponseEntity<Void> updateNewGrants(
            @PathVariable UUID id, @Valid @RequestBody FeatureControlUpdateRequest request) {
        registryService.updateNewGrants(id, request.enabled(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/features/{id}/emergency-runtime")
    public ResponseEntity<Void> updateEmergencyRuntime(
            @PathVariable UUID id, @Valid @RequestBody EmergencyRuntimeUpdateRequest request) {
        registryService.updateEmergencyRuntime(id, request.enabled(), request.reason(),
                request.impactConfirmed(), request.communicationConfirmed());
        return ResponseEntity.noContent().build();
    }
}
