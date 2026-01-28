package com.hiveapp.plan.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class PlanFeatureResponse {
    private UUID id;
    private UUID featureId;
    private Map<String, Object> config;
}
