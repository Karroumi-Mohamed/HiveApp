package com.hiveapp.platform.client.collaboration.api;

import com.hiveapp.platform.client.collaboration.dto.CollaborationDto;
import com.hiveapp.platform.client.collaboration.dto.InitiateCollaborationRequest;
import com.hiveapp.platform.client.collaboration.dto.B2BPermissionRequest;
import com.hiveapp.platform.client.collaboration.mapper.CollaborationMapper;
import com.hiveapp.platform.client.collaboration.service.CollaborationService;
import com.hiveapp.platform.registry.dto.PermissionPickerModuleDto;
import com.hiveapp.shared.security.context.HiveAppContextHolder;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/collaborations")
@RequiredArgsConstructor
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final CollaborationMapper collaborationMapper;

    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    public CollaborationDto initiate(@Valid @RequestBody InitiateCollaborationRequest req) {
        UUID clientAccountId = HiveAppContextHolder.getContext().currentAccountId();
        var collab = collaborationService.initiateCollaboration(clientAccountId, req.companyId());
        return collaborationMapper.toDto(collab);
    }

    @PatchMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable UUID id) {
        UUID providerAccountId = HiveAppContextHolder.getContext().currentAccountId();
        collaborationService.acceptCollaboration(providerAccountId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        collaborationService.revokeCollaboration(accountId, id);
    }

    @GetMapping
    public List<CollaborationDto> getCollaborations() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return collaborationService.getClientCollaborations(accountId).stream()
                .map(collaborationMapper::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/incoming")
    public List<CollaborationDto> getIncomingCollaborations() {
        UUID providerAccountId = HiveAppContextHolder.getContext().currentAccountId();
        return collaborationService.getProviderCollaborations(providerAccountId).stream()
                .map(collaborationMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantPermission(@PathVariable UUID id, @Valid @RequestBody B2BPermissionRequest req) {
        UUID providerAccountId = HiveAppContextHolder.getContext().currentAccountId();
        collaborationService.grantPermission(providerAccountId, id, req.permissionCode());
    }

    @GetMapping("/{id}/permission-catalog")
    public List<PermissionPickerModuleDto> getPermissionCatalog(@PathVariable UUID id) {
        UUID providerAccountId = HiveAppContextHolder.getContext().currentAccountId();
        return collaborationService.getPermissionCatalog(providerAccountId, id);
    }

    @DeleteMapping("/{id}/permissions/{permissionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePermission(@PathVariable UUID id, @PathVariable String permissionCode) {
        UUID providerAccountId = HiveAppContextHolder.getContext().currentAccountId();
        collaborationService.revokePermission(providerAccountId, id, permissionCode);
    }
}
