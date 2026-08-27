package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;

import java.time.Instant;
import java.util.UUID;

/** Operator read model with durable provenance that is intentionally absent from the client DTO. */
public record AdminSubscriptionChangeOperationDto(
        UUID id,
        Instant createdAt,
        Instant updatedAt,
        SubscriptionChangeTiming timing,
        SubscriptionChangeStatus status,
        Instant effectiveAt,
        String sourcePlanCode,
        String targetPlanCode,
        String attentionReason,
        SubscriptionCheckoutDto checkout,
        SubscriptionChangeOrigin requestOrigin,
        UUID requestedByUserId,
        String requestReason,
        SubscriptionChangeOrigin cancellationOrigin,
        UUID cancelledByUserId,
        String cancellationReason,
        Instant cancelledAt
) {}
