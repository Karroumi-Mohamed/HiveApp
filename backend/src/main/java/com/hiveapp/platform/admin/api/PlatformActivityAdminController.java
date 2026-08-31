package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.dto.PlatformActivityModels;
import com.hiveapp.platform.admin.service.PlatformActivityService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import com.hiveapp.shared.exception.InvalidRequestException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/activities")
@RequiredArgsConstructor
public class PlatformActivityAdminController {
    private final PlatformActivityService activities;

    @GetMapping
    public PageResponse<PlatformActivityModels.Activity> search(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant until,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditActorSurface actorSurface,
            @RequestParam(required = false) String actionPrefix,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID targetAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("Activity pages support 1 to 100 rows.");
        }
        var query = new PlatformActivityService.Query(
                from, until, outcome, actorSurface, actionPrefix, resourceType, resourceId,
                actorUserId, targetAccountId);
        var order = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
        return PageResponse.from(activities.search(query, PageRequest.of(page, size, order)));
    }

    @GetMapping("/{id}")
    public PlatformActivityModels.Activity detail(@PathVariable UUID id) {
        return activities.detail(id);
    }

    @GetMapping("/{id}/payload")
    public PlatformActivityModels.Payload payload(@PathVariable UUID id) {
        return activities.payload(id);
    }

    @PostMapping("/actor-identities")
    public java.util.List<PlatformActivityModels.ActorResolution> actorIdentities(
            @RequestBody PlatformActivityModels.ResolutionRequest request
    ) {
        return activities.actorIdentities(request.activityIds());
    }

    @PostMapping("/account-identities")
    public java.util.List<PlatformActivityModels.AccountResolution> accountIdentities(
            @RequestBody PlatformActivityModels.ResolutionRequest request
    ) {
        return activities.accountIdentities(request.activityIds());
    }
}
