package com.hiveapp.platform.client.company.dto;

import com.hiveapp.platform.client.company.domain.constant.GroupStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrganizationGroupDto(
        UUID id,
        UUID companyId,
        UUID parentId,
        String name,
        String description,
        int displayOrder,
        GroupStatus status,
        List<String> positionSuggestions,
        long directMemberCount,
        Instant createdAt,
        Instant updatedAt
) {}
