package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.money.ExactDecimal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;

public record CreateQuotaPackageRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 160) String featureCode,
        @NotBlank @Size(max = 100) String resource,
        @Min(1) long capacityPerUnit,
        @ExactDecimal @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal price,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}", message = "must be a three-letter ISO currency code") String currencyCode,
        @NotNull BillingCycle billingCycle,
        boolean repeatable,
        @Min(1) int maximumQuantity,
        @Size(max = 100) Set<@NotBlank @Size(max = 100) String> allowedPlanCodes,
        @Size(max = 100) Set<@NotBlank @Size(max = 100) String> allowedAddOnCodes,
        ProductSalesVisibility salesVisibility
) {
    public CreateQuotaPackageRequest(
            String name, String description, String featureCode, String resource,
            long capacityPerUnit, BigDecimal price, String currencyCode, BillingCycle billingCycle,
            boolean repeatable, int maximumQuantity, Set<String> allowedPlanCodes,
            Set<String> allowedAddOnCodes
    ) {
        this(name, description, featureCode, resource, capacityPerUnit, price, currencyCode,
                billingCycle, repeatable, maximumQuantity, allowedPlanCodes, allowedAddOnCodes,
                ProductSalesVisibility.PUBLIC);
    }
}
