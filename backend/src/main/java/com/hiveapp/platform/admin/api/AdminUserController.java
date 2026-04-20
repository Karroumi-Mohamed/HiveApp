package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.dto.AssignAdminRoleRequest;
import com.hiveapp.platform.admin.dto.CreateAdminUserRequest;
import com.hiveapp.platform.admin.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<List<AdminUser>> getAll() {
        return ResponseEntity.ok(adminUserService.getAllAdminUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUser> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.getAdminUser(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUser create(@Valid @RequestBody CreateAdminUserRequest req) {
        return adminUserService.createAdminUser(req.userId(), req.isSuperAdmin());
    }

    @PostMapping("/{id}/toggle-active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void toggleActive(@PathVariable UUID id) {
        adminUserService.toggleActive(id);
    }

    // ── Role assignments ──────────────────────────────────────────────────────

    @PostMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRole(@PathVariable UUID id, @Valid @RequestBody AssignAdminRoleRequest req) {
        adminUserService.assignRole(id, req.adminRoleId());
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        adminUserService.removeRole(id, roleId);
    }
}
