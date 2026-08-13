package com.hiveapp.platform.admin.dto;

import com.hiveapp.identity.domain.constant.CredentialState;

/**
 * The created operator plus their initial credentials.
 *
 * <p>{@code temporaryPassword} is returned exactly once, at creation, and is never readable
 * again — it is not persisted in clear form. The creating administrator is responsible for
 * handing it to the operator.
 */
public record AdminUserCreationResponse(
        AdminUserResponseDto operator,
        String temporaryPassword,
        CredentialState credentialState
) {}
