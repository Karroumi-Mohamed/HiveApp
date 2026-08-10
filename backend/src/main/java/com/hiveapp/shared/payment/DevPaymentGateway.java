package com.hiveapp.shared.payment;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Explicit dev/test simulator. Its result is attempt telemetry only and is never trusted
 * as settlement confirmation by the subscription checkout flow.
 */
@Slf4j
@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class DevPaymentGateway implements PaymentGateway {

    private final BillingProperties billingProperties;

    @Override
    public PaymentResult charge(PaymentRequest request) {
        String txId = "DEV-" + UUID.randomUUID();
        PaymentStatus outcome = billingProperties.getSimulator().getOutcome();
        String failureReason = outcome == PaymentStatus.FAILED
                ? billingProperties.getSimulator().getFailureReason()
                : null;
        log.info("[DEV] Simulated payment attempt: {} {} for account {} — outcome: {}, txId: {}",
                request.amount(), request.currency(), request.accountId(), outcome, txId);
        return new PaymentResult(txId, outcome, failureReason);
    }

    @Override
    public PaymentResult refund(String transactionId, BigDecimal amount) {
        String txId = "DEV-REFUND-" + UUID.randomUUID();
        PaymentStatus outcome = billingProperties.getSimulator().getOutcome();
        String failureReason = outcome == PaymentStatus.FAILED
                ? billingProperties.getSimulator().getFailureReason()
                : null;
        log.info("[DEV] Simulated refund attempt of {} for tx {} — outcome: {}, refundTxId: {}",
                amount, transactionId, outcome, txId);
        return new PaymentResult(txId, outcome, failureReason);
    }
}
