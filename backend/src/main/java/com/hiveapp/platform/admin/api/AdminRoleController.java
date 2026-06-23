package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import com.hiveapp.platform.admin.domain.repository.AdminRolePermissionRepository;
import com.hiveapp.platform.admin.dto.AdminPermissionSummaryDto;
import com.hiveapp.platform.admin.dto.AdminRoleResponseDto;
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
    private final AdminRolePermissionRepository adminRolePermissionRepository;

    @GetMapping
    public ResponseEntity<List<AdminRoleResponseDto>> getAll() {
        return ResponseEntity.ok(adminRoleService.getAllAdminRoles().stream()
                .map(this::toDto)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminRoleResponseDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toDto(adminRoleService.getAdminRole(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminRoleResponseDto create(@Valid @RequestBody CreateAdminRoleRequest req) {
        return toDto(adminRoleService.createAdminRole(req.name(), req.description()));
    }

    @PutMapping("/{id}")
    public AdminRoleResponseDto update(@PathVariable UUID id, @Valid @RequestBody UpdateAdminRoleRequest req) {
        return toDto(adminRoleService.updateAdminRole(id, req.name(), req.description()));
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
        adminRoleService.grantPermission(id, req.permissionId());
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePermission(@PathVariable UUID id, @PathVariable UUID permissionId) {
        adminRoleService.revokePermission(id, permissionId);
    }

    private AdminRoleResponseDto toDto(AdminRole role) {
        return new AdminRoleResponseDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isActive(),
                adminRolePermissionRepository.findAllByAdminRoleId(role.getId()).stream()
                        .map(this::toPermissionSummary)
                        .toList()
        );
    }

    private AdminPermissionSummaryDto toPermissionSummary(AdminRolePermission grant) {
        var permission = grant.getPermission();
        return new AdminPermissionSummaryDto(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getAction(),
                permission.getResource()
        );
    }
}
