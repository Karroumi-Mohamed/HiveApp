package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingLineType;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentKind;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot;
import com.hiveapp.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingLedgerServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T08:00:00Z");

    @Mock private BillingInvoiceRepository invoices;
    @Mock private BillingPaymentAttemptRepository payments;
    @Mock private BillingOutboxCommandRepository outbox;

    private BillingLedgerService service;

    @BeforeEach
    void setUp() {
        service = new BillingLedgerService(
                invoices, payments, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void itemizesAcceptedSnapshotAndMakesAdjustmentExplicit() {
        SubscriptionCheckout checkout = checkout();
        SubscriptionEntitlementSnapshot snapshot = snapshot();
        when(invoices.findByCheckoutId(checkout.getId())).thenReturn(Optional.empty());
        when(invoices.saveAndFlush(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        BillingPaymentAttempt payment = service.invoiceAndQueueCharge(
                checkout, snapshot, Money.of(new BigDecimal("25.00"), "USD"), UUID.randomUUID());

        BillingInvoice invoice = payment.getInvoice();
        assertThat(invoice.getStatus()).isEqualTo(BillingInvoiceStatus.OPEN);
        assertThat(invoice.getInvoiceNumber()).startsWith("INV-20260831-");
        assertThat(invoice.getLines()).extracting(line -> line.getType())
                .containsExactly(
                        BillingLineType.PLAN,
                        BillingLineType.ADD_ON,
                        BillingLineType.QUOTA_PACKAGE,
                        BillingLineType.COMMERCIAL_ADJUSTMENT);
        assertThat(invoice.getLines()).extracting(line -> line.getLineAmount())
                .containsExactly(
                        new BigDecimal("20.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("6.00"),
                        new BigDecimal("-6.00"));
        assertThat(invoice.money()).isEqualTo(Money.of(new BigDecimal("25.00"), "USD"));
        assertThat(payment.getIdempotencyKey())
                .isEqualTo("subscription-charge:" + checkout.getChangeOperation().getId());
        verify(outbox).save(any());
    }

    @Test
    void manualSettlementWritesTrustedPaymentAndSettlesInvoice() {
        SubscriptionCheckout checkout = checkout();
        BillingInvoice invoice = BillingInvoice.open(
                checkout,
                Money.of(new BigDecimal("20.00"), "USD"),
                BillingCycle.MONTHLY,
                NOW,
                NOW.plusSeconds(2_592_000),
                UUID.randomUUID(),
                NOW);
        invoice.addLine(com.hiveapp.platform.client.plan.domain.entity.BillingInvoiceLine.component(
                BillingLineType.PLAN, "PRO", "Pro", 3, UUID.randomUUID(), 1,
                Money.of(new BigDecimal("20.00"), "USD")));
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        UUID operatorId = UUID.randomUUID();
        when(invoices.findByCheckoutId(checkout.getId())).thenReturn(Optional.of(invoice));
        when(invoices.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(payments.findFirstByInvoiceIdAndKindOrderByCreatedAtDesc(
                invoice.getId(), BillingPaymentKind.PROVIDER)).thenReturn(Optional.empty());
        when(payments.findByExternalReference("bank-42")).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        BillingPaymentAttempt payment = service.recordManualSettlement(
                checkout.getId(), operatorId, "bank-42", "Bank transfer reconciled");

        assertThat(payment.isTrustedForSettlement()).isTrue();
        assertThat(payment.getExternalReference()).isEqualTo("bank-42");
        assertThat(payment.getOperatorUserId()).isEqualTo(operatorId);
        assertThat(invoice.getStatus()).isEqualTo(BillingInvoiceStatus.SETTLED);
        assertThat(invoice.getSettledAt()).isEqualTo(NOW);
    }

    @Test
    void manualSettlementCancelsUndispatchedAutomaticCharge() {
        SubscriptionCheckout checkout = checkout();
        BillingInvoice invoice = invoice(checkout);
        BillingPaymentAttempt provider = withId(BillingPaymentAttempt.pendingProvider(
                invoice, "subscription-charge:" + checkout.getChangeOperation().getId()));
        BillingOutboxCommand command = withId(BillingOutboxCommand.pending(
                BillingOutboxOperation.CHARGE,
                provider.getId(),
                provider.getIdempotencyKey(),
                NOW));
        when(invoices.findByCheckoutId(checkout.getId())).thenReturn(Optional.of(invoice));
        when(invoices.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(payments.findFirstByInvoiceIdAndKindOrderByCreatedAtDesc(
                invoice.getId(), BillingPaymentKind.PROVIDER)).thenReturn(Optional.of(provider));
        when(outbox.findByAggregateIdAndOperationForUpdate(
                provider.getId(), BillingOutboxOperation.CHARGE)).thenReturn(Optional.of(command));
        when(payments.findByIdForUpdate(provider.getId())).thenReturn(Optional.of(provider));
        when(payments.findByExternalReference("bank-43")).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        service.recordManualSettlement(
                checkout.getId(), UUID.randomUUID(), "bank-43", "Bank transfer reconciled");

        assertThat(provider.getStatus()).isEqualTo(BillingPaymentStatus.CANCELLED);
        assertThat(command.getStatus()).isEqualTo(BillingOutboxStatus.CANCELLED);
        assertThat(invoice.getStatus()).isEqualTo(BillingInvoiceStatus.SETTLED);
    }

    @Test
    void checkoutCancellationStopsUndispatchedAutomaticCharge() {
        SubscriptionCheckout checkout = checkout();
        BillingInvoice invoice = invoice(checkout);
        BillingPaymentAttempt provider = withId(BillingPaymentAttempt.pendingProvider(
                invoice, "subscription-charge:" + checkout.getChangeOperation().getId()));
        BillingOutboxCommand command = withId(BillingOutboxCommand.pending(
                BillingOutboxOperation.CHARGE,
                provider.getId(),
                provider.getIdempotencyKey(),
                NOW));
        when(invoices.findByCheckoutId(checkout.getId())).thenReturn(Optional.of(invoice));
        when(invoices.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(payments.findFirstByInvoiceIdAndKindOrderByCreatedAtDesc(
                invoice.getId(), BillingPaymentKind.PROVIDER)).thenReturn(Optional.of(provider));
        when(outbox.findByAggregateIdAndOperationForUpdate(
                provider.getId(), BillingOutboxOperation.CHARGE)).thenReturn(Optional.of(command));
        when(payments.findByIdForUpdate(provider.getId())).thenReturn(Optional.of(provider));

        service.cancelForCheckout(checkout.getId());

        assertThat(provider.getStatus()).isEqualTo(BillingPaymentStatus.CANCELLED);
        assertThat(command.getStatus()).isEqualTo(BillingOutboxStatus.CANCELLED);
        assertThat(invoice.getStatus()).isEqualTo(BillingInvoiceStatus.CANCELLED);
    }

    private SubscriptionEntitlementSnapshot snapshot() {
        return new SubscriptionEntitlementSnapshot(
                SubscriptionEntitlementSnapshot.CURRENT_SCHEMA_VERSION,
                "PRO",
                "Pro",
                3,
                new BigDecimal("20.00"),
                "USD",
                BillingCycle.MONTHLY,
                NOW,
                NOW.plusSeconds(2_592_000),
                List.of(),
                List.of(new SubscriptionAddOnSnapshot(
                        "ROLES", "Custom roles", 2, new BigDecimal("5.00"), "USD",
                        BillingCycle.MONTHLY, List.of("platform.roles"), UUID.randomUUID())),
                List.of(new SubscriptionQuotaPackageSnapshot(
                        "MEMBERS_5", "Five members", 4, "platform.staff", "members", 5,
                        2, new BigDecimal("3.00"), "USD", BillingCycle.MONTHLY,
                        UUID.randomUUID())),
                UUID.randomUUID(),
                null,
                null);
    }

    private SubscriptionCheckout checkout() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        SubscriptionChangeOperation operation = new SubscriptionChangeOperation();
        ReflectionTestUtils.setField(operation, "id", UUID.randomUUID());
        operation.setAccount(account);
        operation.setTargetSnapshot(snapshot());
        SubscriptionCheckout checkout = new SubscriptionCheckout();
        ReflectionTestUtils.setField(checkout, "id", UUID.randomUUID());
        checkout.setAccount(account);
        checkout.setChangeOperation(operation);
        checkout.setMoney(Money.of(new BigDecimal("25.00"), "USD"));
        checkout.setRequestedByUserId(UUID.randomUUID());
        return checkout;
    }

    private BillingInvoice invoice(SubscriptionCheckout checkout) {
        BillingInvoice invoice = BillingInvoice.open(
                checkout,
                Money.of(new BigDecimal("20.00"), "USD"),
                BillingCycle.MONTHLY,
                NOW,
                NOW.plusSeconds(2_592_000),
                UUID.randomUUID(),
                NOW);
        invoice.addLine(com.hiveapp.platform.client.plan.domain.entity.BillingInvoiceLine.component(
                BillingLineType.PLAN, "PRO", "Pro", 3, UUID.randomUUID(), 1,
                Money.of(new BigDecimal("20.00"), "USD")));
        return withId(invoice);
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
