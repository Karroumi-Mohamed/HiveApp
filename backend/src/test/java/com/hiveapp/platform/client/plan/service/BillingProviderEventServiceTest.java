package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingLineType;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingProviderEventStatus;
import com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoiceLine;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import com.hiveapp.platform.client.plan.domain.entity.BillingProviderEvent;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.model.VerifiedBillingProviderEvent;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingProviderEventRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingRefundRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.shared.exception.IdempotencyConflictException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingProviderEventServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");
    private static final String KEY = "subscription-charge:test";

    @Mock private BillingOutboxCommandRepository commands;
    @Mock private BillingPaymentAttemptRepository payments;
    @Mock private BillingProviderEventRepository events;
    @Mock private BillingRefundRepository refunds;
    @Mock private BillingInvoiceRepository invoices;
    @Mock private SubscriptionCheckoutRepository checkouts;
    @Mock private SubscriptionChangeOperationRepository operations;
    @Mock private SubscriptionChangeActivationService activation;

    private BillingOutboxTransactionService transactions;

    @BeforeEach
    void setUp() {
        transactions = new BillingOutboxTransactionService(
                commands, payments, events, refunds, invoices, checkouts, operations,
                activation, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void trustedSuccessRepairsTransportFailureAndSettlesExactlyMatchedInvoice() {
        Fixture fixture = fixture(new BigDecimal("20.00"));
        fixture.payment.recordTransportFailure("Transport timeout.", NOW.minusSeconds(60));
        fixture.command.claim(NOW.minusSeconds(120));
        fixture.command.fail("Transport timeout.", NOW.minusSeconds(60));
        fixture.checkout.setStatus(SubscriptionCheckoutStatus.FAILED);
        fixture.operation.setStatus(SubscriptionChangeStatus.NEEDS_ATTENTION);
        fixture.operation.setAttentionReason("Transport timeout.");
        BillingProviderEvent event = event(new BigDecimal("20.00"), PaymentStatus.SUCCESS, true);
        wireBase(fixture, event);
        wireFinancialRecords(fixture);

        transactions.reconcileProviderEvent(event.getId());

        assertThat(event.getProcessingStatus()).isEqualTo(BillingProviderEventStatus.APPLIED);
        assertThat(fixture.command.getStatus()).isEqualTo(BillingOutboxStatus.PROCESSED);
        assertThat(fixture.payment.getStatus()).isEqualTo(BillingPaymentStatus.SUCCEEDED);
        assertThat(fixture.payment.isTrustedForSettlement()).isTrue();
        assertThat(fixture.invoice.getStatus()).isEqualTo(BillingInvoiceStatus.SETTLED);
        assertThat(fixture.checkout.getConfirmationSource())
                .isEqualTo(CheckoutConfirmationSource.TRUSTED_PROVIDER);
        verify(activation).activate(fixture.operation, NOW);
    }

    @Test
    void amountMismatchIsRetainedWithoutChangingFinancialState() {
        Fixture fixture = fixture(new BigDecimal("20.00"));
        BillingProviderEvent event = event(new BigDecimal("19.00"), PaymentStatus.SUCCESS, true);
        wireBase(fixture, event);
        when(payments.findByIdForUpdate(fixture.payment.getId()))
                .thenReturn(Optional.of(fixture.payment));

        transactions.reconcileProviderEvent(event.getId());

        assertThat(event.getProcessingStatus()).isEqualTo(BillingProviderEventStatus.MISMATCHED);
        assertThat(event.getAttentionReason()).contains("amount or currency");
        assertThat(fixture.payment.getStatus()).isEqualTo(BillingPaymentStatus.PENDING);
        assertThat(fixture.invoice.getStatus()).isEqualTo(BillingInvoiceStatus.OPEN);
        assertThat(fixture.command.getStatus()).isEqualTo(BillingOutboxStatus.PENDING);
        verify(activation, never()).activate(fixture.operation, event.getOccurredAt());
    }

    @Test
    void cancelledCommandCannotBeResurrectedByAProviderEvent() {
        Fixture fixture = fixture(new BigDecimal("20.00"));
        fixture.command.cancel("Checkout cancelled before dispatch.", NOW.minusSeconds(60));
        BillingProviderEvent event = event(new BigDecimal("20.00"), PaymentStatus.SUCCESS, true);
        wireBase(fixture, event);

        transactions.reconcileProviderEvent(event.getId());

        assertThat(event.getProcessingStatus()).isEqualTo(BillingProviderEventStatus.MISMATCHED);
        assertThat(event.getAttentionReason()).contains("cancelled before dispatch");
        assertThat(fixture.payment.getStatus()).isEqualTo(BillingPaymentStatus.PENDING);
    }

    @Test
    void sameProviderEventIsIdempotentButChangedPayloadIsAConflict() {
        BillingProviderEventRepository repository = org.mockito.Mockito.mock(
                BillingProviderEventRepository.class);
        BillingProviderEventReceiptService receipts = new BillingProviderEventReceiptService(repository);
        VerifiedBillingProviderEvent evidence = evidence(
                new BigDecimal("20.00"), PaymentStatus.SUCCESS, true, "a".repeat(64));
        BillingProviderEvent existing = withId(BillingProviderEvent.received(evidence));
        when(repository.findByProviderAndEventId("stripe", "evt-1"))
                .thenReturn(Optional.of(existing));

        var duplicate = receipts.record(evidence);

        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.id()).isEqualTo(existing.getId());
        assertThatThrownBy(() -> receipts.record(evidence(
                new BigDecimal("20.00"), PaymentStatus.SUCCESS, true, "b".repeat(64))))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    private void wireBase(Fixture fixture, BillingProviderEvent event) {
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(commands.findByIdempotencyKeyForUpdate(KEY)).thenReturn(Optional.of(fixture.command));
    }

    private void wireFinancialRecords(Fixture fixture) {
        when(payments.findByIdForUpdate(fixture.payment.getId())).thenReturn(Optional.of(fixture.payment));
        when(payments.findByExternalReference("provider-charge-1")).thenReturn(Optional.empty());
        when(invoices.findByIdForUpdate(fixture.invoice.getId())).thenReturn(Optional.of(fixture.invoice));
        when(checkouts.findByIdForUpdate(fixture.checkout.getId())).thenReturn(Optional.of(fixture.checkout));
    }

    private Fixture fixture(BigDecimal amount) {
        Account account = withId(new Account());
        SubscriptionChangeOperation operation = withId(new SubscriptionChangeOperation());
        operation.setAccount(account);
        operation.setTiming(SubscriptionChangeTiming.IMMEDIATE);
        operation.setEffectiveAt(NOW.minusSeconds(60));
        operation.setStatus(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        SubscriptionCheckout checkout = withId(new SubscriptionCheckout());
        checkout.setAccount(account);
        checkout.setChangeOperation(operation);
        checkout.setMoney(Money.of(amount, "USD"));
        checkout.setRequestedByUserId(UUID.randomUUID());
        BillingInvoice invoice = withId(BillingInvoice.open(
                checkout, Money.of(amount, "USD"), BillingCycle.MONTHLY,
                NOW, NOW.plusSeconds(2_592_000), UUID.randomUUID(), NOW));
        invoice.addLine(BillingInvoiceLine.component(
                BillingLineType.PLAN, "PRO", "Pro", 1, UUID.randomUUID(), 1,
                Money.of(amount, "USD")));
        BillingPaymentAttempt payment = withId(BillingPaymentAttempt.pendingProvider(invoice, KEY));
        BillingOutboxCommand command = withId(BillingOutboxCommand.pending(
                BillingOutboxOperation.CHARGE, payment.getId(), KEY, NOW.minusSeconds(180)));
        return new Fixture(operation, checkout, invoice, payment, command);
    }

    private BillingProviderEvent event(
            BigDecimal amount,
            PaymentStatus status,
            boolean trusted
    ) {
        return withId(BillingProviderEvent.received(evidence(
                amount, status, trusted, "a".repeat(64))));
    }

    private VerifiedBillingProviderEvent evidence(
            BigDecimal amount,
            PaymentStatus status,
            boolean trusted,
            String digest
    ) {
        return new VerifiedBillingProviderEvent(
                "stripe", "evt-1", BillingOutboxOperation.CHARGE, status,
                amount, "USD", KEY, status == PaymentStatus.SUCCESS ? "provider-charge-1" : null,
                status == PaymentStatus.FAILED ? "Card declined." : null,
                NOW.minusSeconds(30), digest, trusted);
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    private record Fixture(
            SubscriptionChangeOperation operation,
            SubscriptionCheckout checkout,
            BillingInvoice invoice,
            BillingPaymentAttempt payment,
            BillingOutboxCommand command
    ) {}
}
