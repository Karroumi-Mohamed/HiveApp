package com.hiveapp.platform.admin.dto;

import java.util.UUID;

/**
 * An operator holding a role. Enough to recognise the person and judge whether a change to the
 * role matters for them — not a full operator record.
 */
public record RoleHolderDto(
        UUID adminUserId,
        String email,
        String firstName,
        String lastName,
        boolean isActive,
        boolean isSuperAdmin
) {}
