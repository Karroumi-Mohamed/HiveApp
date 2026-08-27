package com.hiveapp.platform.admin.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Operator-facing account row for subscription operations. The latest subscription is a
 * deterministic history record, not an assertion that every status is currently entitled.
 */
public record SubscriptionAccountOperationalListItemDto(
        UUID id,
        String name,
        String slug,
        boolean active,
        Instant createdAt,
        LatestSubscriptionSummary latestSubscription
) {
}
