package com.hiveapp.platform.client.plan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.*;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.exception.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional content executor shared by the single-Account command and population workers. */
@Service
@RequiredArgsConstructor
public class PlanVersionApplicationOperations {
  public static final String APPLY_PERMISSION = "platform.plans.apply_version";
  private final PlanRepository plans;
  private final SubscriptionRepository subscriptions;
  private final AccountRepository accounts;
  private final SubscriptionPeriodRepository periods;
  private final SubscriptionChangeOperationRepository operations;
  private final SubscriptionContentEvidenceRepository evidence;
  private final PlanVersionApplicationAssessor assessor;
  private final PlanVersionContentRules rules;
  private final CommercialCatalogVersionService catalogue;
  private final RegistryCatalogVersionService registry;
  private final CommercialPreviewTokenService tokens;
  private final AdminMutationAuthorizer actors;
  private final AuditTrail audit;
  private final ObjectMapper json;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Preview preview(UUID targetId, UUID accountId, Request request) {
    requireReason(request);
    return catalogue.readConsistently(
        revision -> {
          var current = current(accountId);
          var target = plan(targetId);
          String registryVersion = registry.currentVersion();
          Instant now = clock.instant();
          var assessment = assessor.assess(current, target, request, now);
          var token =
              tokens.issue(
                  CommercialPreviewKind.PLAN_VERSION_APPLICATION,
                  accountId,
                  current.getVersion(),
                  actors.currentActorUserId(),
                  revision,
                  registryVersion,
                  fingerprint(targetId, request, assessment),
                  now);
          registry.requireCurrent(registryVersion);
          return new Preview(
              accountId,
              current.getPlan().getId(),
              targetId,
              current.getPlan().getRevisionNumber(),
              target.getRevisionNumber(),
              request,
              plannedAt(request, current.getCurrentPeriodEnd(), now),
              current.getCurrentPrice(),
              current.getCurrentPriceCurrencyCode(),
              assessment.beforeLimits(),
              assessment.afterLimits(),
              assessment.removedFeatures(),
              assessment.conflicts(),
              token.token(),
              token.expiresAt());
        });
  }

  @Transactional
  public Result applyNow(UUID targetId, UUID accountId, ApplyNow command) {
    requireReason(command.request());
    if (command.request().timing() != Timing.NOW)
      throw new InvalidRequestException(
          "Future applications must use a durable reviewed population job.");
    UUID actor = actors.currentActorUserId();
    actors.requireBackgroundPermission(actor, APPLY_PERMISSION);
    lock(accountId);
    var previous = evidence.findByCommandId(command.commandId()).orElse(null);
    if (previous != null) return repeated(previous, actor, accountId, targetId, command.request());
    var current = current(accountId);
    var target = plan(targetId);
    var assessment = assessor.assess(current, target, command.request(), clock.instant());
    tokens.requireValid(
        command.previewToken(),
        CommercialPreviewKind.PLAN_VERSION_APPLICATION,
        accountId,
        current.getVersion(),
        actor,
        catalogue.currentRevision(),
        registry.currentVersion(),
        fingerprint(targetId, command.request(), assessment),
        this::stale);
    requireReady(assessment);
    return applyLocked(
        command.commandId(), actor, current, target, assessment.reviewed(), clock.instant());
  }

  /** The durable job stores the review; retries never substitute a newly accepted assessment. */
  @Transactional
  public Result executeReviewed(UUID commandId, UUID actor, Reviewed reviewed) {
    actors.requireBackgroundPermission(actor, APPLY_PERMISSION);
    actors.requireBackgroundPermission(actor, "platform.plans.preview_version_application");
    lock(reviewed.accountId());
    var previous = evidence.findByCommandId(commandId).orElse(null);
    if (previous != null)
      return repeated(
          previous, actor, reviewed.accountId(), reviewed.targetPlanId(), reviewed.request());
    Instant now = clock.instant();
    var current = subscriptions.findCurrentByAccountId(reviewed.accountId()).orElse(null);
    if (current == null || !matchesReviewedTerms(current, reviewed))
      return conflict(
          "SUBSCRIPTION_CHANGED",
          "The subscription changed after review. Review it again before applying this version.");
    if (!current.getAccount().isActive() || current.isCancelAtPeriodEnd())
      return conflict(
          "LIFECYCLE_CHANGED", "The Account was disabled or cancellation was scheduled.");
    if (reviewed.request().timing() == Timing.AT_DATE
        && now.isBefore(reviewed.request().notBefore())) return waiting();
    if (reviewed.request().timing() == Timing.AT_RENEWAL
        && current.getCurrentPeriodStart().isBefore(reviewed.periodEnd())) {
      // Keep billing and recovery authoritative. Merely passing a deadline is not evidence
      // that the customer successfully renewed their paid period.
      if (current.getStatus() == SubscriptionStatus.ACTIVE
          || current.getStatus() == SubscriptionStatus.PAST_DUE
          || (current.getStatus() == SubscriptionStatus.SUSPENDED
              && current.getSuspensionCause() == SubscriptionSuspensionCause.COLLECTION))
        return waiting();
      return conflict(
          "RENEWAL_NOT_ELIGIBLE", "The reviewed subscription can no longer renew normally.");
    }
    if (reviewed.request().timing() == Timing.NOW
        && (!current.getCurrentPeriodStart().equals(reviewed.periodStart())
            || !current.getCurrentPeriodEnd().equals(reviewed.periodEnd())))
      return conflict(
          "PAID_PERIOD_CHANGED",
          "The paid period changed before immediate application; review again.");
    var target = plan(reviewed.targetPlanId());
    var fresh = assessor.assess(current, target, reviewed.request(), now, commandId);
    if (!fresh.conflicts().isEmpty())
      return new Result(Outcome.CONFLICT, null, null, null, fresh.conflicts());
    if (!fresh
        .reviewed()
        .target()
        .withEffectivePeriod(null, null)
        .equals(reviewed.target().withEffectivePeriod(null, null)))
      return conflict("TARGET_CHANGED", "The target content changed after review.");
    return applyLocked(
        commandId,
        actor,
        current,
        target,
        fresh.reviewed(),
        plannedAt(reviewed.request(), reviewed.periodEnd(), now));
  }

  private Result applyLocked(
      UUID commandId,
      UUID actor,
      Subscription current,
      Plan target,
      Reviewed reviewed,
      Instant plannedAt) {
    rules.requirePreservedTerms(current.getEntitlementSnapshot(), reviewed.target());
    var period =
        periods
            .findBySubscriptionIdAndStatus(current.getId(), SubscriptionPeriodStatus.OPEN)
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "An open paid-period record is required; no billing history was changed."));
    if (!period.getStartsAt().equals(current.getCurrentPeriodStart())
        || !period.getEndsAt().equals(current.getCurrentPeriodEnd()))
      throw new InvalidStateException("The billing period changed during content review.");
    Instant actual = clock.instant();
    var operation = new SubscriptionChangeOperation();
    operation.setAccount(current.getAccount());
    operation.setSourceSubscription(current);
    operation.setTargetPlan(target);
    operation.setResultSubscription(current);
    operation.setTiming(
        reviewed.request().timing() == Timing.AT_RENEWAL
            ? SubscriptionChangeTiming.AT_RENEWAL
            : SubscriptionChangeTiming.IMMEDIATE);
    operation.setStatus(SubscriptionChangeStatus.APPLIED);
    operation.setEffectiveAt(actual);
    operation.setRequestedSelection(current.getCustomOverrides());
    operation.setBeforeSnapshot(current.getEntitlementSnapshot());
    operation.setTargetSnapshot(reviewed.target());
    operation.setRequestOrigin(SubscriptionChangeOrigin.PLATFORM_ADMIN);
    operation.setRequestedByUserId(actor);
    operation.setRequestReason(reviewed.request().reason());
    operation.setContentCommandId(commandId);
    operations.saveAndFlush(operation);
    var record =
        evidence.saveAndFlush(
            new SubscriptionContentEvidence(
                commandId,
                current.getId(),
                operation.getId(),
                period.getId(),
                actor,
                plannedAt,
                actual,
                reviewed));
    current.applyContentVersion(target, reviewed.target(), record.getId());
    subscriptions.saveAndFlush(current);
    audit.recordSuccess(
        APPLY_PERMISSION,
        "SUBSCRIPTION_CONTENT_EVIDENCE",
        record.getId(),
        AuditActorSurface.PLATFORM_ADMIN,
        actor,
        reviewed.accountId(),
        Map.of("sourcePlanId", reviewed.sourcePlanId()),
        Map.of(
            "targetPlanId",
            target.getId(),
            "operationId",
            operation.getId(),
            "reason",
            reviewed.request().reason(),
            "plannedAt",
            plannedAt.toString(),
            "effectiveAt",
            actual.toString(),
            "financialTermsRetained",
            true));
    return new Result(Outcome.APPLIED, operation.getId(), record.getId(), actual, List.of());
  }

  private boolean matchesReviewedTerms(Subscription current, Reviewed reviewed) {
    return current.termsIdentity().equals(reviewed.financialTermsId())
        && current.getPlan().getId().equals(reviewed.sourcePlanId())
        && Objects.equals(current.getContentEvidenceId(), reviewed.previousEvidenceId())
        && current
            .getEntitlementSnapshot()
            .withEffectivePeriod(null, null)
            .equals(reviewed.before().withEffectivePeriod(null, null))
        && Objects.equals(current.getCustomOverrides(), reviewed.overrides())
        && current.getCurrentPrice() != null
        && current.getCurrentPrice().compareTo(reviewed.total()) == 0
        && Objects.equals(current.getCurrentPriceCurrencyCode(), reviewed.currency());
  }

  private Result repeated(
      SubscriptionContentEvidence previous,
      UUID actor,
      UUID account,
      UUID target,
      Request request) {
    if (!previous.getActorUserId().equals(actor)
        || !previous.getAccountId().equals(account)
        || !previous.getReviewedTerms().targetPlanId().equals(target)
        || !previous.getReviewedTerms().request().equals(request))
      throw new InvalidRequestException(
          "This command identifier was already used for a different instruction.");
    return new Result(
        Outcome.APPLIED,
        previous.getOperationId(),
        previous.getId(),
        previous.getEffectiveAt(),
        List.of());
  }

  private void lock(UUID accountId) {
    catalogue.lockForMutation();
    registry.lockForMutation();
    accounts
        .findByIdForSubscriptionUpdate(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
  }

  private Plan plan(UUID id) {
    return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plan", "id", id));
  }

  private Subscription current(UUID account) {
    return subscriptions
        .findCurrentByAccountId(account)
        .orElseThrow(() -> new InvalidStateException("The Account has no current subscription."));
  }

  private void requireReason(Request request) {
    if (request == null
        || request.reason() == null
        || request.reason().isBlank()
        || request.reason().length() > 2000)
      throw new InvalidRequestException("A reason of at most 2000 characters is required.");
    if (request.timing() == Timing.AT_DATE && !request.notBefore().isAfter(clock.instant()))
      throw new InvalidRequestException("Choose a future application date.");
  }

  private void requireReady(PlanVersionApplicationAssessor.Assessment assessment) {
    if (!assessment.conflicts().isEmpty())
      throw new OperationBlockedException(
          "The version application has unresolved conflicts.",
          assessment.conflicts().stream().map(SubscriptionChangeConflict::code).toList());
  }

  private String fingerprint(
      UUID targetId, Request request, PlanVersionApplicationAssessor.Assessment assessment) {
    try {
      // Sorting sets explicitly avoids a false stale review after a JSON round trip.
      var tree = json.valueToTree(assessment);
      if (assessment.reviewed() != null) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("reviewed").path("overrides"))
            .set(
                "addOnCodes",
                json.valueToTree(new TreeSet<>(assessment.reviewed().overrides().addOnCodes())));
      }
      return ActivationAssessmentFingerprint.digest(
          targetId
              + "\n"
              + json.writeValueAsString(request)
              + "\n"
              + json.writeValueAsString(tree));
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Cannot fingerprint version review", exception);
    }
  }

  public static Instant plannedAt(Request request, Instant renewalAt, Instant now) {
    return switch (request.timing()) {
      case NOW -> now;
      case AT_RENEWAL -> renewalAt;
      case AT_DATE -> request.notBefore();
    };
  }

  private StaleResourceVersionException stale() {
    return new StaleResourceVersionException(
        "Version application changed or expired. Review again.");
  }

  private Result waiting() {
    return new Result(Outcome.WAITING, null, null, null, List.of());
  }

  private Result conflict(String code, String message) {
    return new Result(
        Outcome.CONFLICT,
        null,
        null,
        null,
        List.of(new SubscriptionChangeConflict(code, null, null, null, null, message)));
  }
}
