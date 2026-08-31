package com.hiveapp.shared.email.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {

    Optional<EmailDelivery> findFirstByAccountIdAndRecipientUserIdOrderByCreatedAtDesc(
            UUID accountId,
            UUID recipientUserId);

    long countByAccountIdAndRecipientUserId(UUID accountId, UUID recipientUserId);

    long countByAccountIdAndRecipientUserIdAndStatus(
            UUID accountId,
            UUID recipientUserId,
            EmailDeliveryStatus status);
}
