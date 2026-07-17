package com.hiveapp.platform.registry.definition;

public final class WorkspaceRolesFeature {

    public static final String KEY = "rbac";
    public static final String CODE = "platform." + KEY;
    public static final String READ = "read";
    public static final String VIEW = "view";
    public static final String VIEW_COMPANY = "view_company";
    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";
    public static final String GRANT = "grant";
    public static final String REVOKE = "revoke";
    public static final String PERMISSION_CATALOG = "permission_catalog";
    public static final String IMPACT = "impact";
    public static final String ACTIVATE = "activate";
    public static final String DEACTIVATE = "deactivate";
    public static final String ARCHIVE = "archive";
    public static final String DUPLICATE = "duplicate";

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
