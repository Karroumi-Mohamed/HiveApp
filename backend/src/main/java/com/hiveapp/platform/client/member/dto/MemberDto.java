package com.hiveapp.platform.client.member.dto;

import java.util.UUID;

public record MemberDto(
    UUID id, 
    UUID userId, 
    String displayName, 
    boolean isOwner, 
    boolean isActive
) {}
