package com.hiveapp.platform.client.feature;

import com.hiveapp.platform.generated.PlatformPermissions;
import com.hiveapp.shared.quota.AppFeature;
import com.hiveapp.shared.quota.QuotaSlot;

import java.util.List;

/**
 * Platform-level features.
 *
 * Feature codes are derived from the Permissionizer-generated PlatformPermissions tree via PATH constants.
 * If a controller key is renamed, PlatformPermissions.X.PATH will change and this enum
 * will fail to compile — forcing an intentional update. Never hardcode the path strings.
 *
 * Quota slots only on WORKSPACE — all other platform features are boolean-access (no quotas).
 * ERP module features live in their own AppFeature enums (e.g. HrFeature, CrmFeature).
 */
public enum PlatformFeature implements AppFeature {

    WORKSPACE(PlatformPermissions.Workspace.PATH, List.of(
            QuotaSlot.count("members",   "persons"),
            QuotaSlot.count("companies", "companies")
    )),

    COMPANY(PlatformPermissions.Company.PATH,           List.of()),
    STAFF(PlatformPermissions.Staff.PATH,               List.of()),
    SUBSCRIPTION(PlatformPermissions.Subscription.PATH, List.of()),
    B2B(PlatformPermissions.B2b.PATH,                   List.of()),
    RBAC(PlatformPermissions.Rbac.PATH,                 List.of());

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
