package com.hiveapp.platform.client.plan.domain.constant;

/**
 * Typed commercial review operations whose evidence can be signed by the shared preview-token
 * protocol. New commercial preview flows add a kind here instead of introducing another signer.
 */
public enum CommercialPreviewKind {
    PLAN_ACTIVATION,
    ADD_ON_ACTIVATION,
    QUOTA_PACKAGE_ACTIVATION,
    PRODUCT_PRICE_ACTIVATION,
    PRODUCT_PRICE_CHANGE,
    PLAN_DELETION,
    PLAN_AVAILABILITY,
    ADD_ON_VISIBILITY,
    QUOTA_PACKAGE_VISIBILITY,
    SUBSCRIPTION_CHANGE,
    ADMIN_SUBSCRIPTION_CHANGE,
    SUBSCRIPTION_CHANGE_JOB,
    SUBSCRIPTION_REPRICING,
    SUBSCRIPTION_LIFECYCLE,
    COMMERCIAL_POLICY_ACTIVATION,
    COMMERCIAL_SEGMENT_ACTIVATION,
    COMMERCIAL_CAMPAIGN_SCHEDULE,
    COMMERCIAL_OFFER_PUBLICATION,
    COMMERCIAL_OFFER_REDEMPTION,
    SPECIAL_COMMERCIAL_AGREEMENT
}
