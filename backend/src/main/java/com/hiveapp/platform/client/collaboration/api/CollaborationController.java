package com.hiveapp.platform.client.collaboration.api;

import com.hiveapp.platform.client.collaboration.dto.CollaborationDto;
import com.hiveapp.platform.client.collaboration.dto.InitiateCollaborationRequest;
import com.hiveapp.platform.client.collaboration.dto.B2BPermissionRequest;
import com.hiveapp.platform.client.collaboration.mapper.CollaborationMapper;
import com.hiveapp.platform.client.collaboration.service.CollaborationService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.exception.UnauthorizedException;

import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/collaborations")
@RequiredArgsConstructor
@PermissionNode(key = "b2b", description = "B2B Collaboration Management")
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final CollaborationMapper collaborationMapper;

    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    @PermissionNode(key = "request", description = "Request collaboration with provider")
    public CollaborationDto initiate(@Valid @RequestBody InitiateCollaborationRequest req) {
        UUID clientAccountId = HiveAppContextHolder.getContext().currentAccountId();
        var collab = collaborationService.initiateCollaboration(clientAccountId, req.companyId());
        return collaborationMapper.toDto(collab);
    }

    @PatchMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "accept", description = "Accept incoming collaboration")
    public void accept(@PathVariable UUID id) {
        UUID providerAccountId = HiveAppContextHolder.getContext().currentAccountId();
        var collab = collaborationService.getCollaboration(id);
        
        if (!collab.getProviderAccount().getId().equals(providerAccountId)) {
            throw new UnauthorizedException("Not authorized to accept this collaboration request");
        }
        
        collaborationService.acceptCollaboration(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "revoke", description = "Revoke collaboration")
    public void revoke(@PathVariable UUID id) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var collab = collaborationService.getCollaboration(id);
        
        if (!collab.getProviderAccount().getId().equals(accountId) && !collab.getClientAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Not authorized to revoke this collaboration");
        }
        
        collaborationService.revokeCollaboration(id);
    }

    @GetMapping
    @PermissionNode(key = "view", description = "View my collaborations")
    public List<CollaborationDto> getCollaborations() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return collaborationService.getClientCollaborations(accountId).stream()
                .map(collaborationMapper::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/incoming")
    @PermissionNode(key = "view_incoming", description = "View incoming B2B requests")
    public List<CollaborationDto> getIncomingCollaborations() {
        UUID providerAccountId = HiveAppContextHolder.getContext().currentAccountId();
        return collaborationService.getProviderCollaborations(providerAccountId).stream()
                .map(collaborationMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "grant_permission", description = "Grant permissions to this B2B client")
    public void grantPermission(@PathVariable UUID id, @Valid @RequestBody B2BPermissionRequest req) {
        UUID providerAccountId = HiveAppContextHolder.getContext().currentAccountId();
        var collab = collaborationService.getCollaboration(id);
        
        if (!collab.getProviderAccount().getId().equals(providerAccountId)) {
            throw new UnauthorizedException("Only the provider can grant B2B permissions");
        }
        
        collaborationService.grantPermission(id, req.permissionCode());
    }

    @DeleteMapping("/{id}/permissions/{permissionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "revoke_permission", description = "Revoke permissions from this B2B client")
    public void revokePermission(@PathVariable UUID id, @PathVariable String permissionCode) {
        UUID providerAccountId = HiveAppContextHolder.getContext().currentAccountId();
        var collab = collaborationService.getCollaboration(id);
        
        if (!collab.getProviderAccount().getId().equals(providerAccountId)) {
            throw new UnauthorizedException("Only the provider can revoke B2B permissions");
        }
        
        collaborationService.revokePermission(id, permissionCode);
    }
}
