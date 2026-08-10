package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.shared.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionCheckoutDto(
        UUID id,
        SubscriptionCheckoutStatus status,
        BigDecimal amount,
        String currencyCode,
        PaymentStatus gatewayAttemptStatus,
        String gatewayReference,
        String gatewayFailureReason,
        CheckoutConfirmationSource confirmationSource,
        String confirmationReference,
        Instant confirmedAt
) {}
