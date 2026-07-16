package com.hiveapp.platform.client.company.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InstantiateGroupTemplateRequest(@NotNull UUID companyId, UUID parentId) {}
