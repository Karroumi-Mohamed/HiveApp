package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.dto.AdminUserResponseDto;
import com.hiveapp.platform.admin.dto.AdminAccessOverviewDto;
import com.hiveapp.platform.admin.dto.AssignAdminRoleRequest;
import com.hiveapp.platform.admin.dto.CreateAdminUserRequest;
import com.hiveapp.platform.admin.service.AdminUserService;
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
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final com.hiveapp.platform.admin.service.AdminUserRoleSetService adminUserRoleSetService;
    private final com.hiveapp.shared.email.delivery.EmailDeliveryTracker emailDeliveryTracker;

    @GetMapping("/overview")
    public AdminAccessOverviewDto overview() {
        return adminUserService.getAccessOverview();
    }

    @GetMapping
    public PageResponse<AdminUserResponseDto> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(
                adminUserService.getAdminUsers(search, active, pageRequest(page, size, sort, direction)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponseDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.getAdminUser(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public com.hiveapp.platform.admin.dto.AdminUserCreationResponse create(
            @Valid @RequestBody CreateAdminUserRequest req) {
        return withDelivery(adminUserService.createAdminUser(
                req.firstName(), req.lastName(), req.email(), req.initialAccessMethod(), req.isSuperAdmin()));
    }

    @PostMapping("/bulk/active")
    public com.hiveapp.platform.admin.dto.BulkOperationResult setActiveBulk(
            @Valid @RequestBody com.hiveapp.platform.admin.dto.BulkSetActiveRequest req) {
        return adminUserService.setActiveBulk(req.ids(), req.active());
    }

    @PostMapping("/bulk/roles")
    public com.hiveapp.platform.admin.dto.BulkOperationResult assignRoleBulk(
            @Valid @RequestBody com.hiveapp.platform.admin.dto.BulkAssignRoleRequest req) {
        return adminUserService.assignRoleBulk(req.ids(), req.adminRoleId());
    }

    @PostMapping("/bulk/access/resend")
    public com.hiveapp.platform.admin.dto.BulkOperationResult resendActivationBulk(
            @Valid @RequestBody com.hiveapp.platform.admin.dto.BulkOperatorIdsRequest req) {
        return adminUserService.resendActivationBulk(req.ids());
    }

    @PostMapping("/{id}/toggle-active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void toggleActive(@PathVariable UUID id) {
        adminUserService.toggleActive(id);
    }

    // ── Role assignments ──────────────────────────────────────────────────────

    @PatchMapping("/{id}")
    public AdminUserResponseDto rename(
            @PathVariable UUID id,
            @Valid @RequestBody com.hiveapp.platform.admin.dto.RenameOperatorRequest req) {
        return adminUserService.renameOperator(id, req.firstName(), req.lastName());
    }

    @PatchMapping("/{id}/email")
    public AdminUserResponseDto changeEmail(
            @PathVariable UUID id,
            @Valid @RequestBody com.hiveapp.platform.admin.dto.ChangeOperatorEmailRequest req) {
        return adminUserService.changeOperatorEmail(id, req.email());
    }

    @GetMapping("/{id}/permissions")
    public java.util.List<com.hiveapp.platform.admin.dto.AdminPermissionSummaryDto> getEffectivePermissions(
            @PathVariable UUID id) {
        return adminUserService.getEffectivePermissions(id);
    }

    @PostMapping("/{id}/access/resend")
    public com.hiveapp.platform.admin.dto.AdminOperatorAccessResponse resendActivation(
            @PathVariable UUID id) {
        return withDelivery(adminUserService.resendActivation(id));
    }

    @PostMapping("/{id}/email-verification")
    public com.hiveapp.platform.admin.dto.AdminOperatorAccessResponse sendEmailVerification(
            @PathVariable UUID id) {
        return withDelivery(adminUserService.sendEmailVerification(id));
    }

    @PostMapping("/{id}/access/temporary")
    public com.hiveapp.platform.admin.dto.AdminOperatorAccessResponse generateTemporaryAccess(
            @PathVariable UUID id) {
        return withDelivery(adminUserService.generateTemporaryAccess(id));
    }

    @PostMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRole(@PathVariable UUID id, @Valid @RequestBody AssignAdminRoleRequest req) {
        adminUserService.assignRole(id, req.adminRoleId());
    }

    @PostMapping("/{id}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        adminUserService.assignRole(id, roleId);
    }

    @PutMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceRoles(
            @PathVariable UUID id,
            @Valid @RequestBody com.hiveapp.platform.admin.dto.ReplaceAdminRolesRequest request) {
        adminUserRoleSetService.replaceRoles(id, request.roleIds());
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        adminUserService.removeRole(id, roleId);
    }

    private com.hiveapp.platform.admin.dto.AdminOperatorAccessResponse withDelivery(
            com.hiveapp.platform.admin.dto.AdminOperatorAccessResponse response) {
        return response.emailDeliveryId() == null
                ? response
                : response.withEmailDelivery(
                        emailDeliveryTracker.findSummary(response.emailDeliveryId()).orElse(null));
    }

    private com.hiveapp.platform.admin.dto.AdminUserCreationResponse withDelivery(
            com.hiveapp.platform.admin.dto.AdminUserCreationResponse response) {
        return response.emailDeliveryId() == null
                ? response
                : response.withEmailDelivery(
                        emailDeliveryTracker.findSummary(response.emailDeliveryId()).orElse(null));
    }

    /**
     * Sortable columns are whitelisted. Passing an arbitrary property straight to Spring Data
     * throws PropertyReferenceException deep in the repository, which surfaces as a 500 for what
     * is really a bad request — and it lets a caller probe the entity's field names.
     */
    private static final java.util.Map<String, String> SORTABLE =
            java.util.Map.of(
                    "email", "user.email",
                    "createdAt", "createdAt",
                    "active", "isActive",
                    "superAdmin", "isSuperAdmin");

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
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Unsupported sort column: " + sort);
        }
        Sort.Direction resolved = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(resolved, property));
    }
}
