package com.hiveapp.platform.registry.definition;

/** Platform-only campaign audience and lifecycle control plane. */
public final class CampaignsFeature {

    public static final String KEY = "campaigns";
    public static final String CODE = "platform." + KEY;

    private CampaignsFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Campaigns")
                .description("Versioned campaigns with reviewed audiences and operational scheduling")
                .sortOrder(31)
                .build();
    }
}
