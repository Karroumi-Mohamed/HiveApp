package com.hiveapp.platform.registry.definition;

public final class WorkspaceRolesFeature {

    public static final String KEY = "rbac";
    public static final String CODE = "platform." + KEY;

    private WorkspaceRolesFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Workspace Roles")
                .description("Client workspace role and role-permission management")
                .sortOrder(40)
                .build();
    }
}
