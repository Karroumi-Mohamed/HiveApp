package com.hiveapp.platform.registry.api;

import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.dto.FeatureCatalogAudience;
import com.hiveapp.platform.registry.dto.PermissionCatalogAudience;
import com.hiveapp.platform.registry.dto.RegistryModuleReadModelDto;
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
    public ResponseEntity<List<Module>> getInventory() {
        return ResponseEntity.ok(registryService.getFullInventory());
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<Module>> getCatalog() {
        return ResponseEntity.ok(registryService.getPublicCatalog());
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

    @PatchMapping("/features/{id}/active")
    public ResponseEntity<Void> updateActive(@PathVariable UUID id, @RequestParam boolean active) {
        registryService.updateFeatureActive(id, active);
        return ResponseEntity.noContent().build();
    }
}
