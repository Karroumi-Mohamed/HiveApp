package com.hiveapp.platform.client.collaboration.api;

import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.service.CollaborationService;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collaborations")
@RequiredArgsConstructor
@PermissionNode(key = "b2b", description = "B2B Collaboration Management")
public class CollaborationController {

    private final CollaborationService collaborationService;

    @PostMapping("/initiate")
    @PermissionNode(key = "request", description = "Request collaboration with provider")
    public ResponseEntity<Collaboration> initiate(
            @RequestParam UUID clientAccountId,
            @RequestParam UUID providerAccountId,
            @RequestParam UUID companyId) {
        return ResponseEntity
                .ok(collaborationService.initiateCollaboration(clientAccountId, providerAccountId, companyId));
    }

    @PatchMapping("/{id}/accept")
    @PermissionNode(key = "accept", description = "Accept incoming collaboration")
    public ResponseEntity<Void> accept(@PathVariable UUID id) {
        collaborationService.acceptCollaboration(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/account/{accountId}")
    @PermissionNode(key = "view", description = "View my collaborations")
    public ResponseEntity<List<Collaboration>> getCollaborations(@PathVariable UUID accountId) {
        return ResponseEntity.ok(collaborationService.getClientCollaborations(accountId));
    }
}
