package com.hiveapp.platform.registry.definition;

public final class CommercialAvailabilityFeature {

    public static final String KEY = "commercial_availability";
    public static final String CODE = "platform." + KEY;

    private CommercialAvailabilityFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Commercial Availability")
                .description("Plan extension policy, direct-sales visibility, and compatibility inspection")
                .sortOrder(27)
                .build();
    }
}
