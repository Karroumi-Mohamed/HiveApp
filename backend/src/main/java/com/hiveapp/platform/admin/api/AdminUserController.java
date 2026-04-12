package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.service.AdminUserService;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PermissionNode(key = "users", description = "Platform User Management")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PermissionNode(key = "read", description = "List admin users")
    public ResponseEntity<List<AdminUser>> getAll() {
        return ResponseEntity.ok(adminUserService.getAllAdminUsers());
    }

    @PostMapping
    @PermissionNode(key = "create", description = "Create admin user")
    public ResponseEntity<AdminUser> create(@RequestParam UUID userId, @RequestParam boolean isSuperAdmin) {
        return ResponseEntity.ok(adminUserService.createAdminUser(userId, isSuperAdmin));
    }
}
