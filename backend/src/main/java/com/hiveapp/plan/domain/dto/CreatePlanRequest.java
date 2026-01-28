package com.hiveapp.plan.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CreatePlanRequest {

    @NotBlank(message = "Plan code is required")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Plan name is required")
    @Size(max = 100)
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00")
    private BigDecimal price;

    @NotBlank(message = "Billing cycle is required")
    private String billingCycle;

    @Min(1)
    private int maxCompanies;

    @Min(1)
    private int maxMembers;

    private List<UUID> featureIds;
}
