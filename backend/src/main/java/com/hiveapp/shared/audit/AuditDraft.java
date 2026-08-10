package com.hiveapp.shared.audit;

import com.hiveapp.shared.audit.domain.AuditActorSurface;

import java.util.UUID;

record AuditDraft(
        AuditActorSurface actorSurface,
        UUID actorUserId,
        UUID clientAccountId,
        UUID targetAccountId,
        UUID targetCompanyId,
        UUID collaborationId,
        String action,
        String resourceType,
        String resourceId,
        String requestMethod,
        String requestPath,
        String requestData
) {
}
