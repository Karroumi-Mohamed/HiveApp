package com.hiveapp.module.api;

import com.hiveapp.module.domain.dto.*;
import com.hiveapp.module.domain.service.ModuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ModuleResponse> createModule(@Valid @RequestBody CreateModuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moduleService.createModule(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ModuleResponse>> getAllActiveModules() {
        return ResponseEntity.ok(moduleService.getAllActiveModules());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ModuleResponse> getModuleById(@PathVariable UUID id) {
        return ResponseEntity.ok(moduleService.getModuleById(id));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ModuleResponse> getModuleByCode(@PathVariable String code) {
        return ResponseEntity.ok(moduleService.getModuleByCode(code));
    }

    @PostMapping("/features")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureResponse> createFeature(@Valid @RequestBody CreateFeatureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moduleService.createFeature(request));
    }

    @GetMapping("/{moduleId}/features")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FeatureResponse>> getFeaturesByModule(@PathVariable UUID moduleId) {
        return ResponseEntity.ok(moduleService.getFeaturesByModuleId(moduleId));
    }
}
