package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionChangeOperationDto(
        UUID id,
        SubscriptionChangeTiming timing,
        SubscriptionChangeStatus status,
        Instant effectiveAt,
        String sourcePlanCode,
        String targetPlanCode,
        String attentionReason
) {}
