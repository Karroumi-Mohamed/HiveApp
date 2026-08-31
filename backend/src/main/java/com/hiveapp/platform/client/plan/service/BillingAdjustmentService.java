package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingRefundStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingCredit;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import com.hiveapp.platform.client.plan.domain.entity.BillingRefund;
import com.hiveapp.platform.client.plan.domain.repository.BillingCreditRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingRefundRepository;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingAdjustmentService {
    private final BillingPaymentAttemptRepository payments;
    private final BillingRefundRepository refunds;
    private final BillingCreditRepository credits;
    private final BillingInvoiceRepository invoices;
    private final BillingOutboxCommandRepository outbox;
    private final Clock clock;

    @Transactional
    public BillingRefund requestRefund(
            UUID paymentId,
            Money amount,
            String reason,
            UUID operatorUserId,
            String idempotencyKey
    ) {
        String normalizedKey = requireText(idempotencyKey, "Refund idempotency key is required");
        String normalizedReason = requireText(reason, "Refund reason is required");
        BillingRefund replay = refunds.findByIdempotencyKey(normalizedKey).orElse(null);
        if (replay != null) {
            if (replay.getPayment().getId().equals(paymentId)
                    && replay.money().equals(amount)
                    && replay.getReason().equals(normalizedReason)
                    && replay.getOperatorUserId().equals(operatorUserId)) {
                return replay;
            }
            throw new InvalidStateException("Refund idempotency key was used for different data.");
        }
        var payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingPaymentAttempt", "id", paymentId));
        requireRefundCapacity(paymentId, payment.getCurrencyCode(), payment.getAmount(), amount);
        BillingRefund refund = refunds.saveAndFlush(BillingRefund.pendingProvider(
                payment, amount, normalizedReason, operatorUserId, normalizedKey));
        outbox.save(BillingOutboxCommand.pending(
                BillingOutboxOperation.REFUND,
                refund.getId(),
                normalizedKey,
                clock.instant()));
        return refund;
    }

    @Transactional
    public BillingRefund recordManualRefund(
            UUID paymentId,
            Money amount,
            String reason,
            UUID operatorUserId,
            String idempotencyKey,
            String externalReference
    ) {
        String normalizedKey = requireText(idempotencyKey, "Refund idempotency key is required");
        String normalizedReason = requireText(reason, "Refund reason is required");
        String normalizedReference = requireText(
                externalReference, "Manual Refund reference is required");
        BillingRefund replay = refunds.findByIdempotencyKey(normalizedKey).orElse(null);
        if (replay != null) {
            if (replay.getPayment().getId().equals(paymentId)
                    && replay.money().equals(amount)
                    && replay.getReason().equals(normalizedReason)
                    && replay.getOperatorUserId().equals(operatorUserId)
                    && normalizedReference.equals(replay.getProviderReference())) {
                return replay;
            }
            throw new InvalidStateException("Refund idempotency key was used for different data.");
        }
        var payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingPaymentAttempt", "id", paymentId));
        requireRefundCapacity(paymentId, payment.getCurrencyCode(), payment.getAmount(), amount);
        return refunds.save(BillingRefund.manualSucceeded(
                payment, amount, normalizedReason, operatorUserId, normalizedKey,
                normalizedReference, clock.instant()));
    }

    @Transactional
    public BillingCredit issueCredit(
            UUID invoiceId,
            Money amount,
            String reason,
            String source,
            UUID operatorUserId,
            String externalReference
    ) {
        var invoice = invoices.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingInvoice", "id", invoiceId));
        Money issued = Money.of(credits.sumAmountByInvoiceId(invoiceId), invoice.getCurrencyCode());
        if (issued.add(amount).amount().compareTo(invoice.getTotalAmount()) > 0) {
            throw new InvalidStateException("Credit exceeds the remaining Invoice amount.");
        }
        return credits.save(BillingCredit.issue(
                invoice, amount, reason, source, operatorUserId, externalReference, clock.instant()));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private void requireRefundCapacity(
            UUID paymentId,
            String currencyCode,
            java.math.BigDecimal paymentAmount,
            Money requested
    ) {
        Money reserved = Money.of(
                refunds.sumAmountByPaymentIdAndStatusIn(
                        paymentId,
                        List.of(BillingRefundStatus.PENDING, BillingRefundStatus.SUCCEEDED)),
                currencyCode);
        if (reserved.add(requested).amount().compareTo(paymentAmount) > 0) {
            throw new InvalidStateException("Refund exceeds the remaining refundable Payment amount.");
        }
    }
}
