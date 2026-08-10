package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionPeriodStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionPeriod;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionPeriodRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.shared.audit.AuditedMutation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleManager {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPeriodRepository subscriptionPeriodRepository;
    private final SubscriptionPeriodCalculator periodCalculator;
    private final Clock clock;

    public void initialize(
            Subscription subscription,
            SubscriptionStatus status,
            SubscriptionPeriodCalculator.Period period
    ) {
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(period.startsAt());
        subscription.setCurrentPeriodEnd(period.endsAt());
        subscription.setCancelAtPeriodEnd(false);
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

    @Transactional
    @AuditedMutation(
            action = "platform.client.subscription.lifecycle.process_due",
            resourceType = "SUBSCRIPTION_BATCH")
    public void processDueSubscriptions() {
        Instant now = clock.instant();
        List<Subscription> due = subscriptionRepository.findDueUsableForUpdate(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING), now);
        due.forEach(subscription -> processDue(subscription, now));
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
            closeOpenPeriod(subscription, SubscriptionPeriodStatus.PAYMENT_DUE, now);
        }
        subscriptionRepository.save(subscription);
    }

    private void renewZeroPricedSubscription(Subscription current) {
        var next = periodCalculator.recurring(current.getPlan().getBillingCycle(), current.getCurrentPeriodEnd());
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
