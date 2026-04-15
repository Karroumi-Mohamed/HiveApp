package com.hiveapp.platform.client.role.api;

import com.hiveapp.platform.client.role.dto.RoleDto;
import com.hiveapp.platform.client.role.dto.CreateRoleRequest;
import com.hiveapp.platform.client.role.mapper.RoleMapper;
import com.hiveapp.platform.client.role.service.RoleService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PermissionNode(key = "rbac", description = "Role Management")
public class RoleController {

    private final RoleService roleService;
    private final RoleMapper roleMapper;

    @GetMapping
    @PermissionNode(key = "view", description = "View account roles")
    public List<RoleDto> getAccountRoles() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return roleService.getAccountRoles(accountId).stream()
                .map(roleMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PermissionNode(key = "create", description = "Create custom company role")
    public RoleDto createRole(@Valid @RequestBody CreateRoleRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var role = roleService.createRole(accountId, req.companyId(), req.name(), req.description());
        return roleMapper.toDto(role);
    }

    @PostMapping("/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "grant", description = "Add brick to role")
    public void addPermission(
        @PathVariable UUID id, 
        @RequestParam String permissionCode
    ) {
        roleService.addPermissionToRole(id, permissionCode);
    }
}
