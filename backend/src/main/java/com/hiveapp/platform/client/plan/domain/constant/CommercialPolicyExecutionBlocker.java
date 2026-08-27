package com.hiveapp.platform.client.plan.domain.constant;

/** Phase-boundary blockers: definitions may activate, but subscriber mutation is not claimed. */
public enum CommercialPolicyExecutionBlocker {
    SUBSCRIPTION_OPERATION_ENGINE_NOT_CONNECTED,
    SCHEDULED_EXECUTION_NOT_AVAILABLE
}
