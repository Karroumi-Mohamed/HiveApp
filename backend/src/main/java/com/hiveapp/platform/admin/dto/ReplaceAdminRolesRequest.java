package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record ReplaceAdminRolesRequest(@NotNull Set<@NotNull UUID> roleIds) {
}
