package com.hiveapp.platform.client.invitation.dto;

import com.hiveapp.platform.client.invitation.domain.constant.InvitationStatus;

import java.time.Instant;
import java.util.UUID;

public record InvitationDto(
        UUID id,
        String email,
        InvitationStatus status,
        Instant expiresAt,
        Instant createdAt,
        String invitedByName,
        UUID roleId,
        String roleName,
        UUID companyId,
        String companyName
) {}
