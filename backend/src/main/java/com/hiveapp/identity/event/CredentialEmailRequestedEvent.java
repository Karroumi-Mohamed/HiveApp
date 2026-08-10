package com.hiveapp.identity.event;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;

import java.time.Instant;
import java.util.UUID;

public record CredentialEmailRequestedEvent(
        UUID deliveryId,
        String email,
        String memberName,
        String workspaceName,
        String rawToken,
        CredentialTokenPurpose purpose,
        Instant expiresAt
) {
}
