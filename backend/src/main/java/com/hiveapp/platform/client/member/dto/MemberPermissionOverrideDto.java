package com.hiveapp.platform.client.member.dto;

import java.util.UUID;
import java.time.Instant;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope;

public record MemberPermissionOverrideDto(
        UUID id,
        UUID memberId,
        PermissionOverrideScope scope,
        UUID companyId,
        String permissionCode,
        PermissionOverrideDecision decision,
        String reason,
        UUID createdByMemberId,
        Instant expiresAt,
        boolean effective,
        Instant createdAt,
        Instant updatedAt
) {
}
