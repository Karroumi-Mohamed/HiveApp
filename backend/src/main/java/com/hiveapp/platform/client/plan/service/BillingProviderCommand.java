package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import java.math.BigDecimal;
import java.util.UUID;

public record BillingProviderCommand(
        UUID commandId,
        BillingOutboxOperation operation,
        UUID aggregateId,
        UUID accountId,
        BigDecimal amount,
        String currencyCode,
        String paymentReference,
        String description,
        String idempotencyKey
) {}
