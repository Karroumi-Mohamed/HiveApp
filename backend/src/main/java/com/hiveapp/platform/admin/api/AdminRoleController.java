package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.service.AdminRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    @GetMapping
    public ResponseEntity<List<AdminRole>> getAll() {
        return ResponseEntity.ok(adminRoleService.getAllAdminRoles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminRole> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminRoleService.getAdminRole(id));
    }

    @PostMapping
    public ResponseEntity<AdminRole> create(@RequestParam String name, @RequestParam String description) {
        return ResponseEntity.ok(adminRoleService.createAdminRole(name, description));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        adminRoleService.deactivateAdminRole(id);
        return ResponseEntity.noContent().build();
    }
}
