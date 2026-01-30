package com.hiveapp.permission.api;

import com.hiveapp.permission.domain.dto.CreatePermissionRequest;
import com.hiveapp.permission.domain.dto.PermissionResponse;
import com.hiveapp.permission.domain.service.PermissionService;
import com.hiveapp.permission.engine.PermissionResolver;
import com.hiveapp.permission.engine.PermissionSet;
import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final PermissionResolver permissionResolver;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(permissionService.createPermission(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PermissionResponse> getPermissionById(@PathVariable UUID id) {
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }

    @GetMapping("/feature/{featureId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PermissionResponse>> getPermissionsByFeature(@PathVariable UUID featureId) {
        return ResponseEntity.ok(permissionService.getPermissionsByFeatureId(featureId));
    }

    /**
     * Resolve effective permissions for the current member on a specific company.
     * This endpoint executes the full permission formula:
     *   rolePermissions ∩ planCeiling ∩ companyModulePermissions
     *
     * Returns the set of permission codes the member actually has.
     */
    @GetMapping("/effective/company/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Set<String>> getEffectivePermissions(
            @AuthenticationPrincipal HiveAppUserDetails userDetails,
            @PathVariable UUID companyId
    ) {
        PermissionSet effective = permissionResolver.resolveForOwnAccount(
                userDetails.getMemberId(), userDetails.getAccountId(), companyId);
        return ResponseEntity.ok(effective.getCodes());
    }

    /**
     * Resolve effective permissions for the current member on a collaboration.
     * Formula: rolePermissions ∩ collaborationCeiling ∩ companyModulePermissions
     */
    @GetMapping("/effective/collaboration/{collaborationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Set<String>> getEffectiveCollaborationPermissions(
            @AuthenticationPrincipal HiveAppUserDetails userDetails,
            @PathVariable UUID collaborationId
    ) {
        PermissionSet effective = permissionResolver.resolveForCollaboration(
                userDetails.getMemberId(), userDetails.getAccountId(), collaborationId);
        return ResponseEntity.ok(effective.getCodes());
    }
}
