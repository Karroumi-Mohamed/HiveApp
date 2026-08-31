package com.hiveapp.platform.client.collaboration.api;

import com.hiveapp.platform.client.collaboration.dto.B2BPermissionRequest;
import com.hiveapp.platform.client.collaboration.dto.CollaborationCommandRequest;
import com.hiveapp.platform.client.collaboration.dto.CollaborationDto;
import com.hiveapp.platform.client.collaboration.dto.CollaborationGrantDto;
import com.hiveapp.platform.client.collaboration.dto.CollaborationInitiationResult;
import com.hiveapp.platform.client.collaboration.dto.CompanyShareCodeDto;
import com.hiveapp.platform.client.collaboration.dto.CompanyShareResolutionDto;
import com.hiveapp.platform.client.collaboration.dto.InitiateCollaborationRequest;
import com.hiveapp.platform.client.collaboration.dto.ShareCodeRequest;
import com.hiveapp.platform.client.collaboration.service.CollaborationService;
import com.hiveapp.platform.registry.dto.picker.PermissionPickerCatalogDto;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collaborations")
@RequiredArgsConstructor
public class CollaborationController {

    private final CollaborationService collaborationService;

    @PostMapping("/share-code/resolve")
    public CompanyShareResolutionDto resolveShareCode(@Valid @RequestBody ShareCodeRequest request) {
        return collaborationService.resolveCompanyShareCode(request.shareCode());
    }

    @PostMapping("/companies/{companyId}/share-code")
    public CompanyShareCodeDto regenerateShareCode(@PathVariable UUID companyId) {
        return collaborationService.regenerateCompanyShareCode(currentAccountId(), companyId);
    }

    @GetMapping("/companies/{companyId}/share-code")
    public CompanyShareCodeDto getShareCode(@PathVariable UUID companyId) {
        return collaborationService.getCompanyShareCode(currentAccountId(), companyId);
    }

    @PatchMapping("/companies/{companyId}/share-code")
    public CompanyShareCodeDto setShareCodeEnabled(
            @PathVariable UUID companyId,
            @RequestParam boolean enabled
    ) {
        return collaborationService.setCompanyShareCodeEnabled(currentAccountId(), companyId, enabled);
    }

    @PostMapping("/initiate")
    public ResponseEntity<CollaborationDto> initiate(@Valid @RequestBody InitiateCollaborationRequest request) {
        CollaborationInitiationResult result = collaborationService.initiateCollaboration(
                currentAccountId(), request);
        HttpStatus status = result.outcome()
                == CollaborationInitiationResult.Outcome.EXISTING_IDENTICAL
                ? HttpStatus.OK
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.collaboration());
    }

    @GetMapping("/{id}")
    public CollaborationDto detail(@PathVariable UUID id) {
        return collaborationService.getCollaboration(id);
    }

    @PatchMapping("/{id}/accept")
    public CollaborationDto accept(
            @PathVariable UUID id,
            @Valid @RequestBody CollaborationCommandRequest request
    ) {
        return collaborationService.acceptCollaboration(currentAccountId(), id, request);
    }

    @PatchMapping("/{id}/reject")
    public CollaborationDto reject(
            @PathVariable UUID id,
            @Valid @RequestBody CollaborationCommandRequest request
    ) {
        return collaborationService.rejectCollaboration(currentAccountId(), id, request);
    }

    @PatchMapping("/{id}/cancel-request")
    public CollaborationDto cancelRequest(
            @PathVariable UUID id,
            @Valid @RequestBody CollaborationCommandRequest request
    ) {
        return collaborationService.cancelRequest(currentAccountId(), id, request);
    }

    @PatchMapping("/{id}/suspend")
    public CollaborationDto suspend(
            @PathVariable UUID id,
            @Valid @RequestBody CollaborationCommandRequest request
    ) {
        return collaborationService.suspendCollaboration(currentAccountId(), id, request);
    }

    @PatchMapping("/{id}/resume")
    public CollaborationDto resume(
            @PathVariable UUID id,
            @Valid @RequestBody CollaborationCommandRequest request
    ) {
        return collaborationService.resumeCollaboration(currentAccountId(), id, request);
    }

    @DeleteMapping("/{id}")
    public CollaborationDto revoke(
            @PathVariable UUID id,
            @Valid @RequestBody CollaborationCommandRequest request
    ) {
        return collaborationService.revokeCollaboration(currentAccountId(), id, request);
    }

    @GetMapping
    public List<CollaborationDto> getCollaborations() {
        return collaborationService.getClientCollaborations(currentAccountId());
    }

    @GetMapping("/incoming")
    public List<CollaborationDto> getIncomingCollaborations() {
        return collaborationService.getProviderCollaborations(currentAccountId());
    }

    @PostMapping("/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantPermission(@PathVariable UUID id, @Valid @RequestBody B2BPermissionRequest request) {
        collaborationService.grantPermission(
                currentAccountId(), id, request.permissionCode(), request.registryVersion());
    }

    @GetMapping("/{id}/permissions")
    public List<CollaborationGrantDto> permissions(@PathVariable UUID id) {
        return collaborationService.getPermissions(id);
    }

    @GetMapping("/{id}/permission-catalog")
    public PermissionPickerCatalogDto getPermissionCatalog(@PathVariable UUID id) {
        return collaborationService.getPermissionCatalog(currentAccountId(), id);
    }

    @DeleteMapping("/{id}/permissions/{permissionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePermission(@PathVariable UUID id, @PathVariable String permissionCode) {
        collaborationService.revokePermission(currentAccountId(), id, permissionCode);
    }

    private UUID currentAccountId() {
        return HiveAppContextHolder.getContext().currentAccountId();
    }
}
