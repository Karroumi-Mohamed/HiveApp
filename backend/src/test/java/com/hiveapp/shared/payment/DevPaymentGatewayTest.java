package com.hiveapp.shared.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DevPaymentGatewayTest {

    @Test
    void returnsConfiguredPendingOutcomeButNeverClaimsSettlementAuthority() {
        BillingProperties properties = new BillingProperties();
        properties.getSimulator().setOutcome(PaymentStatus.PENDING);
        DevPaymentGateway gateway = new DevPaymentGateway(properties);

        PaymentResult result = gateway.charge(request());

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.transactionId()).startsWith("DEV-");
        assertThat(gateway.trustedForSettlement()).isFalse();
    }

    @Test
    void returnsConfiguredFailureWithAnOperatorVisibleReason() {
        BillingProperties properties = new BillingProperties();
        properties.getSimulator().setOutcome(PaymentStatus.FAILED);
        properties.getSimulator().setFailureReason("Card simulation declined");
        DevPaymentGateway gateway = new DevPaymentGateway(properties);

        PaymentResult result = gateway.charge(request());

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("Card simulation declined");
        assertThat(gateway.trustedForSettlement()).isFalse();
    }

    @Test
    void simulatedSuccessStillHasNoSettlementAuthority() {
        BillingProperties properties = new BillingProperties();
        properties.getSimulator().setOutcome(PaymentStatus.SUCCESS);
        DevPaymentGateway gateway = new DevPaymentGateway(properties);

        assertThat(gateway.charge(request()).succeeded()).isTrue();
        assertThat(gateway.trustedForSettlement()).isFalse();
    }

    @Test
    void replayingTheSameIdempotencyKeyReturnsTheSameProviderReference() {
        BillingProperties properties = new BillingProperties();
        DevPaymentGateway gateway = new DevPaymentGateway(properties);

        assertThat(gateway.charge(request()).transactionId())
                .isEqualTo(gateway.charge(request()).transactionId());
    }

    private PaymentRequest request() {
        return new PaymentRequest(
                UUID.randomUUID(), new BigDecimal("29.99"), "USD", "Test checkout", "checkout-1");
    }
}
