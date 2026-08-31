package com.hiveapp.platform.registry.definition;

/** Platform-only control surface for reviewed, typed commercial policy definitions. */
public final class CommercialPoliciesFeature {

    public static final String KEY = "commercial_policies";
    public static final String CODE = "platform." + KEY;

    private CommercialPoliciesFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Commercial Policies")
                .description("Reviewed Account and audience commercial exceptions with immutable evidence")
                .sortOrder(29)
                .build();
    }
}
