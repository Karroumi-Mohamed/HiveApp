package com.hiveapp.shared.email.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID>,
        JpaSpecificationExecutor<EmailDelivery> {

    Optional<EmailDelivery> findFirstByAccountIdAndRecipientUserIdOrderByCreatedAtDesc(
            UUID accountId,
            UUID recipientUserId);

    long countByAccountIdAndRecipientUserId(UUID accountId, UUID recipientUserId);

    long countByAccountIdAndRecipientUserIdAndStatus(
            UUID accountId,
            UUID recipientUserId,
            EmailDeliveryStatus status);

    @Query("select delivery.status as value, count(delivery) as total, "
            + "min(delivery.createdAt) as oldest "
            + "from EmailDelivery delivery group by delivery.status")
    java.util.List<CountByStatus> countByStatus();

    @Query("select delivery.purpose as value, count(delivery) as total "
            + "from EmailDelivery delivery group by delivery.purpose")
    java.util.List<CountByPurpose> countByPurpose();

    interface CountByStatus {
        EmailDeliveryStatus getValue();
        long getTotal();
        java.time.Instant getOldest();
    }

    interface CountByPurpose {
        com.hiveapp.identity.domain.constant.CredentialTokenPurpose getValue();
        long getTotal();
    }
}
