package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;

import java.time.Instant;
import java.util.UUID;

public record ClientSubscriptionChangeOperationDto(
        UUID id,
        Instant createdAt,
        Instant updatedAt,
        SubscriptionChangeTiming timing,
        SubscriptionChangeStatus status,
        Instant effectiveAt,
        String sourcePlanCode,
        String targetPlanCode,
        ClientSubscriptionAttentionCode attentionCode,
        ClientSubscriptionCheckoutDto checkout,
        ClientCommercialPolicyEvaluation commercialPolicyEvaluation
) {
    public static ClientSubscriptionChangeOperationDto from(SubscriptionChangeOperationDto source) {
        return new ClientSubscriptionChangeOperationDto(
                source.id(), source.createdAt(), source.updatedAt(), source.timing(), source.status(),
                source.effectiveAt(), source.sourcePlanCode(), source.targetPlanCode(),
                source.attentionReason() == null
                        ? null
                        : ClientSubscriptionAttentionCode.OPERATOR_ASSISTANCE_REQUIRED,
                ClientSubscriptionCheckoutDto.from(source.checkout()),
                ClientCommercialPolicyEvaluation.from(source.commercialPolicyEvaluation()));
    }
}
