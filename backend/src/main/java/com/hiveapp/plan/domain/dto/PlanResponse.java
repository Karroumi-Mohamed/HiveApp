package com.hiveapp.plan.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PlanResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private String billingCycle;
    private int maxCompanies;
    private int maxMembers;
    private boolean active;
    private List<PlanFeatureResponse> features;
}
