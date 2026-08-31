package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import com.hiveapp.platform.client.plan.domain.entity.BillingRefund;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingRefundRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.payment.PaymentResult;
import com.hiveapp.shared.payment.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Owns the short transactions on either side of provider I/O. */
@Service
@RequiredArgsConstructor
public class BillingOutboxTransactionService {
    private static final int MAX_ATTEMPTS = 5;

    private final BillingOutboxCommandRepository commands;
    private final BillingPaymentAttemptRepository payments;
    private final BillingRefundRepository refunds;
    private final BillingInvoiceRepository invoices;
    private final SubscriptionCheckoutRepository checkouts;
    private final SubscriptionChangeOperationRepository operations;
    private final SubscriptionChangeActivationService activationService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<BillingProviderCommand> claim(UUID commandId) {
        Instant now = clock.instant();
        BillingOutboxCommand command = commands.findByIdForUpdate(commandId).orElse(null);
        if (command == null || command.getStatus() != BillingOutboxStatus.PENDING
                || command.getNextAttemptAt().isAfter(now)) {
            return Optional.empty();
        }
        command.claim(now);
        commands.save(command);
        return Optional.of(command.getOperation() == BillingOutboxOperation.CHARGE
                ? chargeCommand(command) : refundCommand(command));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            UUID commandId,
            BillingProviderCommandExecutor.Result providerResult
    ) {
        BillingOutboxCommand command = requireProcessing(commandId);
        PaymentResult result = providerResult.paymentResult();
        Instant now = clock.instant();
        if (command.getOperation() == BillingOutboxOperation.CHARGE) {
            completeCharge(command, result, providerResult.trustedForSettlement(), now);
        } else {
            BillingRefund refund = refunds.findByIdForUpdate(command.getAggregateId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "BillingRefund", "id", command.getAggregateId()));
            refund.recordProviderResult(result, now);
            refunds.save(refund);
        }
        command.processed(now);
        commands.save(command);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failOrRetry(UUID commandId, RuntimeException failure) {
        BillingOutboxCommand command = commands.findByIdForUpdate(commandId).orElse(null);
        if (command == null || command.getStatus() != BillingOutboxStatus.PROCESSING) return;
        Instant now = clock.instant();
        String safeReason = "Provider command failed (" + failure.getClass().getSimpleName() + ").";
        if (command.getAttemptCount() >= MAX_ATTEMPTS) {
            terminalTransportFailure(command, safeReason, now);
            command.fail(safeReason, now);
        } else {
            long delaySeconds = Math.min(300, 5L << Math.max(0, command.getAttemptCount() - 1));
            command.retry(safeReason, now.plusSeconds(delaySeconds));
        }
        commands.save(command);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverStale(UUID commandId, Instant staleBefore) {
        BillingOutboxCommand command = commands.findByIdForUpdate(commandId).orElse(null);
        if (command == null || command.getStatus() != BillingOutboxStatus.PROCESSING
                || command.getClaimedAt() == null || !command.getClaimedAt().isBefore(staleBefore)) {
            return;
        }
        command.recoverStaleClaim("Recovered an interrupted provider command.", clock.instant());
        commands.save(command);
    }

    private BillingProviderCommand chargeCommand(BillingOutboxCommand command) {
        BillingPaymentAttempt payment = payments.findByIdForUpdate(command.getAggregateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingPaymentAttempt", "id", command.getAggregateId()));
        BillingInvoice invoice = payment.getInvoice();
        return new BillingProviderCommand(
                command.getId(),
                command.getOperation(),
                payment.getId(),
                invoice.getAccount().getId(),
                payment.getAmount(),
                payment.getCurrencyCode(),
                null,
                "Invoice " + invoice.getInvoiceNumber(),
                command.getIdempotencyKey());
    }

    private BillingProviderCommand refundCommand(BillingOutboxCommand command) {
        BillingRefund refund = refunds.findByIdForUpdate(command.getAggregateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingRefund", "id", command.getAggregateId()));
        BillingPaymentAttempt payment = refund.getPayment();
        return new BillingProviderCommand(
                command.getId(),
                command.getOperation(),
                refund.getId(),
                payment.getInvoice().getAccount().getId(),
                refund.getAmount(),
                refund.getCurrencyCode(),
                payment.getExternalReference(),
                "Refund for Invoice " + payment.getInvoice().getInvoiceNumber(),
                command.getIdempotencyKey());
    }

    private void completeCharge(
            BillingOutboxCommand command,
            PaymentResult result,
            boolean trusted,
            Instant now
    ) {
        BillingPaymentAttempt payment = payments.findByIdForUpdate(command.getAggregateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingPaymentAttempt", "id", command.getAggregateId()));
        payment.recordProviderResult(result, trusted, now);
        payments.save(payment);

        BillingInvoice invoice = invoices.findByIdForUpdate(payment.getInvoice().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingInvoice", "id", payment.getInvoice().getId()));
        SubscriptionCheckout checkout = checkouts.findByIdForUpdate(invoice.getCheckout().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SubscriptionCheckout", "id", invoice.getCheckout().getId()));
        checkout.setGatewayAttemptStatus(result.status());
        checkout.setGatewayReference(result.transactionId());
        checkout.setGatewayFailureReason(result.failureReason());

        if (result.status() == PaymentStatus.FAILED) {
            checkout.setStatus(SubscriptionCheckoutStatus.FAILED);
            SubscriptionChangeOperation operation = checkout.getChangeOperation();
            operation.setStatus(SubscriptionChangeStatus.NEEDS_ATTENTION);
            operation.setAttentionReason(result.failureReason() == null || result.failureReason().isBlank()
                    ? "Payment attempt failed." : result.failureReason().trim());
            operations.save(operation);
        } else if (result.status() == PaymentStatus.SUCCESS && trusted) {
            if (result.transactionId() == null || result.transactionId().isBlank()) {
                throw new IllegalStateException("Trusted settlement requires a provider reference");
            }
            invoice.settle(now);
            settleCheckout(checkout, result.transactionId().trim(), now);
        }
        invoices.save(invoice);
        checkouts.save(checkout);
    }

    private void settleCheckout(SubscriptionCheckout checkout, String reference, Instant now) {
        if (checkout.getStatus() != SubscriptionCheckoutStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Only a pending Checkout can be settled by a provider");
        }
        checkout.setStatus(SubscriptionCheckoutStatus.CONFIRMED);
        checkout.setConfirmationSource(CheckoutConfirmationSource.TRUSTED_PROVIDER);
        checkout.setConfirmationReference(reference);
        checkout.setConfirmationReason("Trusted payment provider settlement.");
        checkout.setConfirmedByUserId(null);
        checkout.setConfirmedAt(now);

        SubscriptionChangeOperation operation = checkout.getChangeOperation();
        if (operation.getStatus() != SubscriptionChangeStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("Checkout operation is not awaiting confirmation");
        }
        if (operation.getTiming() == SubscriptionChangeTiming.AT_RENEWAL
                && operation.getEffectiveAt().isAfter(now)) {
            operation.setStatus(SubscriptionChangeStatus.PENDING);
            operation.setAttentionReason(null);
            operations.save(operation);
        } else {
            activationService.activate(operation, now);
        }
    }

    private void terminalTransportFailure(
            BillingOutboxCommand command,
            String reason,
            Instant now
    ) {
        if (command.getOperation() == BillingOutboxOperation.CHARGE) {
            BillingPaymentAttempt payment = payments.findByIdForUpdate(command.getAggregateId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "BillingPaymentAttempt", "id", command.getAggregateId()));
            payment.recordTransportFailure(reason, now);
            payments.save(payment);
            BillingInvoice invoice = payment.getInvoice();
            SubscriptionCheckout checkout = checkouts.findByIdForUpdate(invoice.getCheckout().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "SubscriptionCheckout", "id", invoice.getCheckout().getId()));
            checkout.setStatus(SubscriptionCheckoutStatus.FAILED);
            checkout.setGatewayAttemptStatus(PaymentStatus.FAILED);
            checkout.setGatewayFailureReason(reason);
            checkouts.save(checkout);
            SubscriptionChangeOperation operation = checkout.getChangeOperation();
            operation.setStatus(SubscriptionChangeStatus.NEEDS_ATTENTION);
            operation.setAttentionReason(reason);
            operations.save(operation);
        } else {
            BillingRefund refund = refunds.findByIdForUpdate(command.getAggregateId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "BillingRefund", "id", command.getAggregateId()));
            refund.recordTransportFailure(reason, now);
            refunds.save(refund);
        }
    }

    private BillingOutboxCommand requireProcessing(UUID commandId) {
        BillingOutboxCommand command = commands.findByIdForUpdate(commandId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingOutboxCommand", "id", commandId));
        if (command.getStatus() != BillingOutboxStatus.PROCESSING) {
            throw new IllegalStateException("Billing outbox command is not processing");
        }
        return command;
    }
}
