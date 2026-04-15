package com.hiveapp.platform.client.feature;

import com.hiveapp.shared.quota.AppFeature;
import com.hiveapp.shared.quota.QuotaSlot;

import java.util.List;

/**
 * Platform-level features.
 *
 * Feature code convention: "platform.[classLevelPermissionNodeKey]"
 * Must match the @PermissionNode class key on each controller, prefixed by the
 * root platform node — so PermissionSeeder can join permissions to features automatically.
 *
 * Quota slots only on WORKSPACE — all other platform features are boolean-access (no quotas).
 * ERP module features live in their own AppFeature enums (e.g. HrFeature, CrmFeature).
 */
public enum PlatformFeature implements AppFeature {

    WORKSPACE("platform.workspace", List.of(
            QuotaSlot.count("members",   "persons"),
            QuotaSlot.count("companies", "companies")
    )),

    COMPANY("platform.company",      List.of()),
    STAFF("platform.staff",          List.of()),
    SUBSCRIPTION("platform.subscription", List.of()),
    B2B("platform.b2b",              List.of()),
    RBAC("platform.rbac",            List.of());

    // Quota slot name constants (WORKSPACE only)
    public static final String MEMBERS   = "members";
    public static final String COMPANIES = "companies";

    private final String code;
    private final List<QuotaSlot> slots;

    PlatformFeature(String code, List<QuotaSlot> slots) {
        this.code  = code;
        this.slots = slots;
    }

    @Override public String code()             { return code; }
    @Override public List<QuotaSlot> quotaSlots() { return slots; }
}
