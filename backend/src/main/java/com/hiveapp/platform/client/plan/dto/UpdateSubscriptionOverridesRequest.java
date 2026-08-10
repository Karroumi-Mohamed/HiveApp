package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Set;

public record UpdateSubscriptionOverridesRequest(
        Set<String> addOnCodes,
        @Valid List<QuotaPackageSelection> quotaPackages
) {}
