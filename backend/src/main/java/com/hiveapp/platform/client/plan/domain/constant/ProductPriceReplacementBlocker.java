package com.hiveapp.platform.client.plan.domain.constant;

public enum ProductPriceReplacementBlocker {
    CURRENT_NOT_ACTIVE,
    SUCCESSOR_NOT_DRAFT,
    SUCCESSOR_NOT_DIRECT_REVISION,
    COMMERCIAL_TUPLE_MISMATCH,
    CUTOFF_NOT_FUTURE,
    CURRENT_DOES_NOT_COVER_CUTOFF,
    OWNER_NOT_ACTIVE,
    OTHER_ACTIVE_WINDOW_OVERLAP
}
