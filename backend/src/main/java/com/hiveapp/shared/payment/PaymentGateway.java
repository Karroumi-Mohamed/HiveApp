package com.hiveapp.shared.payment;

/**
 * Swappable payment gateway contract.
 *
 * Swap implementations via Spring @Primary or @ConditionalOnProperty.
 * Current active impl: DevPaymentGateway (always succeeds, no real charges).
 * Future: StripePaymentGateway, etc.
 */
public interface PaymentGateway {

    /**
     * Charge the account for a subscription period or one-time add-on.
     */
    PaymentResult charge(PaymentRequest request);

    /**
     * Refund a previous charge by transaction ID.
     */
    PaymentResult refund(String transactionId, java.math.BigDecimal amount);
}
