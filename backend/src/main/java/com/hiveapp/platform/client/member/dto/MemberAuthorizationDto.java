package com.hiveapp.platform.client.member.dto;

import java.util.List;

public record MemberAuthorizationDto(
        MemberDto member,
        List<MemberRoleAssignmentDto> roles,
        List<MemberPermissionOverrideDto> overrides
) {
    public MemberAuthorizationDto {
        roles = List.copyOf(roles);
        overrides = List.copyOf(overrides);
    }
}
