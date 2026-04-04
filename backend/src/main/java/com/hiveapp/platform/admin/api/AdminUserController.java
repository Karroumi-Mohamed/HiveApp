package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<AdminUser> create(@RequestParam UUID userId, @RequestParam boolean isSuperAdmin) {
        return ResponseEntity.ok(adminUserService.createAdminUser(userId, isSuperAdmin));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        adminUserService.deactivateAdminUser(id);
        return ResponseEntity.noContent().build();
    }
}
