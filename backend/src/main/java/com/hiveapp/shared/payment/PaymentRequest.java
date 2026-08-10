package com.hiveapp.shared.payment;

import com.hiveapp.shared.money.Money;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
        UUID accountId,
        BigDecimal amount,
        String currency,
        String description
) {
    public PaymentRequest {
        Money money = Money.of(amount, currency);
        if (money.isNegative()) {
            throw new IllegalArgumentException("Payment amount cannot be negative");
        }
        amount = money.amount();
        currency = money.currencyCode();
    }
}
