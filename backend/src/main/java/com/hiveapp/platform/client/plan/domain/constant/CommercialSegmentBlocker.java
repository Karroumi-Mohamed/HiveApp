package com.hiveapp.platform.client.plan.domain.constant;

/** Stable lifecycle/evaluation blockers rendered by operational clients. */
public enum CommercialSegmentBlocker {
    EMPTY_AUDIENCE,
    AUDIENCE_EXCEEDS_ACTIVATION_LIMIT,
    NOT_DRAFT,
    NOT_ACTIVE,
    NOT_LATEST_REVISION,
    HAS_POLICY_REFERENCES,
    HAS_ACTIVATION_HISTORY,
    ACTIVE_SUCCESSOR_EXISTS
}
