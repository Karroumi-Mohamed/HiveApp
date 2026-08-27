package com.hiveapp.platform.registry.definition;

/** Platform-only control surface for safe, typed Account audience definitions. */
public final class AccountSegmentsFeature {

    public static final String KEY = "segments";
    public static final String CODE = "platform." + KEY;

    private AccountSegmentsFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Account Segments")
                .description("Versioned explicit or typed Account audiences with immutable activation snapshots")
                .sortOrder(30)
                .build();
    }
}
