package com.hiveapp.member.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class MemberRoleResponse {
    private UUID id;
    private UUID roleId;
    private UUID companyId;
    private boolean accountWide;
    private Instant assignedAt;
}
