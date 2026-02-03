package com.hiveapp.admin.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AdminPermissionResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private UUID moduleId;
    private String action;
    private String resource;
}
