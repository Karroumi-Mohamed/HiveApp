package com.hiveapp.platform.client.invitation.dto;

import java.time.Instant;

/**
 * Public response for GET /api/v1/invitations/validate?token=...
 * Shown to the invitee before they fill in name/password.
 * Never exposes internal IDs or sensitive data.
 */
public record InvitationInfoDto(
        String email,
        String workspaceName,
        String inviterName,
        Instant expiresAt,
        boolean requiresRegistration  // true = new user, false = existing user just needs to accept
) {}
