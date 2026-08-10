package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionPeriodStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionPeriod;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionPeriodRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.shared.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleManagerTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPeriodRepository subscriptionPeriodRepository;
    @Mock private SubscriptionPeriodCalculator periodCalculator;
    @Mock private Clock clock;

    @InjectMocks
    private SubscriptionLifecycleManager lifecycleManager;

    @Test
    void dueTrialExpiresAndClosesItsHistoricalPeriod() {
        Subscription trial = subscription(SubscriptionStatus.TRIALING, Money.zero("USD"));
        SubscriptionPeriod open = openPeriod(trial);
        when(clock.instant()).thenReturn(NOW);
        when(subscriptionRepository.findDueUsableForUpdate(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING), NOW))
                .thenReturn(List.of(trial));
        when(subscriptionPeriodRepository.findBySubscriptionIdAndStatus(
                trial.getId(), SubscriptionPeriodStatus.OPEN)).thenReturn(Optional.of(open));

        lifecycleManager.processDueSubscriptions();

        assertThat(trial.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(open.getStatus()).isEqualTo(SubscriptionPeriodStatus.TRIAL_EXPIRED);
        assertThat(open.getClosedAt()).isEqualTo(NOW);
        verify(subscriptionRepository).save(trial);
    }

    @Test
    void duePaidSubscriptionBecomesPastDueWithoutPretendingPaymentSucceeded() {
        Subscription paid = subscription(SubscriptionStatus.ACTIVE, Money.of(new BigDecimal("20.00"), "USD"));
        SubscriptionPeriod open = openPeriod(paid);
        when(clock.instant()).thenReturn(NOW);
        when(subscriptionRepository.findDueUsableForUpdate(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING), NOW))
                .thenReturn(List.of(paid));
        when(subscriptionPeriodRepository.findBySubscriptionIdAndStatus(
                paid.getId(), SubscriptionPeriodStatus.OPEN)).thenReturn(Optional.of(open));

        lifecycleManager.processDueSubscriptions();

        assertThat(paid.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(open.getStatus()).isEqualTo(SubscriptionPeriodStatus.PAYMENT_DUE);
        assertThat(open.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void dueFreeSubscriptionRenewsAndKeepsTheCompletedPeriod() {
        Subscription free = subscription(SubscriptionStatus.ACTIVE, Money.zero("USD"));
        SubscriptionPeriod completed = openPeriod(free);
        Instant nextEnd = free.getCurrentPeriodEnd().plusSeconds(2_592_000);
        when(clock.instant()).thenReturn(NOW);
        when(subscriptionRepository.findDueUsableForUpdate(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING), NOW))
                .thenReturn(List.of(free));
        when(subscriptionPeriodRepository.findBySubscriptionIdAndStatus(
                free.getId(), SubscriptionPeriodStatus.OPEN)).thenReturn(Optional.of(completed));
        when(periodCalculator.recurring(BillingCycle.MONTHLY, free.getCurrentPeriodEnd()))
                .thenReturn(new SubscriptionPeriodCalculator.Period(free.getCurrentPeriodEnd(), nextEnd));

        lifecycleManager.processDueSubscriptions();

        assertThat(free.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(free.getCurrentPeriodStart()).isEqualTo(NOW.minusSeconds(60));
        assertThat(free.getCurrentPeriodEnd()).isEqualTo(nextEnd);
        assertThat(completed.getStatus()).isEqualTo(SubscriptionPeriodStatus.COMPLETED);
        verify(subscriptionPeriodRepository, times(2)).save(any(SubscriptionPeriod.class));
    }

    private Subscription subscription(SubscriptionStatus status, Money money) {
        Plan plan = new Plan();
        plan.setCode("FREE");
        plan.setName("Free");
        plan.setBillingCycle(BillingCycle.MONTHLY);
        plan.setCurrencyCode("USD");
        plan.setPrice(BigDecimal.ZERO);

        Subscription subscription = new Subscription();
        ReflectionTestUtils.setField(subscription, "id", UUID.randomUUID());
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(NOW.minusSeconds(2_592_060));
        subscription.setCurrentPeriodEnd(NOW.minusSeconds(60));
        subscription.setEntitlementSnapshot(SubscriptionEntitlementSnapshot.empty(
                plan.getCode(), plan.getPrice(), plan.getCurrencyCode(), plan.getBillingCycle())
                .withEffectivePeriod(subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd()));
        subscription.setCurrentMoney(money);
        return subscription;
    }

    private SubscriptionPeriod openPeriod(Subscription subscription) {
        SubscriptionPeriod period = new SubscriptionPeriod();
        period.setSubscription(subscription);
        period.setStartsAt(subscription.getCurrentPeriodStart());
        period.setEndsAt(subscription.getCurrentPeriodEnd());
        period.setStatus(SubscriptionPeriodStatus.OPEN);
        period.setEntitlementSnapshot(subscription.getEntitlementSnapshot());
        return period;
    }
}
