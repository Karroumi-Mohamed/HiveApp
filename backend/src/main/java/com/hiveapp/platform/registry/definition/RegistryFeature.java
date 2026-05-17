package com.hiveapp.platform.registry.definition;

public final class RegistryFeature {

    public static final String KEY = "registry";
    public static final String CODE = "platform." + KEY;

    private RegistryFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Platform Registry")
                .description("Platform module, feature, permission, and catalog registry management")
                .sortOrder(10)
                .build();
    }
}
