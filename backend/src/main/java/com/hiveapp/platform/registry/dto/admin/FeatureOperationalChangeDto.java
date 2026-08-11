package com.hiveapp.platform.registry.dto.admin;

import com.hiveapp.platform.registry.domain.constant.FeatureOperationalControl;

import java.time.Instant;
import java.util.UUID;

public record FeatureOperationalChangeDto(
        UUID id,
        String featureCode,
        FeatureOperationalControl control,
        UUID actorUserId,
        boolean previousValue,
        boolean newValue,
        String reason,
        boolean impactConfirmed,
        boolean communicationConfirmed,
        String effectiveTiming,
        Instant createdAt
) {
}
