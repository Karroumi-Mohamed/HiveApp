package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Set;

public record SubscriptionChangeRequest(
        @NotBlank String targetPlanCode,
        Set<String> addOnCodes,
        @Valid List<QuotaPackageSelection> quotaPackages
) {}
