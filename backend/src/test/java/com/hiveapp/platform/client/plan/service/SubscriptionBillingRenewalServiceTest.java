package com.hiveapp.platform.client.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubscriptionBillingRenewalServiceTest {
    private static final Instant PERIOD_END = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NEXT_END = Instant.parse("2026-10-01T00:00:00Z");

    @Mock private SubscriptionChangeOperationRepository operations;
    @Mock private SubscriptionCheckoutRepository checkouts;
    @Mock private SubscriptionPeriodCalculator periods;
    @Mock private SubscriptionSnapshotReader snapshots;
    @Mock private BillingLedgerService ledger;
    @InjectMocks private SubscriptionBillingRenewalService service;

    @Test
    void createsOneSystemOwnedSameTermsChargeForTheNextPeriod() {
        Subscription subscription = paidSubscription();
        when(operations.findTopByAccountIdAndStatusIn(
                subscription.getAccount().getId(), outstanding())).thenReturn(Optional.empty());
        when(snapshots.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(subscription.getEntitlementSnapshot()));
        when(periods.recurring(BillingCycle.MONTHLY, PERIOD_END))
                .thenReturn(new SubscriptionPeriodCalculator.Period(PERIOD_END, NEXT_END));
        when(operations.saveAndFlush(any())).thenAnswer(invocation -> {
            SubscriptionChangeOperation operation = invocation.getArgument(0);
            ReflectionTestUtils.setField(operation, "id", UUID.randomUUID());
            return operation;
        });
        when(checkouts.saveAndFlush(any())).thenAnswer(invocation -> {
            SubscriptionCheckout checkout = invocation.getArgument(0);
            ReflectionTestUtils.setField(checkout, "id", UUID.randomUUID());
            return checkout;
        });

        SubscriptionChangeOperation operation = service.ensureCharge(subscription);

        assertThat(operation.getRequestOrigin()).isEqualTo(SubscriptionChangeOrigin.SYSTEM);
        assertThat(operation.getRequestedByUserId()).isNull();
        assertThat(operation.getTiming()).isEqualTo(SubscriptionChangeTiming.AT_RENEWAL);
        assertThat(operation.getStatus()).isEqualTo(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        assertThat(operation.getEffectiveAt()).isEqualTo(PERIOD_END);
        assertThat(operation.getBeforeSnapshot().effectiveUntil()).isEqualTo(PERIOD_END);
        assertThat(operation.getTargetSnapshot().effectiveFrom()).isEqualTo(PERIOD_END);
        assertThat(operation.getTargetSnapshot().effectiveUntil()).isEqualTo(NEXT_END);
        assertThat(operation.getRequestedSelection()).isEqualTo(subscription.getCustomOverrides());

        ArgumentCaptor<SubscriptionCheckout> checkout = ArgumentCaptor.forClass(SubscriptionCheckout.class);
        verify(checkouts).saveAndFlush(checkout.capture());
        assertThat(checkout.getValue().getRequestedByUserId()).isNull();
        verify(ledger).invoiceAndQueueCharge(
                checkout.getValue(), operation.getTargetSnapshot(), subscription.currentMoney(), null);
    }

    @Test
    void anExistingExplicitOperationWinsOverDefaultRenewal() {
        Subscription subscription = paidSubscription();
        SubscriptionChangeOperation existing = new SubscriptionChangeOperation();
        when(operations.findTopByAccountIdAndStatusIn(
                subscription.getAccount().getId(), outstanding())).thenReturn(Optional.of(existing));

        assertThat(service.ensureCharge(subscription)).isSameAs(existing);

        verify(checkouts, never()).saveAndFlush(any());
        verify(ledger, never()).invoiceAndQueueCharge(any(), any(), any(), isNull());
    }

    private Subscription paidSubscription() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        plan.setCode("PRO");
        plan.setName("Pro");
        plan.setMoney(Money.of(new BigDecimal("29.0000"), "USD"));
        plan.setBillingCycle(BillingCycle.MONTHLY);

        Subscription subscription = new Subscription();
        ReflectionTestUtils.setField(subscription, "id", UUID.randomUUID());
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(Instant.parse("2026-08-01T00:00:00Z"));
        subscription.setCurrentPeriodEnd(PERIOD_END);
        subscription.setCustomOverrides(SubscriptionOverrides.empty());
        subscription.setEntitlementSnapshot(SubscriptionEntitlementSnapshot.empty(
                "PRO", new BigDecimal("29.0000"), "USD", BillingCycle.MONTHLY)
                .withEffectivePeriod(subscription.getCurrentPeriodStart(), PERIOD_END));
        subscription.setCurrentMoney(Money.of(new BigDecimal("29.0000"), "USD"));
        return subscription;
    }

    private List<SubscriptionChangeStatus> outstanding() {
        return List.of(
                SubscriptionChangeStatus.PENDING,
                SubscriptionChangeStatus.AWAITING_CONFIRMATION,
                SubscriptionChangeStatus.NEEDS_ATTENTION);
    }
}
