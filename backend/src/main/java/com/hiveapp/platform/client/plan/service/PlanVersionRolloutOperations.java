package com.hiveapp.platform.client.plan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.*;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.exception.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public workflow around the existing durable population-job model. Assessment runs off-request.
 */
@Service
@RequiredArgsConstructor
public class PlanVersionRolloutOperations {
  public static final String PREVIEW = "platform.plans.preview_version_application";
  public static final String APPLY = PlanVersionApplicationOperations.APPLY_PERMISSION;
  private final SubscriptionChangeJobRepository jobs;
  private final SubscriptionChangeJobItemRepository items;
  private final PlanRepository plans;
  private final AccountRepository accounts;
  private final PlanContentNoticeService notices;
  private final PlanContentNoticeRepository noticeRepository;
  private final PlanVersionRolloutAudience audience;
  private final CommercialCatalogVersionService catalogue;
  private final RegistryCatalogVersionService registry;
  private final CommercialPreviewTokenService tokens;
  private final AdminMutationAuthorizer actors;
  private final AuditTrail audit;
  private final ObjectMapper json;
  private final jakarta.persistence.EntityManager entityManager;
  private final Clock clock;

  @Transactional
  public Detail create(UUID targetId, Request request) {
    UUID actor = actors.currentActorUserId();
    actors.requireBackgroundPermission(actor, PREVIEW);
    var application = request.application();
    if (application == null
        || application.reason() == null
        || application.reason().isBlank()
        || application.reason().length() > 2000)
      throw new InvalidRequestException("A reason of at most 2000 characters is required.");
    if (application.notBefore() != null && !application.notBefore().isAfter(clock.instant()))
      throw new InvalidRequestException("Choose a future application date.");
    return catalogue.readConsistently(
        revision -> {
          String registryVersion = registry.currentVersion();
          Plan target = plan(targetId);
          Plan source = plan(request.sourcePlanId());
          if (!source.getLineageId().equals(target.getLineageId()) || !target.isActive())
            throw new InvalidRequestException(
                "Choose an active target version in the same Plan family.");
          var population = audience.freeze(source, request);
          var definition = new Definition(targetId, request);
          String fingerprint = digest(List.of(definition, population));
          Instant now = clock.instant();
          var job =
              jobs.saveAndFlush(
                  SubscriptionChangeJob.assessingContent(
                      target.getLineageId(),
                      definition,
                      actor,
                      revision,
                      registryVersion,
                      now,
                      fingerprint));
          persistFrozenAudience(job, population, now);
          registry.requireCurrent(registryVersion);
          record("preview_version_application", job, Map.of("frozenAccounts", population.size()));
          return detail(job);
        });
  }

  @Transactional(readOnly = true)
  public Page<Summary> list(UUID planId, Pageable pageable) {
    var result = jobs.findAllByPlanLineageId(plan(planId).getLineageId(), bounded(pageable));
    Map<UUID, Map<SubscriptionChangeJobItemStatus, Long>> counts = new HashMap<>();
    if (!result.isEmpty())
      for (var count :
          items.countStatusesByJobIds(result.stream().map(SubscriptionChangeJob::getId).toList()))
        counts
            .computeIfAbsent(
                count.getJobId(), ignored -> new EnumMap<>(SubscriptionChangeJobItemStatus.class))
            .put(count.getStatus(), count.getTotal());
    return result.map(job -> summary(job, counts.getOrDefault(job.getId(), Map.of())));
  }

  @Transactional(readOnly = true)
  public Detail get(UUID jobId) {
    return detail(find(jobId));
  }

  @Transactional(readOnly = true)
  public Page<Item> results(
      UUID jobId, SubscriptionChangeJobItemStatus status, String reason, Pageable pageable) {
    find(jobId);
    if (reason != null && reason.length() > 100)
      throw new InvalidRequestException("Reason code is too long.");
    var result =
        items.findAll(
            (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("job").get("id"), jobId),
                    status == null ? cb.conjunction() : cb.equal(root.get("status"), status),
                    reason == null || reason.isBlank()
                        ? cb.conjunction()
                        : cb.equal(root.get("outcomeCode"), reason)),
            bounded(pageable));
    var deliveries =
        notices.deliveryViews(result.stream().map(SubscriptionChangeJobItem::getId).toList());
    return result.map(
        item ->
            new Item(
                item.getId(),
                item.getStatus(),
                item.getFrozenSubscriptionId(),
                impact(item.getContentAssessment()),
                item.getExecutionConflicts() == null ? List.of() : item.getExecutionConflicts(),
                deliveries.get(item.getId()),
                item.getSubscriptionOperationId(),
                item.getOutcomeCode(),
                item.getAttempts(),
                item.getNextAttemptAt(),
                item.getCompletedAt()));
  }

  @Transactional(readOnly = true)
  public List<com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels.Identity> identities(
      UUID jobId, List<UUID> resultIds) {
    find(jobId);
    if (resultIds == null
        || resultIds.isEmpty()
        || resultIds.size() > 100
        || new HashSet<>(resultIds).size() != resultIds.size())
      throw new InvalidRequestException("Choose between 1 and 100 unique result IDs.");
    var result = items.findAllByJobIdAndIdIn(jobId, resultIds);
    if (result.size() != resultIds.size())
      throw new InvalidRequestException("Some results do not belong to this application.");
    return result.stream()
        .map(
            item ->
                new com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels.Identity(
                    item.getId(), item.getAccount().getId(), item.getAccount().getName()))
        .toList();
  }

  @Transactional
  public Detail confirm(UUID jobId, Confirm request) {
    UUID actor = actors.currentActorUserId();
    actors.requireBackgroundPermission(actor, APPLY);
    actors.requireBackgroundPermission(actor, PREVIEW);
    catalogue.lockForMutation();
    registry.lockForMutation();
    var job = locked(jobId);
    requireOwner(job, actor);
    catalogue.requireCurrent(job.getCatalogRevision());
    registry.requireCurrent(job.getRegistryVersion());
    tokens.requireValid(
        request.previewToken(),
        CommercialPreviewKind.PLAN_VERSION_ROLLOUT,
        jobId,
        job.getVersion(),
        actor,
        job.getCatalogRevision(),
        job.getRegistryVersion(),
        job.getAssessmentFingerprint(),
        () -> new StaleResourceVersionException("Review this audience again before confirming."));
    var counts = counts(jobId);
    if (job.getStatus() != SubscriptionChangeJobStatus.PREVIEWED
        || counts.getOrDefault(SubscriptionChangeJobItemStatus.READY, 0L) == 0)
      throw new InvalidStateException("The reviewed audience contains no ready subscriptions.");
    if (!request.applyReadyOnly()
        && (counts.getOrDefault(SubscriptionChangeJobItemStatus.CONFLICT, 0L) > 0
            || counts.getOrDefault(SubscriptionChangeJobItemStatus.FAILED, 0L) > 0))
      throw new InvalidRequestException(
          "Explicitly choose to apply ready Accounts only, or resolve conflicts and review again.");
    if (items.countCompetingContent(jobId) > 0)
      throw new InvalidStateException(
          "Another confirmed content change now targets this audience. Resolve it and review"
              + " again.");
    job.confirm(clock.instant());
    job.prepareContentNotices(clock.instant());
    jobs.saveAndFlush(job);
    record(
        "apply_version",
        job,
        Map.of(
            "confirmedReadyAccounts",
            counts.get(SubscriptionChangeJobItemStatus.READY),
            "applyReadyOnly",
            request.applyReadyOnly()));
    return detail(job);
  }

  @Transactional
  public Detail cancel(UUID jobId, String reason) {
    var job = locked(jobId);
    try {
      job.cancelContent(actors.currentActorUserId(), reason, clock.instant());
    } catch (IllegalStateException | IllegalArgumentException failure) {
      throw new InvalidStateException(failure.getMessage());
    }
    items.transitionItems(
        jobId,
        List.of(
            SubscriptionChangeJobItemStatus.ASSESSING,
            SubscriptionChangeJobItemStatus.READY,
            SubscriptionChangeJobItemStatus.WAITING),
        SubscriptionChangeJobItemStatus.CANCELLED,
        "CANCELLED_BY_OPERATOR",
        clock.instant());
    noticeRepository.cancelUnapplied(
        jobId,
        com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.State.CANCELLED,
        com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.State.APPLIED);
    jobs.saveAndFlush(job);
    record("cancel_version_application", job, Map.of("reason", reason));
    return detail(job);
  }

  @Transactional
  public Detail retry(UUID jobId, String reason) {
    UUID actor = actors.currentActorUserId();
    actors.requireBackgroundPermission(actor, APPLY);
    actors.requireBackgroundPermission(actor, PREVIEW);
    var job = locked(jobId);
    // A different operator must create their own review, not inherit another actor's authority.
    requireOwner(job, actor);
    if (counts(jobId).getOrDefault(SubscriptionChangeJobItemStatus.FAILED, 0L) == 0)
      throw new InvalidStateException(
          "There are no technical failures to retry. Conflicts require a new review.");
    try {
      job.retryContent(actor, reason, clock.instant());
    } catch (IllegalStateException | IllegalArgumentException failure) {
      throw new InvalidStateException(failure.getMessage());
    }
    items.retryTechnicalItems(
        jobId,
        SubscriptionChangeJobItemStatus.READY,
        SubscriptionChangeJobItemStatus.FAILED,
        clock.instant());
    jobs.saveAndFlush(job);
    record("retry_version_application", job, Map.of("reason", reason));
    return detail(job);
  }

  @Transactional
  public Detail retryNotices(
      UUID jobId, com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Retry request) {
    UUID actor = actors.currentActorUserId();
    actors.requireBackgroundPermission(actor, APPLY);
    actors.requireBackgroundPermission(actor, PREVIEW);
    var job = locked(jobId);
    requireOwner(job, actor);
    if (job.getStatus() == SubscriptionChangeJobStatus.CANCELLED)
      throw new InvalidStateException("Cancelled applications cannot send new notices.");
    if (request.noticeIds() == null
        || request.noticeIds().isEmpty()
        || request.noticeIds().size() > 100
        || new HashSet<>(request.noticeIds()).size() != request.noticeIds().size())
      throw new InvalidRequestException("Choose between 1 and 100 unique notices.");
    var selected = noticeRepository.findAllById(request.noticeIds());
    if (selected.size() != request.noticeIds().size()
        || selected.stream().anyMatch(n -> !jobId.equals(n.getJobId())))
      throw new InvalidRequestException("Some notices do not belong to this application.");
    selected.sort(Comparator.comparing(n -> n.getAccount().getId()));
    boolean resume = false;
    for (var snapshot : selected) {
      var account =
          accounts.findByIdForSubscriptionUpdate(snapshot.getAccount().getId()).orElseThrow();
      var notice = noticeRepository.lock(snapshot.getId()).orElseThrow();
      var item = items.findByIdAndJobId(notice.getCommandId(), jobId).orElseThrow();
      boolean changedRecipient =
          "NOTICE_RECIPIENT_CHANGED".equals(item.getOutcomeCode()) && account.getOwner() != null;
      boolean retried =
          notice.getDelivery().retry()
              || (changedRecipient
                  && notice.getDelivery().retryForNewRecipient(account.getOwner().getId()));
      if (!retried)
        throw new InvalidStateException(
            "Only failed/suppressed notices or a changed recipient can be retried.");
      if (item.resumeAfterNotice(clock.instant())) {
        notice.resumeAfterNoticeRetry();
        resume = true;
        items.save(item);
      }
      noticeRepository.save(notice);
    }
    if (resume) job.resumeAfterNotice(actor, request.reason(), clock.instant());
    jobs.saveAndFlush(job);
    record(
        "retry_version_notices",
        job,
        Map.of("reason", request.reason(), "notices", selected.size()));
    return detail(job);
  }

  public Map<SubscriptionChangeJobItemStatus, Long> counts(UUID jobId) {
    Map<SubscriptionChangeJobItemStatus, Long> counts =
        new EnumMap<>(SubscriptionChangeJobItemStatus.class);
    for (var count : items.countStatusesByJobIds(List.of(jobId)))
      counts.put(count.getStatus(), count.getTotal());
    return counts;
  }

  private ResolutionKind resolutionKind(String code) {
    if (code == null) return ResolutionKind.REVIEW_VERSION;
    if (code.contains("NOTICE")) return ResolutionKind.REVIEW_NOTICE;
    if (code.matches(".*(POLICY|BONUS|OFFER|AGREEMENT).*"))
      return ResolutionKind.REVIEW_COMMERCIAL_TERMS;
    if (code.matches(".*(QUOTA|CAPACITY|USAGE|LIMIT).*")) return ResolutionKind.REVIEW_CAPACITY;
    if (code.matches(".*(ADD_ON|PACK|PURCHASE|DEPENDENC|DUPLICATE_PAID).*"))
      return ResolutionKind.REVIEW_PURCHASES;
    if (code.matches(".*(PENDING|PERIOD|TRIAL|ENTITLED|ACCOUNT).*"))
      return ResolutionKind.REVIEW_SUBSCRIPTION;
    return ResolutionKind.REVIEW_VERSION;
  }

  private Detail detail(SubscriptionChangeJob job) {
    boolean stale =
        job.getCatalogRevision() != catalogue.currentRevision()
            || !job.getRegistryVersion().equals(registry.currentVersion());
    CommercialPreviewTokenService.IssuedEvidence token = null;
    if (job.getStatus() == SubscriptionChangeJobStatus.PREVIEWED
        && !stale
        && job.getRequestedByUserId().equals(actors.currentActorUserId()))
      token =
          tokens.issue(
              CommercialPreviewKind.PLAN_VERSION_ROLLOUT,
              job.getId(),
              job.getVersion(),
              job.getRequestedByUserId(),
              job.getCatalogRevision(),
              job.getRegistryVersion(),
              job.getAssessmentFingerprint(),
              clock.instant());
    var conflicts =
        items.countPrimaryConflicts(job.getId()).stream()
            .map(
                group ->
                    new ConflictGroup(
                        group.getCode(), group.getTotal(), resolutionKind(group.getCode())))
            .toList();
    return new Detail(
        summary(job, counts(job.getId())),
        job.getContentDefinition(),
        conflicts,
        stale,
        token == null ? null : token.token(),
        token == null ? null : token.expiresAt());
  }

  private Summary summary(
      SubscriptionChangeJob job, Map<SubscriptionChangeJobItemStatus, Long> counts) {
    return new Summary(
        job.getId(),
        job.getPlanLineageId(),
        job.getContentDefinition().targetPlanId(),
        job.getStatus(),
        counts,
        job.getCreatedAt(),
        job.getEvaluatedAt(),
        job.getExecuteAt(),
        job.getCompletedAt(),
        job.getRequestedByUserId(),
        job.getReason(),
        job.getVersion());
  }

  private Impact impact(Assessment assessment) {
    if (assessment == null) return null;
    var reviewed = assessment.reviewed();
    return new Impact(
        reviewed == null ? 0 : reviewed.before().planDefinitionVersion(),
        reviewed == null ? 0 : reviewed.target().planDefinitionVersion(),
        reviewed == null ? null : reviewed.total(),
        reviewed == null ? null : reviewed.currency(),
        assessment.conflicts(),
        assessment.beforeLimits(),
        assessment.afterLimits(),
        assessment.removedFeatures(),
        reviewed == null ? List.of() : reviewed.addedFeatures());
  }

  private Plan plan(UUID id) {
    return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plan", "id", id));
  }

  private SubscriptionChangeJob find(UUID id) {
    return jobs.findById(id)
        .filter(SubscriptionChangeJob::isContentVersion)
        .orElseThrow(() -> new ResourceNotFoundException("PlanVersionRollout", "id", id));
  }

  private SubscriptionChangeJob locked(UUID id) {
    return jobs.lockById(id)
        .filter(SubscriptionChangeJob::isContentVersion)
        .orElseThrow(() -> new ResourceNotFoundException("PlanVersionRollout", "id", id));
  }

  private void requireOwner(SubscriptionChangeJob job, UUID actor) {
    if (!job.getRequestedByUserId().equals(actor))
      throw new ForbiddenException(
          "Create your own reviewed audience before confirming or retrying.");
  }

  private Pageable bounded(Pageable page) {
    return PageRequest.of(
        page.getPageNumber(),
        Math.min(100, page.getPageSize()),
        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
  }

  private String digest(Object value) {
    try {
      return ActivationAssessmentFingerprint.digest(json.writeValueAsString(value));
    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private void persistFrozenAudience(
      SubscriptionChangeJob job, List<PlanVersionRolloutAudience.Member> population, Instant now) {
    // Bounded scalar freeze and JDBC batching keep large reviews off the entitlement hot path.
    var session = entityManager.unwrap(org.hibernate.Session.class);
    Integer previousBatch = session.getJdbcBatchSize();
    session.setJdbcBatchSize(50);
    try {
      int count = 0;
      for (var member : population) {
        items.save(
            SubscriptionChangeJobItem.frozenContent(
                jobs.getReferenceById(job.getId()),
                accounts.getReferenceById(member.accountId()),
                member.subscriptionId(),
                member.sourcePlanId(),
                member.excluded(),
                now));
        if (++count % 250 == 0) {
          items.flush();
          entityManager.clear();
        }
      }
      items.flush();
    } finally {
      session.setJdbcBatchSize(previousBatch);
    }
  }

  private void record(String permission, SubscriptionChangeJob job, Map<String, Object> details) {
    audit.recordSuccess(
        "platform.plans." + permission,
        "PLAN_VERSION_ROLLOUT",
        job.getId(),
        AuditActorSurface.PLATFORM_ADMIN,
        actors.currentActorUserId(),
        null,
        Map.of(),
        details);
  }
}
