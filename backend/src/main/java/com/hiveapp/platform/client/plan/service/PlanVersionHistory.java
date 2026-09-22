package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.shared.audit.domain.*;
import com.hiveapp.shared.exception.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Family history projects existing append-only audit, never arbitrary request/result payloads. */
@Service
@RequiredArgsConstructor
public class PlanVersionHistory {
  public enum Kind {
    VERSION,
    APPLICATION
  }

  public record Event(
      UUID id,
      Instant occurredAt,
      Kind kind,
      String action,
      AuditOutcome outcome,
      UUID actorUserId,
      String actorLabel,
      UUID resourceId,
      Integer productVersionNumber) {}

  private final PlanRepository plans;
  private final AuditLogRepository audit;
  private final AdminUserRepository admins;

  @Transactional(readOnly = true)
  public Page<Event> list(
      UUID planId, Kind kind, UUID actorId, Instant from, Instant until, Pageable page) {
    var plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
    if (page.getPageSize() > 100
        || page.getPageSize() < 1
        || (from != null && until != null && !from.isBefore(until)))
      throw new InvalidRequestException("Choose a valid page and chronological date range.");
    var logs =
        audit.findAll(
            (root, query, cb) -> {
              var versions = query.subquery(String.class);
              var version = versions.from(Plan.class);
              versions
                  .select(version.get("id").as(String.class))
                  .where(cb.equal(version.get("lineageId"), plan.getLineageId()));
              var jobs = query.subquery(String.class);
              var job = jobs.from(SubscriptionChangeJob.class);
              jobs.select(job.get("id").as(String.class))
                  .where(cb.equal(job.get("planLineageId"), plan.getLineageId()));
              var versionEvents =
                  cb.and(
                      root.get("resourceType").in("PLAN", "PLAN_ADMIN"),
                      root.get("resourceId").in(versions));
              var applications =
                  cb.and(
                      cb.equal(root.get("resourceType"), "PLAN_VERSION_ROLLOUT"),
                      root.get("resourceId").in(jobs));
              return cb.and(
                  kind == Kind.VERSION
                      ? versionEvents
                      : kind == Kind.APPLICATION
                          ? applications
                          : cb.or(versionEvents, applications),
                  actorId == null ? cb.conjunction() : cb.equal(root.get("actorUserId"), actorId),
                  from == null
                      ? cb.conjunction()
                      : cb.greaterThanOrEqualTo(root.get("occurredAt"), from),
                  until == null ? cb.conjunction() : cb.lessThan(root.get("occurredAt"), until));
            },
            PageRequest.of(
                page.getPageNumber(),
                page.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt")
                    .and(Sort.by(Sort.Direction.DESC, "id"))));
    var actorIds =
        logs.stream()
            .map(AuditLog::getActorUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    var labels =
        actorIds.isEmpty()
            ? Map.<UUID, String>of()
            : admins.findAllWithUserByUserIdIn(actorIds).stream()
                .collect(
                    Collectors.toMap(
                        admin -> admin.getUser().getId(),
                        admin ->
                            (Objects.toString(admin.getUser().getFirstName(), "")
                                    + " "
                                    + Objects.toString(admin.getUser().getLastName(), ""))
                                .trim()));
    var ids =
        logs.stream()
            .filter(log -> !log.getResourceType().equals("PLAN_VERSION_ROLLOUT"))
            .map(log -> UUID.fromString(log.getResourceId()))
            .toList();
    var versions =
        ids.isEmpty()
            ? Map.<UUID, Integer>of()
            : plans.findAllById(ids).stream()
                .collect(Collectors.toMap(Plan::getId, Plan::getRevisionNumber));
    return logs.map(
        log ->
            new Event(
                log.getId(),
                log.getOccurredAt(),
                log.getResourceType().equals("PLAN_VERSION_ROLLOUT")
                    ? Kind.APPLICATION
                    : Kind.VERSION,
                log.getAction(),
                log.getOutcome(),
                log.getActorUserId(),
                log.getActorUserId() == null ? null : labels.get(log.getActorUserId()),
                UUID.fromString(log.getResourceId()),
                versions.get(UUID.fromString(log.getResourceId()))));
  }
}
