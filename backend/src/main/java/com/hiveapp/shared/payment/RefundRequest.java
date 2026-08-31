package com.hiveapp.shared.payment;

import com.hiveapp.shared.money.Money;
import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(
        UUID accountId,
        String paymentReference,
        BigDecimal amount,
        String currency,
        String idempotencyKey
) {
    public RefundRequest {
        if (accountId == null) throw new IllegalArgumentException("Refund Account is required");
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("Refund payment reference is required");
        }
        Money money = Money.of(amount, currency);
        if (money.amount().signum() <= 0) throw new IllegalArgumentException("Refund amount must be positive");
        amount = money.amount();
        currency = money.currencyCode();
        paymentReference = paymentReference.trim();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Refund idempotency key is required");
        }
        idempotencyKey = idempotencyKey.trim();
    }
}
