package com.hiveapp.platform.admin.dto;

import com.hiveapp.shared.audit.domain.AuditOutcome;

import java.time.Instant;
import java.util.UUID;

public record AdminRoleHistoryEntryDto(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String actorEmail,
        String action,
        /**
         * Who the event happened to — the assigned or removed operator's email, or their raw id
         * if the operator can no longer be resolved. Null for events without a subject.
         */
        String subject,
        AuditOutcome outcome,
        String failureType
) {}
