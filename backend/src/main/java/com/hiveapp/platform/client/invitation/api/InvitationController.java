package com.hiveapp.platform.client.invitation.api;

import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.platform.client.invitation.dto.AcceptInvitationRequest;
import com.hiveapp.platform.client.invitation.dto.InvitationDto;
import com.hiveapp.platform.client.invitation.dto.InvitationInfoDto;
import com.hiveapp.platform.client.invitation.dto.SendInvitationRequest;
import com.hiveapp.platform.client.invitation.service.InvitationService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    // ── Authenticated ─────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationDto send(@Valid @RequestBody SendInvitationRequest request) {
        var ctx = HiveAppContextHolder.getContext();
        return invitationService.send(ctx.currentAccountId(), ctx.actorUserId(), request);
    }

    @GetMapping
    public List<InvitationDto> listPending() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return invitationService.listPending(accountId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        invitationService.revoke(id, accountId);
    }

    // ── Public (no auth) ──────────────────────────────────────────────────────

    @GetMapping("/validate")
    public InvitationInfoDto validate(@RequestParam String token) {
        return invitationService.validate(token);
    }

    @PostMapping("/accept")
    public AuthResponse accept(@Valid @RequestBody AcceptInvitationRequest request) {
        return invitationService.accept(request);
    }
}
