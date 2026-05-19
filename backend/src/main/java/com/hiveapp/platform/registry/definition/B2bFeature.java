package com.hiveapp.platform.registry.definition;

public final class B2bFeature {

    public static final String KEY = "b2b";
    public static final String CODE = "platform." + KEY;

    private B2bFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("B2B Collaboration")
                .description("Client workspace B2B collaboration request and delegation management")
                .sortOrder(60)
                .build();
    }
}
