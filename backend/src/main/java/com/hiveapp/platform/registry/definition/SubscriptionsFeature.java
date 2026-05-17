package com.hiveapp.platform.registry.definition;

public final class SubscriptionsFeature {

    public static final String KEY = "subscriptions";
    public static final String CODE = "platform." + KEY;

    private SubscriptionsFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Client Subscriptions")
                .description("Platform administration of client account subscriptions and overrides")
                .sortOrder(30)
                .build();
    }
}
