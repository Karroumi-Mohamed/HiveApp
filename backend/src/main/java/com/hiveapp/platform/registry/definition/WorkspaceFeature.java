package com.hiveapp.platform.registry.definition;

import com.hiveapp.shared.quota.QuotaSlot;

import java.util.List;

public final class WorkspaceFeature {

    public static final String KEY = "workspace";
    public static final String CODE = "platform." + KEY;
    public static final String MEMBERS = "members";
    public static final String COMPANIES = "companies";
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
                .quotas(List.of(
                        QuotaSlot.count(MEMBERS, "persons"),
                        QuotaSlot.count(COMPANIES, "companies")
                ))
                .build();
    }
}
