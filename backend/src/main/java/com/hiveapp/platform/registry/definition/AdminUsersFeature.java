package com.hiveapp.platform.registry.definition;

public final class AdminUsersFeature {

    public static final String KEY = "admin_users";
    public static final String CODE = "platform." + KEY;

    private AdminUsersFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Platform Admin Users")
                .description("Platform control-plane admin user and admin role assignment management")
                .sortOrder(50)
                .build();
    }
}
