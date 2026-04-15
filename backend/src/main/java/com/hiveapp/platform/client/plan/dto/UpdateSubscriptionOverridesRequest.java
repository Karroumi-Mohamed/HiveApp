package com.hiveapp.platform.client.plan.dto;

import java.util.Set;

public record UpdateSubscriptionOverridesRequest(
    Set<String> featureCodes
) {}
