package com.hiveapp.platform.client.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;

public record SubscriptionDto(
        UUID id,
        PlanSummaryDto plan,
        SubscriptionStatus status,
        BigDecimal currentPrice,
        LocalDateTime currentPeriodEnd
) {
    public record PlanSummaryDto(String code, String name, BigDecimal basePrice) {}
}
