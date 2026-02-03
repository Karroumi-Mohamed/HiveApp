package com.hiveapp.collaboration.api;

import com.hiveapp.collaboration.domain.dto.CollaborationResponse;
import com.hiveapp.collaboration.domain.dto.CreateCollaborationRequest;
import com.hiveapp.collaboration.domain.dto.UpdateCollaborationPermissionsRequest;
import com.hiveapp.collaboration.domain.service.CollaborationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/collaborations")
@RequiredArgsConstructor
public class CollaborationController {

    private final CollaborationService collaborationService;

    @PostMapping("/{clientAccountId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CollaborationResponse> createCollaboration(
            @PathVariable UUID clientAccountId,
            @Valid @RequestBody CreateCollaborationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collaborationService.createCollaboration(clientAccountId, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CollaborationResponse> getCollaborationById(@PathVariable UUID id) {
        return ResponseEntity.ok(collaborationService.getCollaborationById(id));
    }

    @GetMapping("/client/{clientAccountId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CollaborationResponse>> getAsClient(@PathVariable UUID clientAccountId) {
        return ResponseEntity.ok(collaborationService.getCollaborationsAsClient(clientAccountId));
    }

    @GetMapping("/provider/{providerAccountId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CollaborationResponse>> getAsProvider(@PathVariable UUID providerAccountId) {
        return ResponseEntity.ok(collaborationService.getCollaborationsAsProvider(providerAccountId));
    }

    @PatchMapping("/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CollaborationResponse> acceptCollaboration(@PathVariable UUID id) {
        return ResponseEntity.ok(collaborationService.acceptCollaboration(id));
    }

    @PatchMapping("/{id}/revoke")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revokeCollaboration(@PathVariable UUID id) {
        collaborationService.revokeCollaboration(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/suspend")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> suspendCollaboration(@PathVariable UUID id) {
        collaborationService.suspendCollaboration(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CollaborationResponse> updatePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCollaborationPermissionsRequest request
    ) {
        return ResponseEntity.ok(collaborationService.updatePermissions(id, request));
    }
}
