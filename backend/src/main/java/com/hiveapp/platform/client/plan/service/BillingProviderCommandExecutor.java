package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.shared.payment.PaymentGateway;
import com.hiveapp.shared.payment.PaymentRequest;
import com.hiveapp.shared.payment.PaymentResult;
import com.hiveapp.shared.payment.RefundRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Executes external provider I/O and fails if a database transaction leaks into the call. */
@Component
@RequiredArgsConstructor
public class BillingProviderCommandExecutor {
    private final ObjectProvider<PaymentGateway> gateways;

    @Transactional(propagation = Propagation.NEVER)
    public Result execute(BillingProviderCommand command) {
        PaymentGateway gateway = gateways.orderedStream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No payment provider is configured"));
        PaymentResult result = command.operation() == BillingOutboxOperation.CHARGE
                ? gateway.charge(new PaymentRequest(
                command.accountId(),
                command.amount(),
                command.currencyCode(),
                command.description(),
                command.idempotencyKey()))
                : gateway.refund(new RefundRequest(
                command.accountId(),
                command.paymentReference(),
                command.amount(),
                command.currencyCode(),
                command.idempotencyKey()));
        if (result == null || result.status() == null) {
            throw new IllegalStateException("Payment provider returned an invalid result");
        }
        return new Result(result, gateway.trustedForSettlement());
    }

    public record Result(PaymentResult paymentResult, boolean trustedForSettlement) {}
}
