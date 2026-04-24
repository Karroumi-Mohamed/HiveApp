package com.hiveapp.platform.client.role.api;

import com.hiveapp.platform.client.role.dto.CreateRoleRequest;
import com.hiveapp.platform.client.role.dto.RoleDto;
import com.hiveapp.platform.client.role.dto.UpdateRoleRequest;
import com.hiveapp.platform.client.role.mapper.RoleMapper;
import com.hiveapp.platform.client.role.service.RoleService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final RoleMapper roleMapper;

    @GetMapping
    public List<RoleDto> getAccountRoles() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return roleService.getAccountRoles(accountId).stream()
                .map(roleMapper::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public RoleDto getRole(@PathVariable UUID id) {
        return roleMapper.toDto(roleService.getRole(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDto createRole(@Valid @RequestBody CreateRoleRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return roleMapper.toDto(roleService.createRole(accountId, req.companyId(), req.name(), req.description()));
    }

    @PutMapping("/{id}")
    public RoleDto updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest req) {
        return roleMapper.toDto(roleService.updateRole(id, req.name(), req.description()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
    }

    // ── Permissions on a role ─────────────────────────────────────────────────

    @PostMapping("/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addPermission(@PathVariable UUID id, @RequestParam String permissionCode) {
        roleService.addPermissionToRole(id, permissionCode);
    }

    @DeleteMapping("/{id}/permissions/{permissionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePermission(@PathVariable UUID id, @PathVariable String permissionCode) {
        roleService.removePermissionFromRole(id, permissionCode);
    }
}
