package com.hiveapp.platform.registry.definition;

public final class ObservabilityFeature {
    public static final String KEY = "observability";
    public static final String CODE = "platform." + KEY;

    private ObservabilityFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Platform Observability")
                .description("Safe health, backlog, and external-log investigation access")
                .sortOrder(57)
                .build();
    }
}
