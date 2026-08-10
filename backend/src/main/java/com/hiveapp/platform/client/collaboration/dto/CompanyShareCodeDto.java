package com.hiveapp.platform.client.collaboration.dto;

import java.time.Instant;
import java.util.UUID;

public record CompanyShareCodeDto(
        UUID companyId,
        boolean enabled,
        String shareCode,
        Instant generatedAt,
        long resolutionCount,
        long requestCount,
        Instant lastResolvedAt,
        Instant lastRequestedAt
) {}
