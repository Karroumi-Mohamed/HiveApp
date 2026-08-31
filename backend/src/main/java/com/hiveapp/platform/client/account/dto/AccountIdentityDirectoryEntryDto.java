package com.hiveapp.platform.client.account.dto;

import java.util.UUID;

/** Internal cross-domain identity projection; never returned by the safe Account chooser. */
public record AccountIdentityDirectoryEntryDto(
        UUID id,
        String name,
        String slug,
        String ownerEmail,
        boolean active
) {}
