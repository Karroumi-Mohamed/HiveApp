package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.dto.AdminRoleResponseDto;
import com.hiveapp.platform.admin.dto.CreateAdminRoleRequest;
import com.hiveapp.platform.admin.dto.GrantAdminPermissionRequest;
import com.hiveapp.platform.admin.dto.UpdateAdminRoleRequest;
import com.hiveapp.platform.admin.service.AdminRoleService;
import com.hiveapp.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    @GetMapping
    public PageResponse<AdminRoleResponseDto> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(adminRoleService.getAdminRoles(pageRequest(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminRoleResponseDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminRoleService.getAdminRole(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminRoleResponseDto create(@Valid @RequestBody CreateAdminRoleRequest req) {
        return adminRoleService.createAdminRole(req.name(), req.description());
    }

    @PutMapping("/{id}")
    public AdminRoleResponseDto update(@PathVariable UUID id, @Valid @RequestBody UpdateAdminRoleRequest req) {
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
        adminRoleService.grantPermission(id, req.permissionId());
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePermission(@PathVariable UUID id, @PathVariable UUID permissionId) {
        adminRoleService.revokePermission(id, permissionId);
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
