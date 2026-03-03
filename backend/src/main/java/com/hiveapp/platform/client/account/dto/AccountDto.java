package com.hiveapp.platform.client.account.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountDto(
        UUID id,
        UUID ownerId,
        String name,
        String slug,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt) {}
