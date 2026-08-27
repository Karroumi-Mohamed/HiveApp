package com.hiveapp.platform.client.plan.domain.constant;

/** Stable, machine-readable reasons a Plan cannot be activated from its reviewed state. */
public enum PlanActivationBlocker {
    WRONG_LIFECYCLE_STATE,
    ARCHIVED_TERMINAL,
    NO_INCLUDED_FEATURES,
    FEATURE_CONFIGURATION_INVALID,
    INCOMPLETE_QUOTA_CONFIGURATION,
    NO_APPLICABLE_PRICE
}
