package com.hiveapp.platform.registry.api;

import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.entity.Feature;
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
    @PermissionNode(key = "read", description = "View full registry inventory")
    public ResponseEntity<List<Module>> getInventory() {
        return ResponseEntity.ok(registryService.getFullInventory());
    }

    @PostMapping("/modules")
    @PermissionNode(key = "create_module", description = "Create a new module")
    public ResponseEntity<Module> createModule(@RequestParam String code, @RequestParam String name) {
        return ResponseEntity.ok(registryService.createModule(code, name));
    }

    @PostMapping("/modules/{id}/features")
    @PermissionNode(key = "create_feature", description = "Create a feature in a module")
    public ResponseEntity<Feature> createFeature(@PathVariable UUID id, @RequestParam String code, @RequestParam String name) {
        return ResponseEntity.ok(registryService.createFeature(id, code, name));
    }
}
