package com.hiveapp.permission.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PermissionResponse {
    private UUID id;
    private UUID featureId;
    private String code;
    private String name;
    private String description;
    private String action;
    private String resource;
}
