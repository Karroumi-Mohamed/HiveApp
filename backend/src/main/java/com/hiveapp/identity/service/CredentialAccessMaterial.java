package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;

import java.time.Instant;

public record CredentialAccessMaterial(
        InitialAccessMethod method,
        CredentialState state,
        String temporaryPassword,
        Instant linkExpiresAt
) {
}
