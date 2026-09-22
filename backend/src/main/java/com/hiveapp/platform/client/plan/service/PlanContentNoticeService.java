package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.*;
import com.hiveapp.shared.exception.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shared private notice projection for content jobs and immediate single-Account commands. */
@Service
@RequiredArgsConstructor
public class PlanContentNoticeService {
  private final PlanContentNoticeRepository notices;
  private final AccountRepository accounts;
  private final CommercialNoticeReadRepository reads;
  private final SubscriptionImpactAnalyzer impacts;
  private final Clock clock;
  private final com.hiveapp.platform.communication.CommunicationSources communicationSources;

  @Transactional
  public PlanContentNotice publish(
      UUID commandId,
      UUID jobId,
      Policy policy,
      PlanVersionApplicationModels.Reviewed review,
      Instant plannedAt) {
    var existing = notices.findByCommandId(commandId).orElse(null);
    if (existing != null) {
      requireCompatible(existing, review);
      return existing;
    }
    var afterCodes =
        review.target().features().stream()
            .map(SubscriptionFeatureSnapshot::featureCode)
            .collect(Collectors.toSet());
    var removed =
        review.before().features().stream()
            .map(SubscriptionFeatureSnapshot::featureCode)
            .filter(code -> !afterCodes.contains(code))
            .sorted()
            .toList();
    var impact =
        new PlanVersionRolloutModels.Impact(
            review.before().planDefinitionVersion(),
            review.target().planDefinitionVersion(),
            review.total(),
            review.currency(),
            List.of(),
            impacts.effectiveQuotaLimits(review.before()),
            impacts.effectiveQuotaLimits(review.target()),
            removed,
            review.addedFeatures());
    var created = notices.saveAndFlush(
        new PlanContentNotice(
            commandId,
            jobId,
            review.sourcePlanId(),
            review.targetPlanId(),
            accounts.getReferenceById(review.accountId()),
            policy,
            Objects.toString(review.target().planName(), review.target().planCode()),
            review.before().planDefinitionVersion(),
            review.target().planDefinitionVersion(),
            review.request().timing(),
            plannedAt,
            impact,
            clock.instant()));
    communicationSources.index(created);
    return created;
  }

  @Transactional
  public void applied(
      UUID commandId,
      PlanVersionApplicationModels.Reviewed review,
      Instant plannedAt,
      Instant actual) {
    var notice = publish(commandId, null, Policy.IN_APP, review, plannedAt);
    requireReady(notice, review.accountId());
    notice.applied(actual);
    notices.saveAndFlush(notice);
  }

  public void requireStandalone(UUID commandId) {
    if (notices.findByCommandId(commandId).map(PlanContentNotice::getJobId).isPresent())
      throw new InvalidStateException(
          "A population command cannot be used through the standalone application API.");
  }

  public Optional<Instant> plannedAt(UUID commandId) {
    return notices.findByCommandId(commandId).map(PlanContentNotice::getPlannedAt);
  }

  public void requireReady(PlanContentNotice notice, UUID accountId) {
    if (notice.getState() == State.CANCELLED || notice.getState() == State.CONFLICT)
      throw new InvalidStateException(
          "This notification belongs to a cancelled or conflicted instruction.");
    if (notice.getPolicy() != Policy.EMAIL_REQUIRED) return;
    var account = accounts.findById(accountId).orElseThrow();
    if (notice.getDelivery().getDelivery() != RepricingModels.Delivery.SENT
        || account.getOwner() == null
        || !account.getOwner().isEmailVerified()
        || !account.getOwner().getId().equals(notice.getDelivery().getRecipientId()))
      throw new InvalidStateException(
          "Required notice dispatch has not been completed for the current Account owner.");
  }

  @Transactional(readOnly = true)
  public Map<UUID, Delivery> deliveryViews(Collection<UUID> commandIds) {
    if (commandIds.isEmpty()) return Map.of();
    return notices.findAllByCommandIdIn(commandIds).stream()
        .collect(
            Collectors.toMap(
                PlanContentNotice::getCommandId,
                n ->
                    new Delivery(
                        n.getId(),
                        n.getState(),
                        n.getDelivery().getDelivery(),
                        n.getDelivery().getAttempts(),
                        n.getDelivery().getCreatedAt(),
                        n.getPolicy() == Policy.EMAIL_REQUIRED)));
  }

  @Transactional(readOnly = true)
  public Page<Notice> list(UUID accountId, UUID userId, Pageable page) {
    var found =
        notices.findAllByAccountId(
            accountId,
            PageRequest.of(
                page.getPageNumber(),
                Math.min(100, page.getPageSize()),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
    var viewed =
        found.isEmpty()
            ? Set.<UUID>of()
            : reads
                .findAllByUserIdAndNoticeIdIn(
                    userId, found.stream().map(PlanContentNotice::getId).toList())
                .stream()
                .map(CommercialNoticeRead::getNoticeId)
                .collect(Collectors.toSet());
    return found.map(
        n ->
            new Notice(
                n.getId(),
                n.getPlanName(),
                n.getSourceVersion(),
                n.getTargetVersion(),
                n.getState(),
                n.getTiming(),
                n.getPlannedAt(),
                n.getEffectiveAt(),
                n.getImpact().beforeLimits(),
                n.getImpact().afterLimits(),
                n.getImpact().removedFeatures(),
                n.getImpact().addedFeatures(),
                true,
                viewed.contains(n.getId()),
                n.getDelivery().getCreatedAt()));
  }

  @Transactional
  public void markRead(UUID accountId, UUID userId, UUID id) {
    accounts
        .findByIdForSubscriptionUpdate(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
    notices
        .findByIdAndAccountId(id, accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Notice", "id", id));
    if (!reads.existsByNoticeIdAndUserId(id, userId)) {
      var read = new CommercialNoticeRead();
      read.setNoticeId(id);
      read.setUserId(userId);
      read.setReadAt(clock.instant());
      reads.save(read);
    }
  }

  private void requireCompatible(
      PlanContentNotice notice, PlanVersionApplicationModels.Reviewed review) {
    if (!notice.getAccount().getId().equals(review.accountId())
        || !notice.getSourcePlanId().equals(review.sourcePlanId())
        || !notice.getTargetPlanId().equals(review.targetPlanId())
        || notice.getTiming() != review.request().timing()
        || (review.request().notBefore() != null
            && !review.request().notBefore().equals(notice.getPlannedAt())))
      throw new InvalidStateException(
          "This command ID already identifies a different content notice.");
  }
}
