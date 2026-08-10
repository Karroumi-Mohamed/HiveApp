package com.hiveapp.shared.email.delivery;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;

import java.time.Instant;
import java.util.UUID;

public record EmailDeliverySummary(
        UUID deliveryId,
        CredentialTokenPurpose purpose,
        EmailDeliveryStatus status,
        Instant attemptedAt,
        Instant deliveredAt,
        EmailDeliveryFailureCode failureCode,
        long totalAttempts,
        long failedAttempts,
        boolean retryable
) {
}
