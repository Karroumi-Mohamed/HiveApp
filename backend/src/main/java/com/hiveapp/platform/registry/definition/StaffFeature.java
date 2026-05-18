package com.hiveapp.platform.registry.definition;

public final class StaffFeature {

    public static final String KEY = "staff";
    public static final String CODE = "platform." + KEY;

    private StaffFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Staff")
                .description("Client workspace member and member permission management")
                .sortOrder(20)
                .build();
    }
}
