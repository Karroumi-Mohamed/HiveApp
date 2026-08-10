package com.hiveapp.platform.client.collaboration.dto;

import java.time.Instant;

public record CollaborationGrantDto(
        String permissionCode,
        String description,
        boolean configured,
        boolean currentlyActive,
        Instant grantedAt,
        Instant revokedAt
) {}
