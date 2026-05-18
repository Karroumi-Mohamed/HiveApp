package com.hiveapp.platform.registry.definition;

import com.hiveapp.shared.quota.QuotaSlot;

import java.util.List;

public final class WorkspaceFeature {

    public static final String KEY = "workspace";
    public static final String CODE = "platform." + KEY;
    public static final String MEMBERS = "members";
    public static final String COMPANIES = "companies";

    private WorkspaceFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Workspace")
                .description("Client account and workspace shell management")
                .sortOrder(10)
                .quotas(List.of(
                        QuotaSlot.count(MEMBERS, "persons"),
                        QuotaSlot.count(COMPANIES, "companies")
                ))
                .build();
    }
}
