package com.hiveapp.platform.client.plan.domain.constant;

/** Stable reasons an AddOn successor cannot retire the currently published revision. */
public enum AddOnActivationBlocker {
    DEPENDENT_ADD_ON_REQUIRES_MIGRATION,
    TARGETED_QUOTA_PACKAGE_REQUIRES_MIGRATION
}
