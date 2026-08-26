package com.hiveapp.platform.client.plan.domain.constant;

/**
 * Stable, machine-readable facts that keep an operational product revision from being sellable
 * now. These are sale-readiness signals, not lifecycle-command vetoes: for example, activation may
 * publish a reviewed price draft even while {@link #NO_ACTIVE_PRICE} is present on the draft row.
 */
public enum CommercialProductBlocker {
    NOT_PUBLISHED,
    PAUSED,
    ARCHIVED_TERMINAL,
    NO_ACTIVE_PRICE,
    NO_PRICE_STARTING_POINT,
    PUBLISHED_PRICE_HISTORY,
    REFERENCED_BY_ADD_ON,
    REFERENCED_BY_QUOTA_PACKAGE,
    NO_FEATURES,
    NO_INCLUDED_FEATURES,
    DRAFT_SUCCESSOR_EXISTS,
    NOT_LATEST_REVISION,
    DEFAULT_PLAN_LOCKED
}
