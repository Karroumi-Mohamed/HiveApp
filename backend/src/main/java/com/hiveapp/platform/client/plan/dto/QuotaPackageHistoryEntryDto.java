package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.shared.audit.domain.AuditOutcome;

import java.time.Instant;
import java.util.UUID;

public record QuotaPackageHistoryEntryDto(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String actorEmail,
        String action,
        AuditOutcome outcome,
        String failureType,
        String reason,
        UUID resourceId,
        Integer revisionNumber,
        QuotaPackageLifecycleAction lifecycleAction,
        QuotaPackageStatus resultingStatus,
        UUID successorId,
        Integer successorRevisionNumber
) {}
