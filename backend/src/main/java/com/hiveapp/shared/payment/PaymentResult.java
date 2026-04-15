package com.hiveapp.shared.payment;

public record PaymentResult(
        String transactionId,
        PaymentStatus status,
        String failureReason
) {
    public boolean succeeded() {
        return status == PaymentStatus.SUCCESS;
    }
}
