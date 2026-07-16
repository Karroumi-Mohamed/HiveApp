package com.hiveapp.platform.registry.definition;

public final class OrganizationFeature {
    public static final String KEY = "organization";
    public static final String CODE = "platform." + KEY;
    public static final String LIST_GROUPS = "list_groups";
    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String MOVE = "move";
    public static final String REORDER = "reorder";
    public static final String ARCHIVE = "archive";
    public static final String RESTORE = "restore";
    public static final String DELETE = "delete";
    public static final String LIST_MEMBERSHIPS = "list_memberships";
    public static final String LIST_MEMBER_PLACEMENTS = "list_member_placements";
    public static final String PUT_MEMBERSHIP = "put_membership";
    public static final String REMOVE_MEMBERSHIP = "remove_membership";
    public static final String CREATE_TEMPLATE = "create_template";
    public static final String LIST_TEMPLATES = "list_templates";
    public static final String PREVIEW_TEMPLATE = "preview_template";
    public static final String INSTANTIATE_TEMPLATE = "instantiate_template";
    public static final String DELETE_TEMPLATE = "delete_template";

    private OrganizationFeature() {}

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Organization Groups")
                .description("Company organization folders, memberships, positions, and structure templates")
                .sortOrder(35)
                .build();
    }
}
