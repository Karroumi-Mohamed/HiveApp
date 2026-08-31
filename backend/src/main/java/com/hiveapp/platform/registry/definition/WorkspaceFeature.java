package com.hiveapp.platform.registry.definition;

public final class WorkspaceFeature {

    public static final String KEY = "workspace";
    public static final String CODE = "platform." + KEY;
    public static final String READ = "read";
    public static final String DELETE = "delete";

    private WorkspaceFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Workspace")
                .description("Client account and workspace shell management")
                .ownerOnlyActions(DELETE)
                .sortOrder(10)
                .build();
    }
}
