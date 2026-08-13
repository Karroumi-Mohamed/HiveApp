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
        Instant expiresAt,
        /**
         * Which portal the link belongs to. Operators and members complete their credentials on
         * different pages against different endpoints, so the link cannot be built from the
         * purpose alone.
         */
        CredentialAudience audience
) {
    public enum CredentialAudience {
        CLIENT,
        PLATFORM_OPERATOR
    }
}
