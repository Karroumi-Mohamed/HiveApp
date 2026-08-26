package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.audit.domain.AuditOutcome;

import java.time.Instant;
import java.util.UUID;

/** Safe, purpose-built price history projection; raw audit request/result payloads stay internal. */
public record ProductPriceHistoryEntryDto(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String actorEmail,
        String action,
        AuditOutcome outcome,
        String failureType,
        String reason
) {}
