package com.hiveapp.platform.client.company.dto;

import com.hiveapp.platform.client.company.domain.constant.GroupStatus;
import com.hiveapp.platform.client.company.domain.constant.GroupTemplateScope;

import java.util.UUID;

public record GroupTemplateDto(
        UUID id,
        String name,
        GroupTemplateScope scope,
        GroupStatus status,
        UUID accountId,
        UUID companyId,
        int nodeCount
) {}
