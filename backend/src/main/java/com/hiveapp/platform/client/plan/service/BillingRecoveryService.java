package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentKind;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.shared.exception.IdempotencyConflictException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingRecoveryService {
    private final BillingInvoiceRepository invoices;
    private final BillingPaymentAttemptRepository payments;
    private final BillingOutboxCommandRepository outbox;
    private final SubscriptionCheckoutRepository checkouts;
    private final SubscriptionChangeOperationRepository operations;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RetryPreview preview(UUID invoiceId) {
        var invoice = invoices.findDetailedById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingInvoice", "id", invoiceId));
        var previous = payments.findFirstByInvoiceIdAndKindOrderByCreatedAtDesc(
                        invoiceId, BillingPaymentKind.PROVIDER)
                .orElse(null);
        if (previous == null) {
            return new RetryPreview(null, null, null, false, false,
                    "Invoice has no provider Payment attempt to retry.");
        }
        var command = outbox.findByAggregateIdAndOperation(
                        previous.getId(), BillingOutboxOperation.CHARGE)
                .orElse(null);
        String blocker = retryBlocker(invoice.getStatus(), previous, command);
        return new RetryPreview(
                previous.getId(), previous.getStatus(), command == null ? null : command.getStatus(),
                blocker == null, command != null && command.getStatus() == BillingOutboxStatus.FAILED,
                blocker);
    }

    @Transactional
    public BillingPaymentAttempt retryCharge(
            UUID invoiceId,
            UUID operatorUserId,
            String idempotencyKey,
            String reason,
            String recoveryReference,
            boolean providerConfirmedNotCaptured
    ) {
        var replay = payments.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            if (replay.get().getInvoice().getId().equals(invoiceId)
                    && replay.get().getRetryOfPaymentId() != null) return replay.get();
            throw new IdempotencyConflictException();
        }
        var snapshot = invoices.findDetailedById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingInvoice", "id", invoiceId));
        var previousSnapshot = payments.findFirstByInvoiceIdAndKindOrderByCreatedAtDesc(
                        invoiceId, BillingPaymentKind.PROVIDER)
                .orElseThrow(() -> new InvalidStateException(
                        "Invoice has no provider Payment attempt to retry."));
        BillingOutboxCommand previousCommand = outbox.findByAggregateIdAndOperationForUpdate(
                        previousSnapshot.getId(), BillingOutboxOperation.CHARGE)
                .orElseThrow(() -> new InvalidStateException(
                        "Previous Payment has no provider command evidence."));
        BillingPaymentAttempt previous = payments.findByIdForUpdate(previousSnapshot.getId())
                .orElseThrow(() -> new InvalidStateException("Previous Payment no longer exists."));
        var invoice = invoices.findByIdForUpdate(snapshot.getId())
                .orElseThrow(() -> new InvalidStateException("Invoice no longer exists."));
        String blocker = retryBlocker(invoice.getStatus(), previous, previousCommand);
        if (blocker != null) throw new InvalidStateException(blocker);
        if (previousCommand.getStatus() == BillingOutboxStatus.FAILED
                && !providerConfirmedNotCaptured) {
            throw new InvalidStateException(
                    "Provider non-capture must be confirmed before retrying an ambiguous transport failure.");
        }

        var checkout = checkouts.findByIdForUpdate(invoice.getCheckout().getId())
                .orElseThrow(() -> new InvalidStateException("Checkout no longer exists."));
        var operation = checkout.getChangeOperation();
        if (checkout.getStatus() != SubscriptionCheckoutStatus.FAILED
                || operation.getStatus() != SubscriptionChangeStatus.NEEDS_ATTENTION) {
            throw new InvalidStateException("Checkout is not waiting for charge recovery.");
        }

        BillingPaymentAttempt retry = payments.saveAndFlush(
                BillingPaymentAttempt.pendingProviderRetry(
                        invoice, idempotencyKey, previous.getId(), operatorUserId,
                        reason, recoveryReference));
        outbox.save(BillingOutboxCommand.pending(
                BillingOutboxOperation.CHARGE, retry.getId(), idempotencyKey, clock.instant()));
        checkout.setStatus(SubscriptionCheckoutStatus.PENDING_CONFIRMATION);
        checkout.setGatewayAttemptStatus(null);
        checkout.setGatewayReference(null);
        checkout.setGatewayFailureReason(null);
        operation.setStatus(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        operation.setAttentionReason(null);
        operations.save(operation);
        checkouts.save(checkout);
        return retry;
    }

    private String retryBlocker(
            BillingInvoiceStatus invoiceStatus,
            BillingPaymentAttempt previous,
            BillingOutboxCommand command
    ) {
        if (invoiceStatus != BillingInvoiceStatus.OPEN) return "Only an open Invoice can be retried.";
        if (previous.getStatus() != BillingPaymentStatus.FAILED) {
            return "Only a failed provider Payment can be retried.";
        }
        if (command == null) return "Previous Payment has no provider command evidence.";
        if (command.getStatus() != BillingOutboxStatus.FAILED
                && command.getStatus() != BillingOutboxStatus.PROCESSED) {
            return "Provider command is still pending or in flight; reconcile it before retrying.";
        }
        return null;
    }

    public record RetryPreview(
            UUID previousPaymentId,
            BillingPaymentStatus previousPaymentStatus,
            BillingOutboxStatus previousCommandStatus,
            boolean retryAllowed,
            boolean providerConfirmationRequired,
            String blocker
    ) {}
}
