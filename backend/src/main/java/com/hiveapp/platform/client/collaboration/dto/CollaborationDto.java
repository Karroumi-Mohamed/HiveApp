package com.hiveapp.platform.client.collaboration.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;

public record CollaborationDto(
        UUID id,
        UUID clientAccountId,
        String clientAccountName,
        UUID providerAccountId,
        String providerAccountName,
        UUID companyId,
        String companyName,
        String companyCountry,
        CollaborationStatus status,
        long version,
        String purpose,
        Set<String> requestedPermissionCodes,
        List<CollaborationGrantDto> grants,
        List<String> allowedNextActions,
        List<String> accessBlockers,
        Instant requestedAt,
        Instant acceptedAt,
        Instant cancelledAt,
        Instant rejectedAt,
        Instant suspendedAt,
        Instant suspensionReviewAt,
        Instant automaticResumeAt,
        Instant resumedAt,
        Instant revokedAt,
        String lifecycleReason
) {}
