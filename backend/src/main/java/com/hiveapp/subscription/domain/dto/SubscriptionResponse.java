package com.hiveapp.subscription.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class SubscriptionResponse {
    private UUID id;
    private UUID accountId;
    private UUID planId;
    private String status;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private Instant cancelledAt;
    private Instant createdAt;
}
