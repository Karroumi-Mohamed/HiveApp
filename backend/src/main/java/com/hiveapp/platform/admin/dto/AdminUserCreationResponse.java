package com.hiveapp.platform.admin.dto;

import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.identity.service.CredentialAccessMaterial;

import java.time.Instant;

/**
 * The created operator plus their initial credentials.
 *
 * <p>The chosen method is explicit. Email activation returns its deadline; temporary access
 * returns the password exactly once and never verifies the operator's email address.
 */
public record AdminUserCreationResponse(
        AdminUserResponseDto operator,
        InitialAccessMethod initialAccessMethod,
        String temporaryPassword,
        Instant linkExpiresAt,
        CredentialState credentialState
) {
    public static AdminUserCreationResponse of(
            AdminUserResponseDto operator,
            CredentialAccessMaterial material
    ) {
        return new AdminUserCreationResponse(
                operator,
                material.method(),
                material.temporaryPassword(),
                material.linkExpiresAt(),
                material.state());
    }
}
