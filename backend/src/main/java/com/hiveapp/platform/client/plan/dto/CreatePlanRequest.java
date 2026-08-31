package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.money.ExactDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;

public record CreatePlanRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @ExactDecimal @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal price,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}", message = "must be a three-letter ISO currency code") String currencyCode,
        @NotNull BillingCycle billingCycle,
        /**
         * Optional initial composition, created in the same transaction as the plan. One invalid
         * feature rolls the whole creation back, so the draft that exists is always exactly the
         * draft the operator approved.
         */
        @Valid @Size(max = 100) List<AssignPlanFeatureRequest> features,
        PlanExtensionPolicy extensionPolicy,
        ProductSalesVisibility salesVisibility
) {
    /** Convenience for callers that create a bare plan without composition. */
    public CreatePlanRequest(
            String name,
            String description,
            BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle) {
        this(name, description, price, currencyCode, billingCycle, List.of(),
                PlanExtensionPolicy.OPEN_COMPATIBLE, ProductSalesVisibility.PUBLIC);
    }

    public CreatePlanRequest(
            String name, String description, BigDecimal price, String currencyCode,
            BillingCycle billingCycle, List<AssignPlanFeatureRequest> features) {
        this(name, description, price, currencyCode, billingCycle, features,
                PlanExtensionPolicy.OPEN_COMPATIBLE, ProductSalesVisibility.PUBLIC);
    }
}
