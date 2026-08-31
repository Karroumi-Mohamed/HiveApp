package com.hiveapp.platform.client.plan.domain.constant;

/** Names the backend authority that produced an extension-availability result. */
public enum ExtensionResolutionSource {
    PLAN_POLICY,
    PRODUCT_VISIBILITY,
    PRODUCT_LIFECYCLE,
    PRODUCT_TARGETING,
    REGISTRY,
    PLAN_COMPOSITION,
    DEPENDENCY,
    EXCLUSION,
    ENTITLEMENT,
    QUOTA_OWNERSHIP,
    PRICE_BOOK
}
