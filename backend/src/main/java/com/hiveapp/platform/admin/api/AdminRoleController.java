package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.dto.CreateAdminRoleRequest;
import com.hiveapp.platform.admin.dto.GrantAdminPermissionRequest;
import com.hiveapp.platform.admin.dto.UpdateAdminRoleRequest;
import com.hiveapp.platform.admin.service.AdminRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    public AdminRole create(@Valid @RequestBody CreateAdminRoleRequest req) {
        return adminRoleService.createAdminRole(req.name(), req.description());
    }

    @PutMapping("/{id}")
    public AdminRole update(@PathVariable UUID id, @Valid @RequestBody UpdateAdminRoleRequest req) {
        return adminRoleService.updateAdminRole(id, req.name(), req.description());
    }

    @PostMapping("/{id}/toggle-active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void toggleActive(@PathVariable UUID id) {
        adminRoleService.toggleActive(id);
    }

    // ── Permission assignments ─────────────────────────────────────────────────

    @PostMapping("/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantPermission(@PathVariable UUID id, @Valid @RequestBody GrantAdminPermissionRequest req) {
        adminRoleService.grantPermission(id, req.adminPermissionId());
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePermission(@PathVariable UUID id, @PathVariable UUID permissionId) {
        adminRoleService.revokePermission(id, permissionId);
    }
}
