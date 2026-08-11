package com.hiveapp.platform.client.role.api;

import com.hiveapp.platform.client.role.dto.CreateRoleRequest;
import com.hiveapp.platform.client.role.dto.RoleDto;
import com.hiveapp.platform.client.role.dto.UpdateRoleRequest;
import com.hiveapp.platform.client.role.dto.DuplicateRoleRequest;
import com.hiveapp.platform.client.role.dto.RoleImpactConfirmationRequest;
import com.hiveapp.platform.client.role.dto.RoleImpactDto;
import com.hiveapp.platform.client.role.domain.constant.RoleChangeType;
import com.hiveapp.platform.client.role.service.RoleService;
import com.hiveapp.platform.registry.dto.picker.PermissionPickerCatalogDto;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public List<RoleDto> getAccountRoles() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return roleService.getAccountRoles(accountId);
    }

    @GetMapping("/permission-catalog")
    public PermissionPickerCatalogDto getPermissionCatalog(
            @RequestParam(required = false) UUID roleId) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return roleService.getPermissionCatalog(accountId, roleId);
    }

    @GetMapping("/company/{companyId}")
    public List<RoleDto> getCompanyRoles(@PathVariable UUID companyId) {
        return roleService.getCompanyRoles(companyId);
    }

    @GetMapping("/{id}")
    public RoleDto getRole(@PathVariable UUID id) {
        return roleService.getRole(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDto createRole(@Valid @RequestBody CreateRoleRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return roleService.createRole(
                accountId, req.templateBoundary(), req.boundaryCompanyId(), req.name(), req.description());
    }

    @PutMapping("/{id}")
    public RoleDto updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest req) {
        return roleService.updateRole(
                id, req.name(), req.description(), req.expectedVersion(), req.confirmedAssignmentCount());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
    }

    @GetMapping("/{id}/impact")
    public RoleImpactDto previewImpact(
            @PathVariable UUID id,
            @RequestParam RoleChangeType changeType,
            @RequestParam(required = false) String permissionCode) {
        return roleService.previewRoleImpact(id, changeType, permissionCode);
    }

    @PostMapping("/{id}/activate")
    public RoleDto activateRole(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RoleImpactConfirmationRequest confirmation) {
        return roleService.activateRole(
                id, expectedVersion(confirmation), confirmedAssignments(confirmation));
    }

    @PostMapping("/{id}/deactivate")
    public RoleDto deactivateRole(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RoleImpactConfirmationRequest confirmation) {
        return roleService.deactivateRole(
                id, expectedVersion(confirmation), confirmedAssignments(confirmation));
    }

    @PostMapping("/{id}/archive")
    public RoleDto archiveRole(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RoleImpactConfirmationRequest confirmation) {
        return roleService.archiveRole(
                id, expectedVersion(confirmation), confirmedAssignments(confirmation));
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDto duplicateRole(
            @PathVariable UUID id,
            @Valid @RequestBody DuplicateRoleRequest request) {
        return roleService.duplicateRole(id, request.name(), request.description());
    }

    // ── Permissions on a role ─────────────────────────────────────────────────

    @PostMapping("/{id}/permissions")
    public RoleDto addPermission(
            @PathVariable UUID id,
            @RequestParam String permissionCode,
            @RequestParam String registryVersion,
            @RequestParam(required = false) Long expectedVersion,
            @RequestParam(required = false) Long confirmedAssignmentCount) {
        return roleService.addPermissionToRole(
                id, permissionCode, registryVersion, expectedVersion, confirmedAssignmentCount);
    }

    @DeleteMapping("/{id}/permissions/{permissionCode}")
    public RoleDto removePermission(
            @PathVariable UUID id,
            @PathVariable String permissionCode,
            @RequestParam(required = false) Long expectedVersion,
            @RequestParam(required = false) Long confirmedAssignmentCount) {
        return roleService.removePermissionFromRole(
                id, permissionCode, expectedVersion, confirmedAssignmentCount);
    }

    private Long expectedVersion(RoleImpactConfirmationRequest confirmation) {
        return confirmation == null ? null : confirmation.expectedVersion();
    }

    private Long confirmedAssignments(RoleImpactConfirmationRequest confirmation) {
        return confirmation == null ? null : confirmation.confirmedAssignmentCount();
    }
}
