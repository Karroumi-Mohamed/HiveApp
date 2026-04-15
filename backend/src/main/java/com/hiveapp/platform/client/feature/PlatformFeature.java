package com.hiveapp.platform.client.feature;

import com.hiveapp.shared.quota.AppFeature;
import com.hiveapp.shared.quota.QuotaSlot;

import java.util.List;

/**
 * Platform-level features governing workspace-wide limits.
 * These apply across the entire Account, not per Company or per module.
 *
 * WORKSPACE: controls how many Members and Companies an Account can have.
 *
 * NOTE: Feature codes SHOULD be derived from Permissionizer-generated path() calls
 * once the permission tree for the platform module is declared.
 * Hardcoded strings are used here until that tree exists.
 */
public enum PlatformFeature implements AppFeature {

    WORKSPACE("platform.workspace", List.of(
            QuotaSlot.count("members",   "persons"),
            QuotaSlot.count("companies", "companies")
    ));

    // Slot name constants — use these in service code, never raw strings
    public static final String MEMBERS   = "members";
    public static final String COMPANIES = "companies";

    private final String code;
    private final List<QuotaSlot> slots;

    PlatformFeature(String code, List<QuotaSlot> slots) {
        this.code  = code;
        this.slots = slots;
    }

    @Override public String code() { return code; }
    @Override public List<QuotaSlot> quotaSlots() { return slots; }
}
