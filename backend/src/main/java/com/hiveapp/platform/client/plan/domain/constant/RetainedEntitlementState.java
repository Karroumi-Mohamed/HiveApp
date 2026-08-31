package com.hiveapp.platform.client.plan.domain.constant;

/** Coarse, client-safe state for a product already pinned in the Account's entitlement snapshot. */
public enum RetainedEntitlementState {
    SELECTABLE,
    RETAINED_ONLY,
    HISTORICAL_ONLY
}
