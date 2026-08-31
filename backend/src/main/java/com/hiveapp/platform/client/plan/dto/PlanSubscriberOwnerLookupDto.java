package com.hiveapp.platform.client.plan.dto;

public record PlanSubscriberOwnerLookupDto(
        String ownerEmail,
        PlanSubscriberDto subscriber
) {}
