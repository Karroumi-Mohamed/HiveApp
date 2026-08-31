package com.hiveapp.platform.admin.dto;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.shared.email.delivery.EmailDeliveryFailureCode;
import com.hiveapp.shared.email.delivery.EmailDeliveryStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class PlatformCommunicationModels {
    private PlatformCommunicationModels() {}

    public record Summary(
            long total,
            Map<EmailDeliveryStatus, Long> byStatus,
            Map<CredentialTokenPurpose, Long> byPurpose
    ) {}

    public record Delivery(
            UUID id,
            UUID accountId,
            UUID recipientUserId,
            String recipientEmail,
            CredentialTokenPurpose purpose,
            EmailDeliveryStatus status,
            Instant createdAt,
            Instant attemptedAt,
            Instant deliveredAt,
            EmailDeliveryFailureCode failureCode,
            boolean recipientIdentityVisible,
            boolean failureEvidenceVisible
    ) {}

    public record RecipientIdentity(UUID deliveryId, UUID userId, String email) {}

    public record FailureEvidence(
            UUID deliveryId,
            Instant attemptedAt,
            Instant deliveredAt,
            EmailDeliveryFailureCode failureCode
    ) {}
}
