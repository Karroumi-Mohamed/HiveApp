package com.hiveapp.platform.client.role.api;

import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.service.RoleService;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PermissionNode(key = "rbac", description = "Role Management")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/account/{accountId}")
    @PermissionNode(key = "view", description = "View account roles")
    public ResponseEntity<List<Role>> getAccountRoles(@PathVariable UUID accountId) {
        return ResponseEntity.ok(roleService.getAccountRoles(accountId));
    }

    @PostMapping
    @PermissionNode(key = "create", description = "Create custom company role")
    public ResponseEntity<Role> createRole(
        @RequestParam UUID accountId,
        @RequestParam(required = false) UUID companyId,
        @RequestParam String name,
        @RequestParam String description
    ) {
        return ResponseEntity.ok(roleService.createRole(accountId, companyId, name, description));
    }

    @PostMapping("/{id}/permissions")
    @PermissionNode(key = "grant", description = "Add brick to role")
    public ResponseEntity<Void> addPermission(@PathVariable UUID id, @RequestParam String permissionCode) {
        roleService.addPermissionToRole(id, permissionCode);
        return ResponseEntity.noContent().build();
    }
}
