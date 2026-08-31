package com.hiveapp.platform.registry.definition;

public final class ActivitiesFeature {
    public static final String KEY = "activities";
    public static final String CODE = "platform." + KEY;

    private ActivitiesFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Platform Activities")
                .description("Append-only platform mutation and security investigation evidence")
                .sortOrder(55)
                .build();
    }
}
