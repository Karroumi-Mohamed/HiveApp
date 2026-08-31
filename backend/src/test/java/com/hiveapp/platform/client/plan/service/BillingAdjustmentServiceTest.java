package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingLineType;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoiceLine;
import com.hiveapp.platform.client.plan.domain.entity.BillingCredit;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import com.hiveapp.platform.client.plan.domain.entity.BillingRefund;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.BillingCreditRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingRefundRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingAdjustmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T08:00:00Z");

    @Mock private BillingPaymentAttemptRepository payments;
    @Mock private BillingRefundRepository refunds;
    @Mock private BillingCreditRepository credits;
    @Mock private BillingInvoiceRepository invoices;
    @Mock private BillingOutboxCommandRepository outbox;

    private BillingAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new BillingAdjustmentService(
                payments, refunds, credits, invoices, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reservesRefundCeilingAndQueuesOneIdempotentProviderCommand() {
        BillingPaymentAttempt payment = settledPayment("100.00");
        String key = "refund-1";
        when(refunds.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(refunds.sumAmountByPaymentIdAndStatusIn(any(), any())).thenReturn(BigDecimal.ZERO);
        when(refunds.saveAndFlush(any())).thenAnswer(invocation -> {
            BillingRefund refund = invocation.getArgument(0);
            ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());
            return refund;
        });

        BillingRefund refund = service.requestRefund(
                payment.getId(), Money.of(new BigDecimal("40.00"), "USD"),
                "Service correction", UUID.randomUUID(), key);

        assertThat(refund.getAmount()).isEqualByComparingTo("40.00");
        verify(outbox).save(any());
    }

    @Test
    void pendingRefundReservationsPreventConcurrentOverRefund() {
        BillingPaymentAttempt payment = settledPayment("100.00");
        when(refunds.findByIdempotencyKey("refund-2")).thenReturn(Optional.empty());
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(refunds.sumAmountByPaymentIdAndStatusIn(any(), any()))
                .thenReturn(new BigDecimal("80.00"));

        assertThatThrownBy(() -> service.requestRefund(
                payment.getId(), Money.of(new BigDecimal("21.00"), "USD"),
                "Too much", UUID.randomUUID(), "refund-2"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("remaining refundable");
    }

    @Test
    void issuedCreditsCannotExceedInvoiceTotal() {
        BillingInvoice invoice = settledPayment("100.00").getInvoice();
        when(invoices.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(credits.sumAmountByInvoiceId(invoice.getId())).thenReturn(new BigDecimal("80.00"));

        assertThatThrownBy(() -> service.issueCredit(
                invoice.getId(), Money.of(new BigDecimal("21.00"), "USD"),
                "Commercial correction", "OPERATOR", UUID.randomUUID(), null))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("remaining Invoice");
    }

    @Test
    void validCreditRetainsOperatorEvidence() {
        BillingInvoice invoice = settledPayment("100.00").getInvoice();
        UUID operatorId = UUID.randomUUID();
        when(invoices.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(credits.sumAmountByInvoiceId(invoice.getId())).thenReturn(new BigDecimal("20.00"));
        when(credits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BillingCredit credit = service.issueCredit(
                invoice.getId(), Money.of(new BigDecimal("30.00"), "USD"),
                "Commercial correction", "OPERATOR", operatorId, "credit-42");

        assertThat(credit.getOperatorUserId()).isEqualTo(operatorId);
        assertThat(credit.getExternalReference()).isEqualTo("credit-42");
    }

    private BillingPaymentAttempt settledPayment(String amount) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        SubscriptionChangeOperation operation = new SubscriptionChangeOperation();
        ReflectionTestUtils.setField(operation, "id", UUID.randomUUID());
        operation.setAccount(account);
        SubscriptionCheckout checkout = new SubscriptionCheckout();
        ReflectionTestUtils.setField(checkout, "id", UUID.randomUUID());
        checkout.setAccount(account);
        checkout.setChangeOperation(operation);
        checkout.setMoney(Money.of(new BigDecimal(amount), "USD"));
        checkout.setRequestedByUserId(UUID.randomUUID());
        BillingInvoice invoice = BillingInvoice.open(
                checkout, Money.of(new BigDecimal(amount), "USD"), BillingCycle.MONTHLY,
                NOW, NOW.plusSeconds(2_592_000), UUID.randomUUID(), NOW);
        invoice.addLine(BillingInvoiceLine.component(
                BillingLineType.PLAN, "PRO", "Pro", 1, UUID.randomUUID(), 1,
                Money.of(new BigDecimal(amount), "USD")));
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        BillingPaymentAttempt payment = BillingPaymentAttempt.pendingProvider(invoice, "charge-1");
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        payment.recordProviderResult(
                new PaymentResult("provider-payment", PaymentStatus.SUCCESS, null), true, NOW);
        return payment;
    }
}
