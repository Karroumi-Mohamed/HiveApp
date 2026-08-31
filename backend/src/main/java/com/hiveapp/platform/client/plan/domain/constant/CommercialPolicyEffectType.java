package com.hiveapp.platform.client.plan.domain.constant;

/** Closed effect vocabulary. None of these effects directly settles money or mutates a subscription. */
public enum CommercialPolicyEffectType {
    FIXED_SUBSCRIPTION_PRICE,
    FIXED_DISCOUNT,
    PERCENTAGE_DISCOUNT,
    ALLOW_PRODUCT_SELECTION,
    BLOCK_PRODUCT_SELECTION,
    ADDITIVE_QUOTA_BONUS,
    GRANT_ADD_ON,
    GRANT_QUOTA_PACKAGE,
    BLOCK_FEATURE;

    public boolean isRestriction() {
        return this == BLOCK_PRODUCT_SELECTION || this == BLOCK_FEATURE;
    }

    public boolean isGrant() {
        return this == ALLOW_PRODUCT_SELECTION
                || this == ADDITIVE_QUOTA_BONUS
                || this == GRANT_ADD_ON
                || this == GRANT_QUOTA_PACKAGE;
    }
}
