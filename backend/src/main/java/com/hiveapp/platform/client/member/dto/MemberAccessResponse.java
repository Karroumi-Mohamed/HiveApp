package com.hiveapp.platform.client.member.dto;

import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;

import java.time.Instant;
import java.util.UUID;

public record MemberAccessResponse(
        UUID memberId,
        InitialAccessMethod method,
        CredentialState credentialState,
        String temporaryPassword,
        Instant linkExpiresAt
) {
}
