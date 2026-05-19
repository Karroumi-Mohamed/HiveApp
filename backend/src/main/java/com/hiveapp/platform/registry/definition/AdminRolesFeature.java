package com.hiveapp.platform.registry.definition;

public final class AdminRolesFeature {

    public static final String KEY = "roles";
    public static final String CODE = "platform." + KEY;

    private AdminRolesFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Platform Admin Roles")
                .description("Platform control-plane admin role and permission grant management")
                .sortOrder(40)
                .build();
    }
}
