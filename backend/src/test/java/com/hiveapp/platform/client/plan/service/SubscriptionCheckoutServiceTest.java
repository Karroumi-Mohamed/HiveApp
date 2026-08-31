package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCheckoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock private SubscriptionCheckoutRepository checkoutRepository;
    @Mock private SubscriptionChangeOperationRepository operationRepository;
    @Mock private SubscriptionChangeActivationService activationService;
    @Mock private BillingLedgerService billingLedgerService;
    @Mock private Clock clock;

    private SubscriptionCheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        checkoutService = new SubscriptionCheckoutService(
                checkoutRepository, operationRepository, activationService, billingLedgerService, clock);
    }

    @Test
    void initiationPersistsCheckoutThenQueuesDurableBillingWithoutActivating() {
        SubscriptionChangeOperation operation = operation(SubscriptionChangeTiming.IMMEDIATE, NOW);
        when(checkoutRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionCheckout checkout = checkoutService.initiate(
                operation, Money.of(new BigDecimal("29.99"), "USD"), UUID.randomUUID());

        assertThat(checkout.getStatus()).isEqualTo(SubscriptionCheckoutStatus.PENDING_CONFIRMATION);
        assertThat(operation.getStatus()).isEqualTo(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        verify(billingLedgerService).invoiceAndQueueCharge(
                checkout, operation.getTargetSnapshot(), Money.of(new BigDecimal("29.99"), "USD"),
                checkout.getRequestedByUserId());
        verify(activationService, never()).activate(any(), any());
    }

    @Test
    void manualImmediateConfirmationStoresEvidenceAndRunsLockedActivation() {
        SubscriptionChangeOperation operation = operation(SubscriptionChangeTiming.IMMEDIATE, NOW);
        SubscriptionCheckout checkout = checkout(operation);
        UUID operatorId = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
        when(checkoutRepository.findById(checkout.getId())).thenReturn(Optional.of(checkout));
        when(checkoutRepository.findByIdForUpdate(checkout.getId())).thenReturn(Optional.of(checkout));
        when(activationService.activate(operation, NOW)).thenReturn(operation);

        SubscriptionCheckout result = checkoutService.confirmManual(
                checkout.getId(), operatorId, "manual-123", "External contract confirmed");

        assertThat(result.getStatus()).isEqualTo(SubscriptionCheckoutStatus.CONFIRMED);
        assertThat(result.getConfirmationSource()).isEqualTo(CheckoutConfirmationSource.MANUAL_OPERATOR);
        assertThat(result.getConfirmationReference()).isEqualTo("manual-123");
        assertThat(result.getConfirmedByUserId()).isEqualTo(operatorId);
        verify(activationService).activate(operation, NOW);
        verify(billingLedgerService).recordManualSettlement(
                checkout.getId(), operatorId, "manual-123", "External contract confirmed");
    }

    @Test
    void confirmedRenewalWaitsForItsEffectiveDate() {
        Instant renewal = NOW.plusSeconds(86_400);
        SubscriptionChangeOperation operation = operation(SubscriptionChangeTiming.AT_RENEWAL, renewal);
        SubscriptionCheckout checkout = checkout(operation);
        when(clock.instant()).thenReturn(NOW);
        when(checkoutRepository.findById(checkout.getId())).thenReturn(Optional.of(checkout));
        when(checkoutRepository.findByIdForUpdate(checkout.getId())).thenReturn(Optional.of(checkout));

        checkoutService.confirmManual(
                checkout.getId(), UUID.randomUUID(), "renewal-123", "Renewal contract confirmed");

        assertThat(operation.getStatus()).isEqualTo(SubscriptionChangeStatus.PENDING);
        verify(operationRepository).save(operation);
        verify(activationService, never()).activate(any(), any());
    }

    @Test
    void manualSettlementRecoversFailedCheckoutAndOperation() {
        SubscriptionChangeOperation operation = operation(SubscriptionChangeTiming.IMMEDIATE, NOW);
        operation.setStatus(SubscriptionChangeStatus.NEEDS_ATTENTION);
        operation.setAttentionReason("Card declined");
        SubscriptionCheckout checkout = checkout(operation);
        checkout.setStatus(SubscriptionCheckoutStatus.FAILED);
        UUID operatorId = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
        when(checkoutRepository.findById(checkout.getId())).thenReturn(Optional.of(checkout));
        when(checkoutRepository.findByIdForUpdate(checkout.getId())).thenReturn(Optional.of(checkout));
        when(activationService.activate(operation, NOW)).thenReturn(operation);

        SubscriptionCheckout result = checkoutService.confirmManual(
                checkout.getId(), operatorId, "wire-900", "Wire transfer received");

        assertThat(result.getStatus()).isEqualTo(SubscriptionCheckoutStatus.CONFIRMED);
        assertThat(operation.getAttentionReason()).isNull();
        verify(billingLedgerService).recordManualSettlement(
                checkout.getId(), operatorId, "wire-900", "Wire transfer received");
        verify(activationService).activate(operation, NOW);
    }

    private SubscriptionChangeOperation operation(SubscriptionChangeTiming timing, Instant effectiveAt) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        Plan plan = new Plan();
        plan.setCode("PRO");
        plan.setName("Pro");
        plan.setBillingCycle(BillingCycle.MONTHLY);

        SubscriptionChangeOperation operation = new SubscriptionChangeOperation();
        ReflectionTestUtils.setField(operation, "id", UUID.randomUUID());
        operation.setAccount(account);
        operation.setTargetPlan(plan);
        operation.setTiming(timing);
        operation.setEffectiveAt(effectiveAt);
        operation.setStatus(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        return operation;
    }

    private SubscriptionCheckout checkout(SubscriptionChangeOperation operation) {
        SubscriptionCheckout checkout = new SubscriptionCheckout();
        ReflectionTestUtils.setField(checkout, "id", UUID.randomUUID());
        checkout.setAccount(operation.getAccount());
        checkout.setChangeOperation(operation);
        checkout.setStatus(SubscriptionCheckoutStatus.PENDING_CONFIRMATION);
        checkout.setMoney(Money.of(new BigDecimal("29.99"), "USD"));
        checkout.setRequestedByUserId(UUID.randomUUID());
        operation.setCheckout(checkout);
        return checkout;
    }
}
