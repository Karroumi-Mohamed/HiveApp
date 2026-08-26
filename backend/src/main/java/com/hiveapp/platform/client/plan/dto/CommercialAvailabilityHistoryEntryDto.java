package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.audit.domain.AuditOutcome;

import java.time.Instant;
import java.util.UUID;

/** Safe projection; raw audit payloads remain internal. */
public record CommercialAvailabilityHistoryEntryDto(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String actorEmail,
        String action,
        AuditOutcome outcome,
        String failureType,
        String reason
) {}
