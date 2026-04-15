package com.hiveapp.platform.client.member.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record AddMemberRequest(
    @NotNull UUID userId,
    @NotBlank String displayName
) {}
