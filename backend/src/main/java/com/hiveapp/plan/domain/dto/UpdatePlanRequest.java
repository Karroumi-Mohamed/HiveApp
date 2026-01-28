package com.hiveapp.plan.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class UpdatePlanRequest {

    @Size(max = 100)
    private String name;

    private String description;

    @DecimalMin(value = "0.00")
    private BigDecimal price;

    private String billingCycle;

    @Min(1)
    private Integer maxCompanies;

    @Min(1)
    private Integer maxMembers;
}
