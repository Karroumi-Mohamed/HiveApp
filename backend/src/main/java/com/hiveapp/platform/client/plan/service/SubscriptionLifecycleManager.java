package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionPeriodStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionPeriod;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionPeriodRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.shared.audit.AuditedMutation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleManager {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPeriodRepository subscriptionPeriodRepository;
    private final SubscriptionPeriodCalculator periodCalculator;
    private final SubscriptionBillingRenewalService billingRenewals;
    private final Clock clock;

    @Value("${hiveapp.subscriptions.renewal-grace:PT72H}")
    private Duration renewalGrace = Duration.ofHours(72);

    public void initialize(
            Subscription subscription,
            SubscriptionStatus status,
            SubscriptionPeriodCalculator.Period period
    ) {
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(period.startsAt());
        subscription.setCurrentPeriodEnd(period.endsAt());
        subscription.setCancelAtPeriodEnd(false);
        subscription.setPastDueAt(null);
        subscription.setGraceEndsAt(null);
        subscription.setSuspendedAt(null);
        subscription.setSuspensionCause(null);
        subscription.setSuspendedFromStatus(null);
        subscription.setSuspensionReason(null);
        subscription.setEntitlementSnapshot(
                subscription.getEntitlementSnapshot().withEffectivePeriod(period.startsAt(), period.endsAt()));
    }

    public void recordOpenPeriod(Subscription subscription) {
        SubscriptionPeriod period = new SubscriptionPeriod();
        period.setSubscription(subscription);
        period.setStartsAt(subscription.getCurrentPeriodStart());
        period.setEndsAt(subscription.getCurrentPeriodEnd());
        period.setStatus(SubscriptionPeriodStatus.OPEN);
        period.setEntitlementSnapshot(subscription.getEntitlementSnapshot());
        subscriptionPeriodRepository.save(period);
    }

    public void closeForReplacement(Subscription subscription) {
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        closeOpenPeriod(subscription, SubscriptionPeriodStatus.CANCELLED, clock.instant());
    }

    public void cancelImmediately(Subscription subscription, Instant cancelledAt) {
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelAtPeriodEnd(false);
        closeOpenPeriod(subscription, SubscriptionPeriodStatus.CANCELLED, cancelledAt);
    }

    public void suspendForAgreementReview(
            Subscription subscription, Instant at, String reason) {
        closeOpenPeriod(subscription, SubscriptionPeriodStatus.COMPLETED, at);
        subscription.setStatus(SubscriptionStatus.SUSPENDED);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setPastDueAt(null);
        subscription.setGraceEndsAt(null);
        subscription.setSuspendedAt(at);
        subscription.setSuspensionCause(SubscriptionSuspensionCause.AGREEMENT_REVIEW);
        subscription.setSuspendedFromStatus(SubscriptionStatus.ACTIVE);
        subscription.setSuspensionReason(reason);
    }

    public void awaitAgreementRestoration(
            Subscription subscription, Instant at) {
        closeOpenPeriod(subscription, SubscriptionPeriodStatus.PAYMENT_DUE, at);
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscription.setPastDueAt(at);
        subscription.setGraceEndsAt(graceDeadline(at));
        subscription.setSuspendedAt(null);
        subscription.setSuspensionCause(null);
        subscription.setSuspendedFromStatus(null);
        subscription.setSuspensionReason(null);
    }

    @Transactional
    @AuditedMutation(
            action = "platform.client.subscription.lifecycle.process_due",
            resourceType = "SUBSCRIPTION_BATCH")
    public void processDueSubscriptions() {
        Instant now = clock.instant();
        List<Subscription> due = subscriptionRepository.findDueUsableForUpdate(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING), now);
        due.forEach(subscription -> processDue(subscription, now));
        subscriptionRepository.findGraceExpiredForUpdate(SubscriptionStatus.PAST_DUE, now)
                .forEach(subscription -> suspendAfterGrace(subscription, now));
        subscriptionRepository.findExpiredSuspensionsForUpdate(
                        SubscriptionStatus.SUSPENDED, SubscriptionSuspensionCause.OPERATOR, now)
                .forEach(subscription -> closeExpiredOperatorSuspension(subscription, now));
    }

    private void processDue(Subscription subscription, Instant now) {
        if (subscription.getStatus() == SubscriptionStatus.TRIALING) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            closeOpenPeriod(subscription, SubscriptionPeriodStatus.TRIAL_EXPIRED, now);
        } else if (subscription.isCancelAtPeriodEnd()) {
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            closeOpenPeriod(subscription, SubscriptionPeriodStatus.CANCELLED, now);
        } else if (subscription.currentMoney() != null
                && subscription.currentMoney().amount().signum() == 0) {
            closeOpenPeriod(subscription, SubscriptionPeriodStatus.COMPLETED, now);
            renewZeroPricedSubscription(subscription);
        } else {
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            subscription.setPastDueAt(now);
            subscription.setGraceEndsAt(graceDeadline(now));
            subscription.setSuspendedAt(null);
            closeOpenPeriod(subscription, SubscriptionPeriodStatus.PAYMENT_DUE, now);
            billingRenewals.ensureCharge(subscription);
        }
        subscriptionRepository.save(subscription);
    }

    private Instant graceDeadline(Instant pastDueAt) {
        if (renewalGrace == null || renewalGrace.isZero() || renewalGrace.isNegative()) {
            throw new IllegalStateException("Renewal grace must be a positive duration");
        }
        return pastDueAt.plus(renewalGrace);
    }

    private void suspendAfterGrace(Subscription subscription, Instant now) {
        subscription.setStatus(SubscriptionStatus.SUSPENDED);
        subscription.setSuspendedAt(now);
        subscription.setSuspensionCause(
                com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause.COLLECTION);
        subscription.setSuspendedFromStatus(SubscriptionStatus.PAST_DUE);
        subscription.setSuspensionReason("Renewal collection grace expired");
        subscriptionRepository.save(subscription);
    }

    private void closeExpiredOperatorSuspension(Subscription subscription, Instant now) {
        if (subscription.getSuspendedFromStatus() == SubscriptionStatus.TRIALING) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            closeOpenPeriod(subscription, SubscriptionPeriodStatus.TRIAL_EXPIRED, now);
        } else {
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            closeOpenPeriod(subscription, SubscriptionPeriodStatus.CANCELLED, now);
        }
        subscriptionRepository.save(subscription);
    }

    private void renewZeroPricedSubscription(Subscription current) {
        var next = periodCalculator.recurring(
                current.getEntitlementSnapshot().billingCycle(), current.getCurrentPeriodEnd());
        current.setCurrentPeriodStart(next.startsAt());
        current.setCurrentPeriodEnd(next.endsAt());
        current.setEntitlementSnapshot(
                current.getEntitlementSnapshot().withEffectivePeriod(next.startsAt(), next.endsAt()));
        current.setCancelAtPeriodEnd(false);
        recordOpenPeriod(current);
    }

    private void closeOpenPeriod(
            Subscription subscription,
            SubscriptionPeriodStatus status,
            Instant closedAt
    ) {
        subscriptionPeriodRepository.findBySubscriptionIdAndStatus(
                        subscription.getId(), SubscriptionPeriodStatus.OPEN)
                .ifPresent(period -> {
                    period.setStatus(status);
                    period.setClosedAt(closedAt);
                    subscriptionPeriodRepository.save(period);
                });
    }
}
