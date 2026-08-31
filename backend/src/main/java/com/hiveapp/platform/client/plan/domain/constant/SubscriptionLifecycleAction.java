package com.hiveapp.platform.client.plan.domain.constant;

public enum SubscriptionLifecycleAction {
    CANCEL_AT_PERIOD_END,
    KEEP_RENEWING,
    CANCEL_IMMEDIATELY,
    SUSPEND,
    RESTORE,
    EXTEND_GRACE
}
