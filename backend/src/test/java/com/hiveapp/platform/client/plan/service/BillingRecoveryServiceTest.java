package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingLineType;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentKind;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoiceLine;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.payment.PaymentResult;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T11:00:00Z");

    @Mock private BillingInvoiceRepository invoices;
    @Mock private BillingPaymentAttemptRepository payments;
    @Mock private BillingOutboxCommandRepository outbox;
    @Mock private SubscriptionCheckoutRepository checkouts;
    @Mock private SubscriptionChangeOperationRepository operations;

    private BillingRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new BillingRecoveryService(
                invoices, payments, outbox, checkouts, operations,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ambiguousTransportFailureRequiresProviderNonCaptureEvidence() {
        Fixture fixture = failedFixture(true);
        wire(fixture);

        assertThatThrownBy(() -> service.retryCharge(
                fixture.invoice.getId(), UUID.randomUUID(), "retry-1", "Retry approved",
                "provider-search-42", false))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("non-capture");
    }

    @Test
    void explicitRecoveryCreatesANewAttemptAndRestoresPendingCheckout() {
        Fixture fixture = failedFixture(true);
        wire(fixture);
        when(checkouts.findByIdForUpdate(fixture.checkout.getId()))
                .thenReturn(Optional.of(fixture.checkout));
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        UUID operatorId = UUID.randomUUID();

        BillingPaymentAttempt retry = service.retryCharge(
                fixture.invoice.getId(), operatorId, "retry-2", "Customer requested retry",
                "provider-search-43", true);

        assertThat(retry.getRetryOfPaymentId()).isEqualTo(fixture.previous.getId());
        assertThat(retry.getOperatorUserId()).isEqualTo(operatorId);
        assertThat(retry.getRecoveryReference()).isEqualTo("provider-search-43");
        assertThat(fixture.checkout.getStatus()).isEqualTo(SubscriptionCheckoutStatus.PENDING_CONFIRMATION);
        assertThat(fixture.operation.getStatus()).isEqualTo(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        ArgumentCaptor<BillingOutboxCommand> command = ArgumentCaptor.forClass(BillingOutboxCommand.class);
        verify(outbox).save(command.capture());
        assertThat(command.getValue().getAggregateId()).isEqualTo(retry.getId());
        assertThat(command.getValue().getStatus()).isEqualTo(BillingOutboxStatus.PENDING);
    }

    @Test
    void definitiveProviderDeclineCanBeRetriedWithoutAmbiguityConfirmation() {
        Fixture fixture = failedFixture(false);
        wire(fixture);
        when(checkouts.findByIdForUpdate(fixture.checkout.getId()))
                .thenReturn(Optional.of(fixture.checkout));
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        BillingPaymentAttempt retry = service.retryCharge(
                fixture.invoice.getId(), UUID.randomUUID(), "retry-3", "Use another card",
                "decline-event-17", false);

        assertThat(retry.getRetryOfPaymentId()).isEqualTo(fixture.previous.getId());
    }

    private void wire(Fixture fixture) {
        when(payments.findByIdempotencyKey(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        when(invoices.findDetailedById(fixture.invoice.getId())).thenReturn(Optional.of(fixture.invoice));
        when(payments.findFirstByInvoiceIdAndKindOrderByCreatedAtDesc(
                fixture.invoice.getId(), BillingPaymentKind.PROVIDER))
                .thenReturn(Optional.of(fixture.previous));
        when(outbox.findByAggregateIdAndOperationForUpdate(
                fixture.previous.getId(), BillingOutboxOperation.CHARGE))
                .thenReturn(Optional.of(fixture.command));
        when(payments.findByIdForUpdate(fixture.previous.getId())).thenReturn(Optional.of(fixture.previous));
        when(invoices.findByIdForUpdate(fixture.invoice.getId())).thenReturn(Optional.of(fixture.invoice));
    }

    private Fixture failedFixture(boolean ambiguousTransportFailure) {
        Account account = withId(new Account());
        account.setName("Acme");
        SubscriptionChangeOperation operation = withId(new SubscriptionChangeOperation());
        operation.setAccount(account);
        operation.setStatus(SubscriptionChangeStatus.NEEDS_ATTENTION);
        SubscriptionCheckout checkout = withId(new SubscriptionCheckout());
        checkout.setAccount(account);
        checkout.setChangeOperation(operation);
        checkout.setStatus(SubscriptionCheckoutStatus.FAILED);
        checkout.setMoney(Money.of(new BigDecimal("20.00"), "USD"));
        checkout.setRequestedByUserId(UUID.randomUUID());
        BillingInvoice invoice = withId(BillingInvoice.open(
                checkout, Money.of(new BigDecimal("20.00"), "USD"), BillingCycle.MONTHLY,
                NOW, NOW.plusSeconds(2_592_000), UUID.randomUUID(), NOW));
        invoice.addLine(BillingInvoiceLine.component(
                BillingLineType.PLAN, "PRO", "Pro", 1, UUID.randomUUID(), 1,
                Money.of(new BigDecimal("20.00"), "USD")));
        BillingPaymentAttempt previous = withId(BillingPaymentAttempt.pendingProvider(
                invoice, "original-charge"));
        BillingOutboxCommand command = withId(BillingOutboxCommand.pending(
                BillingOutboxOperation.CHARGE, previous.getId(), "original-charge",
                NOW.minusSeconds(120)));
        command.claim(NOW.minusSeconds(90));
        if (ambiguousTransportFailure) {
            previous.recordTransportFailure("Transport failed.", NOW.minusSeconds(60));
            command.fail("Transport failed.", NOW.minusSeconds(60));
        } else {
            previous.recordProviderResult(
                    new PaymentResult("decline-17", PaymentStatus.FAILED, "Declined."),
                    false, NOW.minusSeconds(60));
            command.processed(NOW.minusSeconds(60));
        }
        return new Fixture(operation, checkout, invoice, previous, command);
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    private record Fixture(
            SubscriptionChangeOperation operation,
            SubscriptionCheckout checkout,
            BillingInvoice invoice,
            BillingPaymentAttempt previous,
            BillingOutboxCommand command
    ) {}
}
