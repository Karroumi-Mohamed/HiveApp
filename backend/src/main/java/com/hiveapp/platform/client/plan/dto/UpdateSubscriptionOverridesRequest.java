package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Set;

public record UpdateSubscriptionOverridesRequest(
        Set<@NotBlank String> addOnCodes,
        @Valid List<QuotaPackageSelection> quotaPackages
) {}
