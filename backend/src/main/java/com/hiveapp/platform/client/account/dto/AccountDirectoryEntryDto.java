package com.hiveapp.platform.client.account.dto;

import java.util.UUID;

public record AccountDirectoryEntryDto(
        UUID id,
        String name,
        String slug,
        String ownerEmail,
        boolean active
) {
}
