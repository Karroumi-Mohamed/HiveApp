package com.hiveapp.shared.payment;

/**
 * Swappable payment gateway contract.
 *
 * Swap implementations via Spring @Primary or @ConditionalOnProperty.
 * Development simulators must return false from {@link #trustedForSettlement()}.
 * Only an installed real provider may opt into trusted asynchronous confirmation.
 */
public interface PaymentGateway {

    default boolean trustedForSettlement() {
        return false;
    }

    /**
     * Charge the account for a subscription period or one-time add-on.
     */
    PaymentResult charge(PaymentRequest request);

    /**
     * Refund a previous charge by transaction ID.
     */
    PaymentResult refund(String transactionId, java.math.BigDecimal amount);
}
