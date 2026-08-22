package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreatePlanRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal price,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}", message = "must be a three-letter ISO currency code") String currencyCode,
        @NotNull BillingCycle billingCycle,
        /**
         * Optional initial composition, created in the same transaction as the plan. One invalid
         * feature rolls the whole creation back, so the draft that exists is always exactly the
         * draft the operator approved.
         */
        @Valid @Size(max = 100) List<AssignPlanFeatureRequest> features
) {
    /** Convenience for callers that create a bare plan without composition. */
    public CreatePlanRequest(
            String name,
            String description,
            BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle) {
        this(name, description, price, currencyCode, billingCycle, List.of());
    }
}
