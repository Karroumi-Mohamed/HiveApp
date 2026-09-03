package com.hiveapp.platform.client.plan.domain.constant;

/** Durable lifecycle of one Account-specific commercial agreement. */
public enum SpecialAgreementStatus {
    SCHEDULED,
    AWAITING_SETTLEMENT,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    NEEDS_ATTENTION
}
