package com.hiveapp.platform.registry.definition;

public final class PlansFeature {

    public static final String KEY = "plans";
    public static final String CODE = "platform." + KEY;

    private PlansFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Plan Catalog")
                .description("Platform plan catalog and plan-feature configuration")
                .sortOrder(20)
                .build();
    }
}
