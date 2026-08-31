package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.PlatformActivityModels;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformActivityService {
    Page<PlatformActivityModels.Activity> search(Query query, Pageable pageable);

    PlatformActivityModels.Activity detail(UUID id);

    PlatformActivityModels.Payload payload(UUID id);

    List<PlatformActivityModels.ActorResolution> actorIdentities(List<UUID> activityIds);

    List<PlatformActivityModels.AccountResolution> accountIdentities(List<UUID> activityIds);

    record Query(
            Instant from,
            Instant until,
            AuditOutcome outcome,
            AuditActorSurface actorSurface,
            String actionPrefix,
            String resourceType,
            String resourceId,
            UUID actorUserId,
            UUID targetAccountId
    ) {}
}
