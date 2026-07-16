package com.hiveapp.identity.event;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;

import java.time.Instant;

public record CredentialEmailRequestedEvent(
        String email,
        String memberName,
        String workspaceName,
        String rawToken,
        CredentialTokenPurpose purpose,
        Instant expiresAt
) {
}
