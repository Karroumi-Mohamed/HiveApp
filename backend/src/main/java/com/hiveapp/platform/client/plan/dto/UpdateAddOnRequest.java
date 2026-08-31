package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.money.ExactDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Set;

public record UpdateAddOnRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @ExactDecimal @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal price,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}", message = "must be a three-letter ISO currency code") String currencyCode,
        @NotNull BillingCycle billingCycle,
        @Size(max = 100) Set<@NotBlank @Size(max = 100) String> allowedPlanCodes,
        @Size(max = 100) Set<@NotBlank @Size(max = 100) String> blockedPlanCodes,
        @Size(max = 100) Set<@NotBlank @Size(max = 100) String> dependencyCodes,
        @Size(max = 100) Set<@NotBlank @Size(max = 100) String> exclusionCodes,
        @NotNull @PositiveOrZero Long expectedVersion
) {}
