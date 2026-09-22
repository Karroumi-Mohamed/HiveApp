package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingRefundStatus;
import com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import com.hiveapp.platform.client.plan.domain.entity.BillingProviderEvent;
import com.hiveapp.platform.client.plan.domain.entity.BillingRefund;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingProviderEventRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingRefundRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
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
    private final com.hiveapp.platform.communication.BusinessNotifications notifications;
    private static final int MAX_ATTEMPTS = 5;

    private final BillingOutboxCommandRepository commands;
    private final BillingPaymentAttemptRepository payments;
    private final BillingProviderEventRepository providerEvents;
    private final BillingRefundRepository refunds;
    private final BillingInvoiceRepository invoices;
    private final SubscriptionCheckoutRepository checkouts;
    private final SubscriptionChangeOperationRepository operations;
    private final SubscriptionChangeActivationService activationService;
    private final SpecialAgreementTransitionService specialAgreements;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileProviderEvent(UUID eventId) {
        BillingProviderEvent event = providerEvents.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingProviderEvent", "id", eventId));
        Instant now = clock.instant();
        if (event.getIdempotencyKey() == null) {
            event.unmatched("Provider evidence did not contain the financial idempotency key.", now);
            providerEvents.save(event);
            return;
        }
        BillingOutboxCommand command = commands.findByIdempotencyKeyForUpdate(
                        event.getIdempotencyKey())
                .orElse(null);
        if (command == null) {
            event.unmatched("No provider command matches this idempotency key.", now);
            providerEvents.save(event);
            return;
        }
        if (command.getOperation() != event.getOperation()) {
            mismatch(event, command, "Provider operation does not match the command.", now);
            return;
        }
        if (command.getStatus() == BillingOutboxStatus.CANCELLED) {
            mismatch(event, command, "Provider evidence targets a command cancelled before dispatch.", now);
            return;
        }
        if (event.getProviderStatus() == PaymentStatus.SUCCESS
                && !event.isTrustedForSettlement()) {
            mismatch(event, command, "Provider adapter is not trusted for settlement.", now);
            return;
        }

        String mismatch = command.getOperation() == BillingOutboxOperation.CHARGE
                ? reconcileChargeEvent(command, event, now)
                : reconcileRefundEvent(command, event, now);
        if (mismatch != null) {
            mismatch(event, command, mismatch, now);
            return;
        }
        command.reconciled(now);
        commands.save(command);
        event.applied(command.getAggregateId(), command.getId(), now);
        providerEvents.save(event);
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
        if (result.status() == PaymentStatus.FAILED || (result.status() == PaymentStatus.SUCCESS && trusted)) {
            notifications.payment(invoice, payment.getId(), result.status() == PaymentStatus.SUCCESS);
        }
    }

    private String reconcileChargeEvent(
            BillingOutboxCommand command,
            BillingProviderEvent event,
            Instant reconciledAt
    ) {
        BillingPaymentAttempt payment = payments.findByIdForUpdate(command.getAggregateId())
                .orElse(null);
        if (payment == null) return "The matched Payment no longer exists.";
        String moneyMismatch = moneyMismatch(payment.money(), event);
        if (moneyMismatch != null) return moneyMismatch;
        if (payment.getStatus() == BillingPaymentStatus.CANCELLED) {
            return "Provider evidence targets a cancelled Payment.";
        }
        if (event.getProviderReference() != null) {
            var referenceOwner = payments.findByExternalReference(event.getProviderReference());
            if (referenceOwner.isPresent() && !referenceOwner.get().getId().equals(payment.getId())) {
                return "Provider reference is already attached to another Payment.";
            }
        }

        BillingInvoice invoice = invoices.findByIdForUpdate(payment.getInvoice().getId())
                .orElse(null);
        if (invoice == null) return "The matched Invoice no longer exists.";
        SubscriptionCheckout checkout = checkouts.findByIdForUpdate(invoice.getCheckout().getId())
                .orElse(null);
        if (checkout == null) return "The matched Checkout no longer exists.";
        String stateMismatch = validateChargeState(payment, invoice, checkout, event);
        if (stateMismatch != null) return stateMismatch;

        try {
            payment.reconcileProviderResult(
                    eventResult(event), event.isTrustedForSettlement(), reconciledAt);
        } catch (IllegalStateException conflict) {
            return conflict.getMessage();
        }
        payments.save(payment);
        checkout.setGatewayAttemptStatus(event.getProviderStatus());
        checkout.setGatewayReference(event.getProviderReference());
        checkout.setGatewayFailureReason(event.getFailureReason());
        if (event.getProviderStatus() == PaymentStatus.SUCCESS
                && invoice.getStatus() == BillingInvoiceStatus.OPEN) {
            invoice.settle(reconciledAt);
            settleCheckoutAfterReconciliation(
                    checkout, event.getProviderReference(), reconciledAt);
        } else if (event.getProviderStatus() == PaymentStatus.FAILED
                && invoice.getStatus() == BillingInvoiceStatus.OPEN) {
            markCheckoutFailed(checkout, event.getFailureReason());
        }
        invoices.save(invoice);
        checkouts.save(checkout);
        if (event.getProviderStatus() == PaymentStatus.FAILED || event.getProviderStatus() == PaymentStatus.SUCCESS) {
            notifications.payment(invoice, payment.getId(), event.getProviderStatus() == PaymentStatus.SUCCESS);
        }
        return null;
    }

    private String validateChargeState(
            BillingPaymentAttempt payment,
            BillingInvoice invoice,
            SubscriptionCheckout checkout,
            BillingProviderEvent event
    ) {
        if (event.getProviderStatus() == PaymentStatus.PENDING
                && payment.getStatus() != BillingPaymentStatus.PENDING) {
            return "Pending provider evidence cannot reopen a terminal Payment.";
        }
        if (invoice.getStatus() == BillingInvoiceStatus.SETTLED) {
            boolean sameSettlement = event.getProviderStatus() == PaymentStatus.SUCCESS
                    && payment.getStatus() == BillingPaymentStatus.SUCCEEDED
                    && checkout.getStatus() == SubscriptionCheckoutStatus.CONFIRMED
                    && checkout.getConfirmationSource() == CheckoutConfirmationSource.TRUSTED_PROVIDER
                    && event.getProviderReference().equals(checkout.getConfirmationReference());
            return sameSettlement ? null
                    : "Invoice was already settled by different evidence.";
        }
        if (invoice.getStatus() != BillingInvoiceStatus.OPEN) {
            return "Provider evidence cannot settle this Invoice state.";
        }
        if (checkout.getStatus() != SubscriptionCheckoutStatus.PENDING_CONFIRMATION
                && checkout.getStatus() != SubscriptionCheckoutStatus.FAILED) {
            return "Provider evidence cannot change this Checkout state.";
        }
        if (event.getProviderStatus() != PaymentStatus.SUCCESS) return null;
        SubscriptionChangeStatus operationStatus = checkout.getChangeOperation().getStatus();
        if (operationStatus != SubscriptionChangeStatus.AWAITING_CONFIRMATION
                && operationStatus != SubscriptionChangeStatus.NEEDS_ATTENTION) {
            return "Provider evidence cannot confirm this subscription operation state.";
        }
        return null;
    }

    private String reconcileRefundEvent(
            BillingOutboxCommand command,
            BillingProviderEvent event,
            Instant reconciledAt
    ) {
        BillingRefund refund = refunds.findByIdForUpdate(command.getAggregateId()).orElse(null);
        if (refund == null) return "The matched Refund no longer exists.";
        String moneyMismatch = moneyMismatch(refund.money(), event);
        if (moneyMismatch != null) return moneyMismatch;
        if (event.getProviderStatus() == PaymentStatus.PENDING
                && refund.getStatus() != BillingRefundStatus.PENDING) {
            return "Pending provider evidence cannot reopen a terminal Refund.";
        }
        if (event.getProviderReference() != null) {
            var referenceOwner = refunds.findByProviderReference(event.getProviderReference());
            if (referenceOwner.isPresent() && !referenceOwner.get().getId().equals(refund.getId())) {
                return "Provider reference is already attached to another Refund.";
            }
        }
        try {
            refund.reconcileProviderResult(eventResult(event), reconciledAt);
        } catch (IllegalStateException conflict) {
            return conflict.getMessage();
        }
        refunds.save(refund);
        return null;
    }

    private String moneyMismatch(Money expected, BillingProviderEvent event) {
        Money actual = Money.of(event.getAmount(), event.getCurrencyCode());
        return expected.equals(actual) ? null
                : "Provider amount or currency does not match the financial command.";
    }

    private PaymentResult eventResult(BillingProviderEvent event) {
        return new PaymentResult(
                event.getProviderReference(), event.getProviderStatus(), event.getFailureReason());
    }

    private void mismatch(
            BillingProviderEvent event,
            BillingOutboxCommand command,
            String reason,
            Instant now
    ) {
        event.mismatched(command.getAggregateId(), command.getId(), reason, now);
        providerEvents.save(event);
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
        if (operation.getEffectiveAt().isAfter(now)) {
            operation.setStatus(SubscriptionChangeStatus.PENDING);
            operation.setAttentionReason(null);
            operations.save(operation);
            specialAgreements.operationScheduled(operation);
        } else {
            activationService.activate(
                    operation,
                    operation.getEffectiveAt().isAfter(now) ? operation.getEffectiveAt() : now);
        }
    }

    private void settleCheckoutAfterReconciliation(
            SubscriptionCheckout checkout,
            String reference,
            Instant now
    ) {
        SubscriptionChangeOperation operation = checkout.getChangeOperation();
        if (checkout.getStatus() == SubscriptionCheckoutStatus.FAILED) {
            checkout.setStatus(SubscriptionCheckoutStatus.PENDING_CONFIRMATION);
        }
        if (operation.getStatus() == SubscriptionChangeStatus.NEEDS_ATTENTION) {
            operation.setStatus(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
            operation.setAttentionReason(null);
        }
        settleCheckout(checkout, reference, now);
    }

    private void markCheckoutFailed(SubscriptionCheckout checkout, String failureReason) {
        String reason = failureReason == null || failureReason.isBlank()
                ? "Payment attempt failed." : failureReason.trim();
        checkout.setStatus(SubscriptionCheckoutStatus.FAILED);
        checkout.setGatewayAttemptStatus(PaymentStatus.FAILED);
        checkout.setGatewayFailureReason(reason);
        SubscriptionChangeOperation operation = checkout.getChangeOperation();
        operation.setStatus(SubscriptionChangeStatus.NEEDS_ATTENTION);
        operation.setAttentionReason(reason);
        operations.save(operation);
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
