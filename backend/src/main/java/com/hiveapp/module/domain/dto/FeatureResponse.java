package com.hiveapp.module.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class FeatureResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private boolean active;
    private int sortOrder;
    private UUID moduleId;
    private String moduleCode;
}
