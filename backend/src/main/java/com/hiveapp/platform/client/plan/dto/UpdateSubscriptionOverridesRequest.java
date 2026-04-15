package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaOverride;

import java.util.List;
import java.util.Set;

public record UpdateSubscriptionOverridesRequest(
        Set<String> featureCodes,
        List<QuotaOverride> quotaOverrides
) {}
