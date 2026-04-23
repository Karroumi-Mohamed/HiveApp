package com.hiveapp.platform.client.member.dto;

import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(
        @Size(max = 100) String displayName
) {}
