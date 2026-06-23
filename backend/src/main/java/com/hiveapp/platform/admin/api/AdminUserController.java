package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.entity.AdminUserRole;
import com.hiveapp.platform.admin.dto.AdminUserResponseDto;
import com.hiveapp.platform.admin.dto.AdminRoleSummaryDto;
import com.hiveapp.platform.admin.dto.AssignAdminRoleRequest;
import com.hiveapp.platform.admin.dto.CreateAdminUserRequest;
import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
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
    private final AdminUserRoleRepository adminUserRoleRepository;

    @GetMapping
    public ResponseEntity<List<AdminUserResponseDto>> getAll() {
        var dtos = adminUserService.getAllAdminUsers().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponseDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toDto(adminUserService.getAdminUser(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponseDto create(@Valid @RequestBody CreateAdminUserRequest req) {
        return toDto(adminUserService.createAdminUser(req.userId(), req.isSuperAdmin()));
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

    private AdminUserResponseDto toDto(AdminUser u) {
        return new AdminUserResponseDto(
                u.getId(),
                u.getUser().getId(),
                u.getUser().getEmail(),
                u.isSuperAdmin(),
                u.isActive(),
                adminUserRoleRepository.findAllByAdminUserId(u.getId()).stream()
                        .map(this::toRoleSummary)
                        .toList()
        );
    }

    private AdminRoleSummaryDto toRoleSummary(AdminUserRole assignment) {
        var role = assignment.getAdminRole();
        return new AdminRoleSummaryDto(role.getId(), role.getName(), role.getDescription(), role.isActive());
    }
}
