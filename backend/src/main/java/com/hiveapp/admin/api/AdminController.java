package com.hiveapp.admin.api;

import com.hiveapp.admin.domain.dto.*;
import com.hiveapp.admin.domain.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/users/{userId}")
    public ResponseEntity<AdminUserResponse> createAdminUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "false") boolean superAdmin
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createAdminUser(userId, superAdmin));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> getAdminUser(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getAdminUserById(id));
    }

    @PostMapping("/users/{adminUserId}/roles/{roleId}")
    public ResponseEntity<Void> assignRole(@PathVariable UUID adminUserId, @PathVariable UUID roleId) {
        adminService.assignRoleToAdmin(adminUserId, roleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/roles")
    public ResponseEntity<AdminRoleResponse> createAdminRole(@Valid @RequestBody CreateAdminRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createAdminRole(request));
    }

    @GetMapping("/roles")
    public ResponseEntity<List<AdminRoleResponse>> getAllActiveRoles() {
        return ResponseEntity.ok(adminService.getAllActiveRoles());
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<AdminPermissionResponse>> getAllPermissions() {
        return ResponseEntity.ok(adminService.getAllPermissions());
    }
}
