package com.hiveapp.module.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ModuleResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private String icon;
    private boolean active;
    private int sortOrder;
    private List<FeatureResponse> features;
}
