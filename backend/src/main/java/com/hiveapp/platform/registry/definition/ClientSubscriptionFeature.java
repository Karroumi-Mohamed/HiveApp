package com.hiveapp.platform.registry.definition;

public final class ClientSubscriptionFeature {

    public static final String KEY = "subscription";
    public static final String CODE = "platform." + KEY;

    private ClientSubscriptionFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Workspace Subscription")
                .description("Client workspace subscription visibility and self-service")
                .sortOrder(70)
                .build();
    }
}
