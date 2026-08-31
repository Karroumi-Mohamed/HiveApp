package com.hiveapp.platform.client.plan.dto;

public record CommercialOverviewDto(
        long totalPlans,
        long draftPlans,
        long activePlans,
        long inactivePlans,
        long archivedPlans,
        long currentSubscriptions,
        long activeSubscriptions,
        long trialingSubscriptions,
        long pastDueSubscriptions,
        long suspendedSubscriptions,
        long pendingCheckouts,
        long scheduledChanges,
        long changesNeedingAttention
) {
}
