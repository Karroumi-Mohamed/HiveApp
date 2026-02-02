package com.hiveapp.member.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MemberResponse {
    private UUID id;
    private UUID userId;
    private UUID accountId;
    private String displayName;
    private boolean owner;
    private boolean active;
    private Instant joinedAt;
    private List<MemberRoleResponse> roles;
}
