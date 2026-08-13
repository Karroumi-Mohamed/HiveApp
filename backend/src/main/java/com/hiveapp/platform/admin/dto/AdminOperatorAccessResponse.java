package com.hiveapp.platform.admin.dto;

import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;

import java.time.Instant;

/**
 * The result of reissuing an operator's access.
 *
 * <p>{@code temporaryPassword} is populated only by the explicit temporary-access fallback, and
 * only on the one response that creates it — nothing stores it in clear form, so it cannot be
 * shown again. The activation-email path leaves it null and carries {@code linkExpiresAt}
 * instead.
 */
public record AdminOperatorAccessResponse(
        InitialAccessMethod method,
        CredentialState credentialState,
        String temporaryPassword,
        Instant linkExpiresAt
) {
    public static AdminOperatorAccessResponse of(
            com.hiveapp.identity.service.CredentialAccessMaterial material) {
        return new AdminOperatorAccessResponse(
                material.method(),
                material.state(),
                material.temporaryPassword(),
                material.linkExpiresAt());
    }
}
