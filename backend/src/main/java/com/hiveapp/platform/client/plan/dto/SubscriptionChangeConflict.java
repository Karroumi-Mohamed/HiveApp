package com.hiveapp.platform.client.plan.dto;

public record SubscriptionChangeConflict(
        String code,
        String featureCode,
        String resource,
        Long currentUsage,
        Long requestedLimit,
        String message
) {}
