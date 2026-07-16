package com.hiveapp.platform.registry.definition;

public final class CompanyFeature {

    public static final String KEY = "company";
    public static final String CODE = "platform." + KEY;
    public static final String CREATE = "create";
    public static final String READ_ALL = "read_all";
    public static final String READ_SINGLE = "read_single";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";
    public static final String REACTIVATE = "reactivate";

    private CompanyFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Companies")
                .description("Client workspace company profile management")
                .b2bDelegatableActions(READ_SINGLE)
                .sortOrder(30)
                .build();
    }
}
