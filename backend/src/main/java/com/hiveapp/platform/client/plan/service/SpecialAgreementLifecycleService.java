package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementAttentionStage;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementEndInstruction;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.SpecialCommercialAgreement;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SpecialCommercialAgreementRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.audit.AuditedMutation;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.exception.InvalidStateException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Executes reviewed start retries and fixed-term completion instructions idempotently. */
@Service
@RequiredArgsConstructor
public class SpecialAgreementLifecycleService {
    private final SpecialCommercialAgreementRepository agreements;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionChangeOperationRepository operations;
    private final PlanRepository plans;
    private final SubscriptionCheckoutService checkouts;
    private final SubscriptionChangeActivationService activation;
    private final SubscriptionLifecycleManager subscriptionLifecycle;
    private final SubscriptionPeriodCalculator periods;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Transactional
    @AuditedMutation(
            action = "platform.client.subscription.special_agreement.process_due",
            resourceType = "SPECIAL_COMMERCIAL_AGREEMENT_BATCH")
    public void processDue(Instant cutoff) {
        for (SpecialCommercialAgreement agreement : agreements.findDueForCompletion(
                SpecialAgreementStatus.ACTIVE, cutoff)) {
            completeOrAttend(agreement, cutoff);
        }
    }

    public void retry(SpecialCommercialAgreement agreement, UUID actorUserId, String reason) {
        if (agreement.getStatus() != SpecialAgreementStatus.NEEDS_ATTENTION) {
            throw new InvalidStateException("Only an agreement needing attention can be retried.");
        }
        if (agreement.getAttentionStage() == SpecialAgreementAttentionStage.START) {
            retryStart(agreement);
            return;
        }
        if (agreement.getEndInstruction() == SpecialAgreementEndInstruction.MANUAL_REVIEW) {
            throw new InvalidStateException(
                    "Manual review requires a new reviewed subscription decision, not a retry.");
        }
        agreement.setStatus(SpecialAgreementStatus.ACTIVE);
        agreement.setAttentionStage(null);
        agreement.setAttentionReason(null);
        agreements.saveAndFlush(agreement);
        completeOrAttend(agreement, clock.instant());
    }

    public void resolveManualReview(
            SpecialCommercialAgreement agreement, UUID actorUserId, String reason) {
        if (agreement.getStatus() != SpecialAgreementStatus.NEEDS_ATTENTION
                || agreement.getAttentionStage() != SpecialAgreementAttentionStage.END
                || agreement.getEndInstruction() != SpecialAgreementEndInstruction.MANUAL_REVIEW) {
            throw new InvalidStateException(
                    "Only a manual-review agreement can be closed through this operation.");
        }
        Subscription result = agreement.getResultSubscription();
        if (result == null || (result.getStatus() == SubscriptionStatus.SUSPENDED
                && result.getSuspensionCause()
                == com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause.AGREEMENT_REVIEW)) {
            throw new InvalidStateException(
                    "Apply a reviewed subscription decision before closing the manual review.");
        }
        complete(agreement, clock.instant());
    }

    private void retryStart(SpecialCommercialAgreement agreement) {
        SubscriptionChangeOperation operation = agreement.getChangeOperation();
        var checkout = operation.getCheckout();
        if (agreement.agreedMoney().amount().signum() > 0
                && (checkout == null || checkout.getStatus() != SubscriptionCheckoutStatus.CONFIRMED)) {
            agreement.setStatus(SpecialAgreementStatus.AWAITING_SETTLEMENT);
            agreement.setAttentionStage(null);
            agreement.setAttentionReason(null);
            agreements.save(agreement);
            return;
        }
        operation.setStatus(SubscriptionChangeStatus.PENDING);
        operation.setAttentionReason(null);
        operations.save(operation);
        agreement.setStatus(SpecialAgreementStatus.SCHEDULED);
        agreement.setAttentionStage(null);
        agreement.setAttentionReason(null);
        agreements.saveAndFlush(agreement);
        if (!operation.getEffectiveAt().isAfter(clock.instant())) {
            activation.activate(operation, operation.getEffectiveAt());
        }
    }

    private void completeOrAttend(SpecialCommercialAgreement agreement, Instant at) {
        String failure = null;
        try {
            Subscription current = subscriptions.findCurrentForLifecycleUpdate(
                            agreement.getResultSubscription().getId(), agreement.getAccount().getId(),
                            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE,
                                    SubscriptionStatus.SUSPENDED))
                    .orElseThrow(() -> new InvalidStateException(
                            "The agreement no longer owns the Account's current subscription."));
            switch (agreement.getEndInstruction()) {
                case CONTINUE_REVIEWED_TERMS -> continueReviewed(agreement, current, at);
                case RESTORE_PREVIOUS_TERMS -> restorePrevious(agreement, current, at);
                case END_ACCESS -> endAccess(agreement, current, at);
                case MANUAL_REVIEW -> requireManualReview(agreement, current, at);
            }
        } catch (RuntimeException exception) {
            failure = exception.getMessage();
            needsAttention(agreement, failure, at);
        }
        recordEndAttempt(agreement, at, failure);
    }

    private void continueReviewed(
            SpecialCommercialAgreement agreement, Subscription current, Instant at) {
        current.setStatus(SubscriptionStatus.ACTIVE);
        current.setSuspensionCause(null);
        current.setSuspendedAt(null);
        current.setSuspendedFromStatus(null);
        current.setSuspensionReason(null);
        current.setCurrentMoney(agreement.followOnMoney());
        subscriptions.save(current);
        complete(agreement, at);
        // The ordinary lifecycle pass runs immediately after this processor and creates either a
        // zero-price recurring period or the normal renewal Invoice/Payment flow.
    }

    private void restorePrevious(
            SpecialCommercialAgreement agreement, Subscription current, Instant at) {
        if (operations.existsByAccountIdAndStatusIn(
                agreement.getAccount().getId(), List.of(
                        SubscriptionChangeStatus.PENDING,
                        SubscriptionChangeStatus.AWAITING_CONFIRMATION))) {
            throw new InvalidStateException(
                    "Another subscription operation is already outstanding.");
        }
        var previous = agreement.getBeforeSnapshot();
        var targetPlan = plans.findByCode(previous.planCode())
                .orElseThrow(() -> new InvalidStateException(
                        "The previous Plan no longer exists."));
        var nextPeriod = periods.recurring(previous.billingCycle(), agreement.getEndsAt());
        SubscriptionChangeOperation operation = new SubscriptionChangeOperation();
        operation.setAccount(agreement.getAccount());
        operation.setSourceSubscription(current);
        operation.setTargetPlan(targetPlan);
        operation.setTiming(SubscriptionChangeTiming.AT_RENEWAL);
        operation.setStatus(agreement.previousMoney().amount().signum() > 0
                ? SubscriptionChangeStatus.AWAITING_CONFIRMATION
                : SubscriptionChangeStatus.PENDING);
        operation.setEffectiveAt(nextPeriod.startsAt());
        operation.setRequestedSelection(selectionFrom(previous));
        operation.setBeforeSnapshot(agreement.getTermSnapshot());
        operation.setTargetSnapshot(previous.withEffectivePeriod(
                nextPeriod.startsAt(), nextPeriod.endsAt()));
        operation.setCommercialPolicyEvaluation(previous.commercialPolicyEvaluation());
        operation.setCommercialOfferEvaluation(previous.offerEvaluation());
        operation.setRequestOrigin(SubscriptionChangeOrigin.SYSTEM);
        operation.setRequestedByUserId(null);
        operation.setRequestReason("Restore reviewed pre-agreement terms");
        operation = operations.saveAndFlush(operation);

        if (agreement.previousMoney().amount().signum() > 0) {
            subscriptionLifecycle.awaitAgreementRestoration(current, at);
            subscriptions.save(current);
            checkouts.initiate(operation, agreement.previousMoney(), null);
        } else {
            activation.activate(operation, operation.getEffectiveAt());
        }
        complete(agreement, at);
    }

    private SubscriptionOverrides selectionFrom(
            com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot snapshot) {
        return new SubscriptionOverrides(
                snapshot.addOns().stream().map(item -> item.code())
                        .collect(java.util.stream.Collectors.toCollection(
                                java.util.LinkedHashSet::new)),
                snapshot.quotaPackages().stream()
                        .map(item -> new QuotaPackageSelection(item.code(), item.quantity()))
                        .toList());
    }

    private void endAccess(
            SpecialCommercialAgreement agreement, Subscription current, Instant at) {
        subscriptionLifecycle.cancelImmediately(current, at);
        subscriptions.save(current);
        complete(agreement, at);
    }

    private void requireManualReview(
            SpecialCommercialAgreement agreement, Subscription current, Instant at) {
        subscriptionLifecycle.suspendForAgreementReview(
                current, at, "Special agreement ended; an operator decision is required.");
        subscriptions.save(current);
        agreement.setStatus(SpecialAgreementStatus.NEEDS_ATTENTION);
        agreement.setAttentionStage(SpecialAgreementAttentionStage.END);
        agreement.setAttentionReason("The reviewed end instruction requires an operator decision.");
        agreements.save(agreement);
    }

    private void complete(SpecialCommercialAgreement agreement, Instant at) {
        agreement.setStatus(SpecialAgreementStatus.COMPLETED);
        agreement.setCompletedAt(at);
        agreement.setAttentionStage(null);
        agreement.setAttentionReason(null);
        agreements.save(agreement);
    }

    private void needsAttention(
            SpecialCommercialAgreement agreement, String reason, Instant at) {
        agreement.setStatus(SpecialAgreementStatus.NEEDS_ATTENTION);
        agreement.setAttentionStage(SpecialAgreementAttentionStage.END);
        agreement.setAttentionReason(reason == null || reason.isBlank()
                ? "Agreement completion could not be applied safely." : reason);
        agreements.save(agreement);
        Subscription current = agreement.getResultSubscription();
        if (current != null && current.getStatus() == SubscriptionStatus.ACTIVE) {
            subscriptionLifecycle.suspendForAgreementReview(
                    current, at, "Special agreement completion requires attention.");
            subscriptions.save(current);
        }
    }

    private void recordEndAttempt(
            SpecialCommercialAgreement agreement, Instant at, String failure) {
        var evidence = new java.util.LinkedHashMap<String, Object>();
        evidence.put("stage", "END");
        evidence.put("outcome", agreement.getStatus().name());
        evidence.put("attemptedAt", at.toString());
        if (failure != null && !failure.isBlank()) evidence.put("reason", failure);
        auditTrail.recordSuccess(
                "platform.client.subscription.special_agreement.end_attempt",
                "SPECIAL_COMMERCIAL_AGREEMENT", agreement.getId(), AuditActorSurface.SYSTEM,
                null, agreement.getAccount().getId(), Map.of(), evidence);
    }
}
