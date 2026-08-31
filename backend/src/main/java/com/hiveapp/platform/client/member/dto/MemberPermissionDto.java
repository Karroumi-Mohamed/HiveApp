package com.hiveapp.platform.client.member.dto;

import java.util.Set;
import java.util.UUID;

public record MemberPermissionDto(
    UUID memberId,
    UUID accountId,
    UUID companyId,
    boolean isOwner,
    Set<String> permissions
) {}
