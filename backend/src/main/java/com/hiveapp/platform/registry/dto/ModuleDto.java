package com.hiveapp.platform.registry.dto;

import java.util.List;
import java.util.UUID;

public record ModuleDto(
    UUID id, 
    String code, 
    List<FeatureDto> features
) {}
