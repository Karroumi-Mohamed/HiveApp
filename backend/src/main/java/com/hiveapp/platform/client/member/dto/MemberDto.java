package com.hiveapp.platform.client.member.dto;

import java.util.UUID;

public record MemberDto(
    UUID id, 
    UUID userId, 
    String username,
    String email,
    String displayName, 
    String employeeNumber,
    boolean isOwner, 
    boolean isActive,
    com.hiveapp.identity.domain.constant.CredentialState credentialState,
    boolean emailVerified,
    boolean initialAccessLocked
) {}
