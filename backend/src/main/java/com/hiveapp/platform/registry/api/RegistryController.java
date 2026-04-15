package com.hiveapp.platform.registry.api;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.service.RegistryService;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/registry")
@RequiredArgsConstructor
@PermissionNode(key = "registry", description = "Platform Registry Management")
public class RegistryController {

    private final RegistryService registryService;

    @GetMapping("/inventory")
    @PermissionNode(key = "read", description = "View full registry inventory including INTERNAL features")
    public ResponseEntity<List<Module>> getInventory() {
        return ResponseEntity.ok(registryService.getFullInventory());
    }

    @GetMapping("/catalog")
    @PermissionNode(key = "catalog", description = "View public catalog of modules and features")
    public ResponseEntity<List<Module>> getCatalog() {
        return ResponseEntity.ok(registryService.getPublicCatalog());
    }

    @PatchMapping("/features/{id}/status")
    @PermissionNode(key = "update_status", description = "Update feature visibility status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @RequestParam FeatureStatus status) {
        registryService.updateFeatureStatus(id, status);
        return ResponseEntity.noContent().build();
    }
}
