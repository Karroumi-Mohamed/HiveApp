package com.hiveapp.platform.registry.definition;

public final class CommunicationsFeature {
    public static final String KEY = "communications";
    public static final String CODE = "platform." + KEY;

    private CommunicationsFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Platform Communications")
                .description("Credential-email delivery operations without stored message secrets")
                .sortOrder(56)
                .build();
    }
}
