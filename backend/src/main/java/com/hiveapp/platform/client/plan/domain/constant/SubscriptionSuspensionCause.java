package com.hiveapp.platform.client.plan.domain.constant;

/** Why a current subscription is suspended; restoration rules depend on this distinction. */
public enum SubscriptionSuspensionCause {
    COLLECTION,
    OPERATOR
}
