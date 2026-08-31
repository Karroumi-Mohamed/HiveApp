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

public record UpdatePlanRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @ExactDecimal @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal price,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}", message = "must be a three-letter ISO currency code") String currencyCode,
        @NotNull BillingCycle billingCycle,
        @NotNull @PositiveOrZero Long expectedVersion
) {}
