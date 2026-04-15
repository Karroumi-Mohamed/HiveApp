package com.hiveapp.shared.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Development payment gateway — always succeeds, never charges anything.
 * Replace with a real implementation (Stripe, etc.) via @Primary or @ConditionalOnProperty
 * when moving to production.
 */
@Slf4j
@Service
public class DevPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(PaymentRequest request) {
        String txId = "DEV-" + UUID.randomUUID();
        log.info("[DEV] Simulated charge: {} {} for account {} — txId: {}",
                request.amount(), request.currency(), request.accountId(), txId);
        return new PaymentResult(txId, PaymentStatus.SUCCESS, null);
    }

    @Override
    public PaymentResult refund(String transactionId, BigDecimal amount) {
        String txId = "DEV-REFUND-" + UUID.randomUUID();
        log.info("[DEV] Simulated refund of {} for tx {} — refundTxId: {}", amount, transactionId, txId);
        return new PaymentResult(txId, PaymentStatus.SUCCESS, null);
    }
}
