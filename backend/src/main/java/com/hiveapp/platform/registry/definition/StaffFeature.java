package com.hiveapp.platform.registry.definition;

import com.hiveapp.shared.quota.QuotaSlot;

public final class StaffFeature {

    public static final String KEY = "staff";
    public static final String CODE = "platform." + KEY;
    public static final String MEMBERS = "members";

    private StaffFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Staff")
                .description("Client workspace member and member permission management")
                .sortOrder(20)
                .quota(QuotaSlot.count(MEMBERS, "persons"))
                .build();
    }
}
