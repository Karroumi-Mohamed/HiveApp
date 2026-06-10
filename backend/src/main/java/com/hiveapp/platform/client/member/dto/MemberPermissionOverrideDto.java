package com.hiveapp.platform.client.member.dto;

import java.util.UUID;

public record MemberPermissionOverrideDto(
        UUID memberId,
        UUID companyId,
        String permissionCode,
        boolean decision
) {
}
