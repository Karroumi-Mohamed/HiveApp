package com.hiveapp.platform.admin.dto;

import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import java.time.Instant;
import java.util.UUID;

public final class PlatformActivityModels {
    private PlatformActivityModels() {}

    public record ActorIdentity(String displayName, String email, boolean active) {}

    public record AccountIdentity(String name, String slug, boolean active) {}

    public record ActorResolution(
            UUID activityId,
            UUID actorUserId,
            ActorIdentity identity
    ) {}

    public record AccountResolution(
            UUID activityId,
            UUID accountId,
            AccountIdentity identity
    ) {}

    public record Activity(
            UUID id,
            Instant occurredAt,
            AuditActorSurface actorSurface,
            UUID actorUserId,
            ActorIdentity actorIdentity,
            UUID clientAccountId,
            UUID targetAccountId,
            AccountIdentity accountIdentity,
            UUID targetCompanyId,
            UUID collaborationId,
            String action,
            String resourceType,
            String resourceId,
            AuditOutcome outcome,
            String requestMethod,
            String requestPath,
            String requestId,
            boolean payloadAvailable
    ) {}

    public record Payload(
            UUID id,
            String requestData,
            String resultData,
            String failureType
    ) {}

    public record ResolutionRequest(java.util.List<UUID> activityIds) {}
}
