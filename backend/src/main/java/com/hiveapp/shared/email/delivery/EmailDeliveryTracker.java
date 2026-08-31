package com.hiveapp.shared.email.delivery;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.shared.email.EmailDispatchOutcome;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailDeliveryTracker {

    private final EmailDeliveryRepository repository;
    private final Clock clock;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID queue(
            UUID accountId,
            UUID recipientUserId,
            String recipientEmail,
            CredentialTokenPurpose purpose
    ) {
        EmailDelivery delivery = new EmailDelivery();
        delivery.setAccountId(accountId);
        delivery.setRecipientUserId(recipientUserId);
        delivery.setRecipientEmail(recipientEmail);
        delivery.setPurpose(purpose);
        delivery.setStatus(EmailDeliveryStatus.PENDING);
        return repository.save(delivery).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOutcome(UUID deliveryId, EmailDispatchOutcome outcome) {
        EmailDelivery delivery = pendingDelivery(deliveryId);
        delivery.setAttemptedAt(clock.instant());
        delivery.setFailureCode(null);
        if (outcome == EmailDispatchOutcome.SENT) {
            delivery.setStatus(EmailDeliveryStatus.SENT);
            delivery.setDeliveredAt(delivery.getAttemptedAt());
        } else {
            delivery.setStatus(EmailDeliveryStatus.SUPPRESSED);
            delivery.setDeliveredAt(null);
        }
        repository.saveAndFlush(delivery);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID deliveryId, EmailDeliveryFailureCode failureCode) {
        EmailDelivery delivery = pendingDelivery(deliveryId);
        delivery.setAttemptedAt(clock.instant());
        delivery.setDeliveredAt(null);
        delivery.setFailureCode(failureCode);
        delivery.setStatus(EmailDeliveryStatus.FAILED);
        repository.saveAndFlush(delivery);
    }

    @Transactional(readOnly = true)
    public Optional<EmailDeliverySummary> findSummary(UUID deliveryId) {
        return repository.findById(deliveryId).map(this::refreshAndSummarize);
    }

    @Transactional(readOnly = true)
    public Optional<EmailDeliverySummary> findLatestSummary(UUID accountId, UUID recipientUserId) {
        return repository.findFirstByAccountIdAndRecipientUserIdOrderByCreatedAtDesc(
                accountId, recipientUserId).map(this::refreshAndSummarize);
    }

    private EmailDelivery pendingDelivery(UUID deliveryId) {
        EmailDelivery delivery = repository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EmailDelivery", "id", deliveryId));
        if (delivery.getStatus() != EmailDeliveryStatus.PENDING) {
            throw new IllegalStateException("Email delivery attempt is already terminal");
        }
        return delivery;
    }

    private EmailDeliverySummary summary(EmailDelivery delivery) {
        long total = repository.countByAccountIdAndRecipientUserId(
                delivery.getAccountId(), delivery.getRecipientUserId());
        long failures = repository.countByAccountIdAndRecipientUserIdAndStatus(
                delivery.getAccountId(), delivery.getRecipientUserId(), EmailDeliveryStatus.FAILED);
        return new EmailDeliverySummary(
                delivery.getId(),
                delivery.getPurpose(),
                delivery.getStatus(),
                delivery.getAttemptedAt(),
                delivery.getDeliveredAt(),
                delivery.getFailureCode(),
                total,
                failures,
                delivery.getStatus() == EmailDeliveryStatus.FAILED);
    }

    private EmailDeliverySummary refreshAndSummarize(EmailDelivery delivery) {
        // AFTER_COMMIT outcome recording uses an isolated EntityManager. Refresh prevents
        // OpenEntityManagerInView from returning the controller's pre-commit PENDING instance.
        entityManager.refresh(delivery);
        return summary(delivery);
    }
}
