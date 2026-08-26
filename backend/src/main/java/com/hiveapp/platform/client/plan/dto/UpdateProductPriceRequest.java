package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateProductPriceRequest(
        @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank String currencyCode,
        @NotNull BillingCycle billingCycle,
        @NotNull Instant effectiveFrom,
        Instant effectiveUntil,
        @PositiveOrZero long version
) {}
