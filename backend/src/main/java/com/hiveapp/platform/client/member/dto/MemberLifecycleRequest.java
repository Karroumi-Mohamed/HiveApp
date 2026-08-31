package com.hiveapp.platform.client.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberLifecycleRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
