package com.hiveapp.platform.registry.definition;

public final class AnalyticsFeature {
    public static final String KEY = "analytics";
    public static final String CODE = "platform." + KEY;

    private AnalyticsFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Commercial Analytics")
                .description("Truthful commercial facts, trends, and operational attention")
                .sortOrder(50)
                .build();
    }
}
