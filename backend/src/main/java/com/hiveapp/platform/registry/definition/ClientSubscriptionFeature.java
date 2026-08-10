package com.hiveapp.platform.registry.definition;

public final class ClientSubscriptionFeature {

    public static final String KEY = "subscription";
    public static final String CODE = "platform." + KEY;
    public static final String CATALOG = "catalog";
    public static final String PREVIEW = "preview";
    public static final String APPLY = "apply";

    private ClientSubscriptionFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Workspace Subscription")
                .description("Client workspace subscription visibility and self-service")
                .ownerOnlyActions(APPLY)
                .sortOrder(70)
                .build();
    }
}
