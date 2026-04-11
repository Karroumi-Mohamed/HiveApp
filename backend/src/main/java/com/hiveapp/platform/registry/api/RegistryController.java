package com.hiveapp.platform.registry.api;

import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.entity.Feature;
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

    @GetMapping("/modules")
    public ResponseEntity<List<Module>> getAllModules() {
        return ResponseEntity.ok(registryService.getAllModules());
    }

    @PostMapping("/modules")
    public ResponseEntity<Module> createModule(@RequestParam String code, @RequestParam String name) {
        return ResponseEntity.ok(registryService.createModule(code, name));
    }

    @PostMapping("/modules/{id}/features")
    public ResponseEntity<Feature> createFeature(@PathVariable UUID id, @RequestParam String code, @RequestParam String name) {
        return ResponseEntity.ok(registryService.createFeature(id, code, name));
    }
}
