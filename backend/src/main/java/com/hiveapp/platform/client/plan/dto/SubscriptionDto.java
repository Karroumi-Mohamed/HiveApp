package com.hiveapp.platform.client.plan.dto;

import java.util.UUID;
import java.time.LocalDateTime;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;

public record SubscriptionDto(
    UUID id, 
    PlanSummaryDto plan, 
    SubscriptionStatus status, 
    LocalDateTime currentPeriodEnd
) {
    public record PlanSummaryDto(String code, String name) {}
}
