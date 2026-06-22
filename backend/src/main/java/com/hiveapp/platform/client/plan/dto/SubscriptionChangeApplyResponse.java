package com.hiveapp.platform.client.plan.dto;

public record SubscriptionChangeApplyResponse(
        SubscriptionDto subscription,
        SubscriptionChangePreviewResponse preview
) {}
