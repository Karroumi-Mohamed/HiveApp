package com.hiveapp.platform.client.plan.dto;

public record ClientSubscriptionChangeApplyResponse(
        SubscriptionDto subscription,
        ClientSubscriptionChangePreviewResponse preview,
        ClientSubscriptionChangeOperationDto operation
) {
    public static ClientSubscriptionChangeApplyResponse from(SubscriptionChangeApplyResponse source) {
        return new ClientSubscriptionChangeApplyResponse(
                source.subscription(), ClientSubscriptionChangePreviewResponse.from(source.preview()),
                ClientSubscriptionChangeOperationDto.from(source.operation()));
    }
}
