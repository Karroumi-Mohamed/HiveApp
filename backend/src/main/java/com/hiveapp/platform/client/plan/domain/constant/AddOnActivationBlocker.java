package com.hiveapp.platform.client.plan.domain.constant;

/** Stable, machine-readable reasons an AddOn cannot be activated from its reviewed state. */
public enum AddOnActivationBlocker {
    WRONG_LIFECYCLE_STATE,
    ARCHIVED_TERMINAL,
    NO_FEATURES,
    FEATURE_CONFIGURATION_INVALID,
    NO_APPLICABLE_PRICE,
    TARGET_PLAN_MISSING,
    TARGET_PLAN_INCOMPATIBLE,
    NO_COMPATIBLE_ACTIVE_PLAN,
    DEPENDENT_ADD_ON_REQUIRES_MIGRATION,
    TARGETED_QUOTA_PACKAGE_REQUIRES_MIGRATION
}
