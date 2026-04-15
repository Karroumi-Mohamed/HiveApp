package com.hiveapp.shared.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
        UUID accountId,
        BigDecimal amount,
        String currency,
        String description
) {}
