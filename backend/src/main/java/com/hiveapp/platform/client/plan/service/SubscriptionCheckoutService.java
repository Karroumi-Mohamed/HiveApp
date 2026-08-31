package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionCheckoutService {

    private final SubscriptionCheckoutRepository checkoutRepository;
    private final SubscriptionChangeOperationRepository operationRepository;
    private final SubscriptionChangeActivationService activationService;
    private final BillingLedgerService billingLedgerService;
    private final Clock clock;

    @Transactional
    public SubscriptionCheckout initiate(
            SubscriptionChangeOperation operation,
            Money amount,
            UUID requestedByUserId
    ) {
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Only positive-price changes require a checkout");
        }
        if (operation.getId() == null) {
            throw new IllegalStateException("Change operation must be persisted before checkout initiation");
        }
        if (requestedByUserId == null) {
            throw new IllegalArgumentException("Checkout requester is required");
        }

        SubscriptionCheckout checkout = new SubscriptionCheckout();
        checkout.setAccount(operation.getAccount());
        checkout.setChangeOperation(operation);
        checkout.setStatus(SubscriptionCheckoutStatus.PENDING_CONFIRMATION);
        checkout.setGatewayAttemptStatus(PaymentStatus.PENDING);
        checkout.setMoney(amount);
        checkout.setRequestedByUserId(requestedByUserId);

        SubscriptionCheckout saved = checkoutRepository.saveAndFlush(checkout);
        operation.setCheckout(saved);
        operationRepository.save(operation);
        billingLedgerService.invoiceAndQueueCharge(
                saved, operation.getTargetSnapshot(), amount, requestedByUserId);
        return saved;
    }

    @Transactional
    public SubscriptionCheckout confirmManual(
            UUID checkoutId,
            UUID operatorUserId,
            String reference,
            String reason
    ) {
        String normalizedReference = requireText(reference, "Manual confirmation reference is required");
        String normalizedReason = requireText(reason, "Manual confirmation reason is required");
        if (operatorUserId == null) {
            throw new IllegalArgumentException("Confirming operator is required");
        }

        SubscriptionCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionCheckout", "id", checkoutId));
        if (checkout.getStatus() == SubscriptionCheckoutStatus.CONFIRMED) {
            if (normalizedReference.equals(checkout.getConfirmationReference())) {
                return checkout;
            }
            throw new InvalidStateException("Checkout was already confirmed with a different reference.");
        }
        if (checkout.getStatus() != SubscriptionCheckoutStatus.PENDING_CONFIRMATION) {
            throw new InvalidStateException("Only a pending checkout can be confirmed.");
        }

        Instant now = clock.instant();
        billingLedgerService.recordManualSettlement(
                checkoutId, operatorUserId, normalizedReference, normalizedReason);
        checkout = checkoutRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionCheckout", "id", checkoutId));
        if (checkout.getStatus() == SubscriptionCheckoutStatus.CONFIRMED) {
            if (normalizedReference.equals(checkout.getConfirmationReference())) {
                return checkout;
            }
            throw new InvalidStateException("Checkout was already confirmed with a different reference.");
        }
        if (checkout.getStatus() != SubscriptionCheckoutStatus.PENDING_CONFIRMATION) {
            throw new InvalidStateException("Only a pending checkout can be confirmed.");
        }
        checkout.setStatus(SubscriptionCheckoutStatus.CONFIRMED);
        checkout.setConfirmationSource(CheckoutConfirmationSource.MANUAL_OPERATOR);
        checkout.setConfirmationReference(normalizedReference);
        checkout.setConfirmationReason(normalizedReason);
        checkout.setConfirmedByUserId(operatorUserId);
        checkout.setConfirmedAt(now);
        checkoutRepository.save(checkout);

        SubscriptionChangeOperation operation = checkout.getChangeOperation();
        if (operation.getStatus() != SubscriptionChangeStatus.AWAITING_CONFIRMATION) {
            throw new InvalidStateException("Checkout change operation is not awaiting confirmation.");
        }
        if (operation.getTiming() == SubscriptionChangeTiming.AT_RENEWAL
                && operation.getEffectiveAt().isAfter(now)) {
            operation.setStatus(SubscriptionChangeStatus.PENDING);
            operation.setAttentionReason(null);
            operationRepository.save(operation);
        } else {
            activationService.activate(operation, now);
        }
        return checkout;
    }

    @Transactional
    public void cancelFor(SubscriptionChangeOperation operation) {
        checkoutRepository.findByChangeOperationId(operation.getId()).ifPresent(checkout -> {
            if (checkout.getStatus() == SubscriptionCheckoutStatus.CONFIRMED) {
                throw new InvalidStateException("A confirmed checkout cannot be cancelled.");
            }
            if (checkout.getStatus() == SubscriptionCheckoutStatus.PENDING_CONFIRMATION) {
                billingLedgerService.cancelForCheckout(checkout.getId());
                SubscriptionCheckout locked = checkoutRepository.findByIdForUpdate(checkout.getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "SubscriptionCheckout", "id", checkout.getId()));
                if (locked.getStatus() != SubscriptionCheckoutStatus.PENDING_CONFIRMATION) {
                    throw new InvalidStateException("Only a pending checkout can be cancelled.");
                }
                locked.setStatus(SubscriptionCheckoutStatus.CANCELLED);
                checkoutRepository.save(locked);
            }
        });
    }

    @Transactional(readOnly = true)
    public SubscriptionCheckoutDto toDto(SubscriptionCheckout checkout) {
        if (checkout == null) {
            return null;
        }
        return new SubscriptionCheckoutDto(
                checkout.getId(),
                checkout.getStatus(),
                checkout.getAmount(),
                checkout.getCurrencyCode(),
                checkout.getGatewayAttemptStatus(),
                checkout.getGatewayReference(),
                checkout.getGatewayFailureReason(),
                checkout.getConfirmationSource(),
                checkout.getConfirmationReference(),
                checkout.getConfirmedAt());
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
