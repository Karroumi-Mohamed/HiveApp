package com.hiveapp.shared.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRequestTest {

    @Test
    void normalizesAnExplicitIsoCurrencyWithoutUnsafeRounding() {
        PaymentRequest request = new PaymentRequest(
                UUID.randomUUID(), new BigDecimal("12.5"), "usd", "Example", "checkout-1");

        assertThat(request.amount()).isEqualByComparingTo("12.50");
        assertThat(request.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsNegativeOrOverPreciseAmounts() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> new PaymentRequest(
                accountId, new BigDecimal("-0.01"), "USD", "Negative", "negative"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment amount cannot be negative");
        assertThatThrownBy(() -> new PaymentRequest(
                accountId, new BigDecimal("1.001"), "USD", "Over precise", "precision"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minor-unit precision");
    }

    @Test
    void requiresAnIdempotencyKey() {
        assertThatThrownBy(() -> new PaymentRequest(
                UUID.randomUUID(), BigDecimal.ONE, "USD", "Missing key", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment idempotency key is required");
    }
}
