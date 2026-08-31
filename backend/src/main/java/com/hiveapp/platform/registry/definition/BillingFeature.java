package com.hiveapp.platform.registry.definition;

public final class BillingFeature {
    public static final String KEY = "billing";
    public static final String CODE = "platform." + KEY;

    private BillingFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Billing Operations")
                .description("Invoices, settlements, credits, refunds, and reconciliation")
                .sortOrder(35)
                .build();
    }
}
