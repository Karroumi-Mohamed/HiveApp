package com.hiveapp.shared.quota;

import java.util.List;

/**
 * Interface every feature enum in the system must implement.
 * Connects the compile-time feature declaration to the runtime quota enforcement.
 *
 * Feature code MUST be derived from the Permissionizer-generated class path() method,
 * not hardcoded as a string. This ensures compile-time safety on renames and deletes.
 *
 * Example:
 *   EMPLOYEES(HrPermissions.Employees.path(), List.of(QuotaSlot.count("persons", "persons")))
 */
public interface AppFeature {
    String code();
    List<QuotaSlot> quotaSlots();

    default boolean hasQuota() {
        return !quotaSlots().isEmpty();
    }
}
