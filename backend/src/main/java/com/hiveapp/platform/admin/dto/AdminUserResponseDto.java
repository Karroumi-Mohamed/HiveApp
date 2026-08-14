package com.hiveapp.platform.admin.dto;

import java.util.List;
import java.util.UUID;

public record AdminUserResponseDto(
    UUID id,
    UUID userId,
    String email,
    String firstName,
    String lastName,
    boolean emailVerified,
    boolean isSuperAdmin,
    boolean isActive,
    /** Whether the operator has activated their access yet, so the UI can offer a resend. */
    com.hiveapp.identity.domain.constant.CredentialState credentialState,
    List<AdminRoleSummaryDto> roles
) {}
