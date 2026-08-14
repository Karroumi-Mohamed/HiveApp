package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;
import com.hiveapp.platform.admin.dto.AdminPermissionSummaryDto;
import com.hiveapp.platform.admin.dto.AdminRoleImpactDto;
import com.hiveapp.platform.admin.dto.AdminRoleImpactRequest;
import com.hiveapp.platform.admin.dto.AdminRolePermissionSetRequest;
import com.hiveapp.platform.admin.dto.AdminRoleResponseDto;
import com.hiveapp.platform.admin.dto.AdminRoleStatusRequest;
import com.hiveapp.platform.admin.dto.CreateAdminRoleRequest;
import com.hiveapp.platform.admin.dto.CreateAdminRoleFromPresetRequest;
import com.hiveapp.platform.admin.dto.DuplicateAdminRoleRequest;
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
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) AdminRoleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(
                adminRoleService.getAdminRoles(
                        search,
                        status != null ? status : activeStatus(active),
                        pageRequest(page, size, sort, direction)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminRoleResponseDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminRoleService.getAdminRole(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminRoleResponseDto create(@Valid @RequestBody CreateAdminRoleRequest req) {
        return adminRoleService.createAdminRole(req.name(), req.description(), req.permissionIds());
    }

    @PostMapping("/from-preset")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminRoleResponseDto createFromPreset(
            @Valid @RequestBody CreateAdminRoleFromPresetRequest req) {
        return adminRoleService.createAdminRoleFromPreset(
                req.presetCode(), req.name(), req.description(), req.permissionIds());
    }

    @PutMapping("/{id}")
    public AdminRoleResponseDto update(@PathVariable UUID id, @Valid @RequestBody UpdateAdminRoleRequest req) {
        return adminRoleService.updateAdminRole(id, req.name(), req.description(), req.expectedVersion());
    }

    @PatchMapping("/{id}/metadata")
    public AdminRoleResponseDto updateMetadata(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAdminRoleRequest req) {
        return adminRoleService.updateAdminRole(id, req.name(), req.description(), req.expectedVersion());
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminRoleResponseDto duplicate(
            @PathVariable UUID id,
            @Valid @RequestBody DuplicateAdminRoleRequest req) {
        return adminRoleService.duplicateAdminRole(id, req.name(), req.description());
    }

    @GetMapping("/grantable-permissions")
    public java.util.List<AdminPermissionSummaryDto> getGrantablePermissions() {
        return adminRoleService.getGrantablePermissions();
    }

    @PostMapping("/{id}/impact-preview")
    public AdminRoleImpactDto previewImpact(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminRoleImpactRequest request) {
        return adminRoleService.previewImpact(
                id,
                request == null ? null : request.permissionIds(),
                request == null ? null : request.status());
    }

    @PutMapping("/{id}/permissions")
    public AdminRoleResponseDto replacePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRolePermissionSetRequest request) {
        return adminRoleService.replacePermissions(
                id,
                request.permissionIds(),
                request.expectedVersion(),
                request.confirmedAssignmentCount());
    }

    @PostMapping("/{id}/status")
    public AdminRoleResponseDto transitionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRoleStatusRequest request) {
        return adminRoleService.transitionStatus(
                id,
                request.status(),
                request.expectedVersion(),
                request.confirmedAssignmentCount());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        adminRoleService.deleteRole(id);
    }

    @GetMapping("/{id}/operators")
    public java.util.List<com.hiveapp.platform.admin.dto.RoleHolderDto> getRoleHolders(@PathVariable UUID id) {
        return adminRoleService.getRoleHolders(id);
    }

    @GetMapping("/{id}/history")
    public java.util.List<com.hiveapp.platform.admin.dto.AdminRoleHistoryEntryDto> getRoleHistory(
            @PathVariable UUID id) {
        return adminRoleService.getRoleHistory(id);
    }

    @PostMapping("/bulk/active")
    public com.hiveapp.platform.admin.dto.BulkOperationResult setActiveBulk(
            @Valid @RequestBody com.hiveapp.platform.admin.dto.BulkSetActiveRequest req) {
        return adminRoleService.setActiveBulk(req.ids(), req.active());
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

    /** Whitelisted so an arbitrary property cannot 500 the request or probe entity fields. */
    private static final java.util.Map<String, String> SORTABLE =
            java.util.Map.of(
                    "name", "name",
                    "createdAt", "createdAt",
                    "updatedAt", "updatedAt",
                    "active", "status",
                    "status", "status",
                    "assignedOperatorCount", "assignedOperatorCount");

    private AdminRoleStatus activeStatus(Boolean active) {
        if (active == null) {
            return null;
        }
        return active ? AdminRoleStatus.ACTIVE : AdminRoleStatus.INACTIVE;
    }

    private PageRequest pageRequest(int page, int size, String sort, String direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100");
        }
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        String property = SORTABLE.get(sort);
        if (property == null) {
            throw new com.hiveapp.shared.exception.InvalidRequestException("Unsupported sort column: " + sort);
        }
        Sort.Direction resolved = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(resolved, property));
    }
}
