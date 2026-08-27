package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.shared.money.ExactDecimal;
import com.hiveapp.shared.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Client-safe checkout state without payment-provider or manual-settlement provenance. */
public record ClientSubscriptionCheckoutDto(
        UUID id,
        SubscriptionCheckoutStatus status,
        @ExactDecimal BigDecimal amount,
        String currencyCode,
        PaymentStatus gatewayAttemptStatus,
        Instant confirmedAt
) {
    public static ClientSubscriptionCheckoutDto from(SubscriptionCheckoutDto source) {
        if (source == null) {
            return null;
        }
        return new ClientSubscriptionCheckoutDto(
                source.id(), source.status(), source.amount(), source.currencyCode(),
                source.gatewayAttemptStatus(), source.confirmedAt());
    }
}
