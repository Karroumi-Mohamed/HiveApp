package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Each Account is assessed/applied in its own transaction; cancellation serializes at the job. */
@Service
@RequiredArgsConstructor
public class PlanVersionRolloutWorker {
  private final SubscriptionChangeJobRepository jobs;
  private final SubscriptionChangeJobItemRepository items;
  private final SubscriptionRepository subscriptions;
  private final PlanRepository plans;
  private final AccountRepository accounts;
  private final PlanVersionApplicationAssessor assessor;
  private final PlanVersionApplicationOperations executor;
  private final PlanVersionRolloutOperations rollouts;
  private final PlanContentNoticeService notices;
  private final PlanContentNoticeRepository noticeRepository;
  private final AdminMutationAuthorizer actors;
  private final CommercialCatalogVersionService catalogue;
  private final RegistryCatalogVersionService registry;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void assess(UUID jobId, UUID itemId) {
    catalogue.lockForMutation();
    registry.lockForMutation();
    var job = jobs.lockById(jobId).orElseThrow();
    if (!job.isContentVersion() || job.getStatus() != SubscriptionChangeJobStatus.ASSESSING) return;
    var item = items.findByIdAndJobId(itemId, jobId).orElseThrow();
    if (item.getStatus() != SubscriptionChangeJobItemStatus.ASSESSING) return;
    Instant now = clock.instant();
    try {
      actors.requireBackgroundPermission(
          job.getRequestedByUserId(), PlanVersionRolloutOperations.PREVIEW);
      actors.requireBackgroundPermission(
          job.getRequestedByUserId(), "platform.plans.create_version_rollout");
      catalogue.requireCurrent(job.getCatalogRevision());
      registry.requireCurrent(job.getRegistryVersion());
      accounts.findByIdForSubscriptionUpdate(item.getAccount().getId()).orElseThrow();
      var current = subscriptions.findCurrentByAccountId(item.getAccount().getId()).orElse(null);
      if (current == null
          || !current.getId().equals(item.getFrozenSubscriptionId())
          || !current.getPlan().getId().equals(item.getFrozenPlanId())) {
        item.assessContent(
            conflict("SOURCE_CHANGED", "The subscription changed after the audience was frozen."),
            now);
      } else if (current.getPlan().getId().equals(job.getContentDefinition().targetPlanId())) {
        item.alreadyOnContentVersion(now);
      } else {
        var target = plans.findById(job.getContentDefinition().targetPlanId()).orElseThrow();
        var assessment =
            assessor.assess(
                current, target, job.getContentDefinition().request().application(), now);
        var conflicts = new ArrayList<>(assessment.conflicts());
        if (job.getContentDefinition().request().notificationPolicy()
                == PlanContentNoticeModels.Policy.EMAIL_REQUIRED
            && (current.getAccount().getOwner() == null
                || !current.getAccount().getOwner().isEmailVerified()))
          conflicts.add(
              new SubscriptionChangeConflict(
                  "NOTICE_RECIPIENT_UNVERIFIED",
                  null,
                  null,
                  null,
                  null,
                  "A verified Account-owner email is required by this notification policy."));
        item.assessContent(
            new PlanVersionRolloutModels.Assessment(
                assessment.reviewed(),
                conflicts,
                assessment.beforeLimits(),
                assessment.afterLimits(),
                assessment.removedFeatures()),
            now);
      }
    } catch (ForbiddenException failure) {
      item.assessContent(
          conflict(
              "ACTOR_NO_LONGER_AUTHORIZED",
              "The requesting operator can no longer review this change."),
          now);
    } catch (StaleResourceVersionException | InvalidStateException failure) {
      item.assessContent(
          conflict(
              "REVIEW_STATE_CHANGED", "Commercial or registry state changed. Create a new review."),
          now);
    }
    items.saveAndFlush(item);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finishAssessment(UUID jobId) {
    var job = jobs.lockById(jobId).orElseThrow();
    if (!job.isContentVersion() || job.getStatus() != SubscriptionChangeJobStatus.ASSESSING) return;
    var counts = rollouts.counts(jobId);
    if (counts.getOrDefault(SubscriptionChangeJobItemStatus.ASSESSING, 0L) != 0) {
      // Round-robin fairness: a large old review must not starve newer due jobs.
      job.deferContent(clock.instant());
      jobs.saveAndFlush(job);
      return;
    }
    job.sealContentAssessment(
        ActivationAssessmentFingerprint.digest(job.getAssessmentFingerprint() + "\n" + counts),
        clock.instant());
    jobs.saveAndFlush(job);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(UUID jobId, UUID itemId) {
    // Consistent order: catalogue -> registry -> job -> Account. Cancellation needs only job.
    catalogue.lockForMutation();
    registry.lockForMutation();
    var job = jobs.lockById(jobId).orElseThrow();
    if (!job.isContentVersion() || job.getStatus() != SubscriptionChangeJobStatus.RUNNING) return;
    var item = items.findByIdAndJobId(itemId, jobId).orElseThrow();
    if (item.getStatus() != SubscriptionChangeJobItemStatus.READY
        && item.getStatus() != SubscriptionChangeJobItemStatus.WAITING) return;
    if (item.getNextAttemptAt() != null && item.getNextAttemptAt().isAfter(clock.instant())) return;
    actors.requireBackgroundPermission(
        job.getRequestedByUserId(), "platform.plans.confirm_version_rollout");
    actors.requireBackgroundPermission(
        job.getRequestedByUserId(), PlanVersionRolloutOperations.APPLY);
    actors.requireBackgroundPermission(
        job.getRequestedByUserId(), PlanVersionRolloutOperations.PREVIEW);
    accounts.findByIdForSubscriptionUpdate(item.getAccount().getId()).orElseThrow();
    var reviewed = item.getContentAssessment().reviewed();
    var notice =
        notices.publish(
            itemId,
            jobId,
            job.getContentDefinition().request().notificationPolicy(),
            reviewed,
            PlanVersionApplicationOperations.plannedAt(
                reviewed.request(), reviewed.periodEnd(), job.getConfirmedAt()));
    if (notice.getPolicy() == PlanContentNoticeModels.Policy.EMAIL_REQUIRED) {
      var delivery = notice.getDelivery().getDelivery();
      if (delivery == RepricingModels.Delivery.PENDING
          || delivery == RepricingModels.Delivery.SENDING) {
        item.waitForContent(clock.instant(), clock.instant().plusSeconds(30));
        items.saveAndFlush(item);
        return;
      }
      var owner = item.getAccount().getOwner();
      String conflictCode =
          delivery != RepricingModels.Delivery.SENT
              ? "NOTICE_REQUIRED_FAILED"
              : owner == null || !owner.isEmailVerified()
                  ? "NOTICE_RECIPIENT_UNVERIFIED"
                  : !owner.getId().equals(notice.getDelivery().getRecipientId())
                      ? "NOTICE_RECIPIENT_CHANGED"
                      : null;
      if (conflictCode != null) {
        item.fail(SubscriptionChangeJobItemStatus.CONFLICT, conflictCode, clock.instant());
        notice.conflicted();
        noticeRepository.saveAndFlush(notice);
        items.saveAndFlush(item);
        return;
      }
    }
    var result =
        executor.executeReviewed(
            itemId, job.getRequestedByUserId(), item.getContentAssessment().reviewed());
    switch (result.outcome()) {
      case APPLIED ->
          item.succeed(
              SubscriptionChangeJobItemStatus.APPLIED, result.operationId(), clock.instant());
      case CONFLICT -> {
        item.rejectContent(result.conflicts(), clock.instant());
        notice.conflicted();
        noticeRepository.saveAndFlush(notice);
      }
      case WAITING -> {
        Instant next = clock.instant().plusSeconds(60);
        var review = item.getContentAssessment().reviewed();
        Instant deadline =
            review.request().notBefore() == null
                ? review.periodEnd()
                : review.request().notBefore();
        if (deadline.isAfter(next)) next = deadline;
        item.waitForContent(clock.instant(), next);
      }
      default -> throw new IllegalStateException("Unexpected content execution result.");
    }
    items.saveAndFlush(item);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(UUID jobId, UUID itemId, RuntimeException failure) {
    var job = jobs.lockById(jobId).orElse(null);
    if (job == null || job.getStatus() == SubscriptionChangeJobStatus.CANCELLED) return;
    var item = items.findByIdAndJobId(itemId, jobId).orElse(null);
    if (item == null || item.getStatus().terminal()) return;
    if (item.getStatus() == SubscriptionChangeJobItemStatus.ASSESSING) {
      item.assessContent(
          conflict("ASSESSMENT_FAILED", "Assessment failed. Create a new review."),
          clock.instant());
    } else if (failure instanceof ForbiddenException) {
      item.fail(
          SubscriptionChangeJobItemStatus.CONFLICT, "ACTOR_NO_LONGER_AUTHORIZED", clock.instant());
    } else if (failure instanceof InvalidRequestException
        || failure instanceof InvalidStateException
        || failure instanceof StaleResourceVersionException
        || failure instanceof ResourceNotFoundException) {
      item.fail(
          SubscriptionChangeJobItemStatus.CONFLICT, "EXECUTION_STATE_CHANGED", clock.instant());
    } else {
      // The Account transaction rolled back. Technical retry retains the same command ID/review.
      item.fail(SubscriptionChangeJobItemStatus.FAILED, "TECHNICAL_FAILURE", clock.instant());
    }
    if (item.getStatus() == SubscriptionChangeJobItemStatus.CONFLICT) {
      noticeRepository
          .findByCommandId(itemId)
          .ifPresent(
              notice -> {
                notice.conflicted();
                noticeRepository.save(notice);
              });
    }
    items.saveAndFlush(item);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finishExecution(UUID jobId) {
    var job = jobs.lockById(jobId).orElseThrow();
    if (!job.isContentVersion() || job.getStatus() != SubscriptionChangeJobStatus.RUNNING) return;
    var counts = rollouts.counts(jobId);
    boolean outstanding =
        counts.entrySet().stream()
            .anyMatch(entry -> !entry.getKey().terminal() && entry.getValue() > 0);
    boolean errors =
        counts.getOrDefault(SubscriptionChangeJobItemStatus.CONFLICT, 0L)
                + counts.getOrDefault(SubscriptionChangeJobItemStatus.FAILED, 0L)
            > 0;
    if (!job.completeContent(outstanding, errors, clock.instant())) {
      Instant earliest =
          items.earliestAttempt(
              jobId,
              List.of(
                  SubscriptionChangeJobItemStatus.READY, SubscriptionChangeJobItemStatus.WAITING));
      job.deferContent(
          earliest == null || earliest.isBefore(clock.instant()) ? clock.instant() : earliest);
    }
    jobs.saveAndFlush(job);
  }

  private PlanVersionRolloutModels.Assessment conflict(String code, String message) {
    return new PlanVersionRolloutModels.Assessment(
        null,
        List.of(new SubscriptionChangeConflict(code, null, null, null, null, message)),
        List.of(),
        List.of(),
        List.of());
  }
}
