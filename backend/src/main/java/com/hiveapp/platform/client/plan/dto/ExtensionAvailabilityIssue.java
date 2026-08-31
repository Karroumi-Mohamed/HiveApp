package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionResolutionSource;

/** Safe operator projection. It is never returned by the ordinary client catalogue. */
public record ExtensionAvailabilityIssue(
        ExtensionAvailabilityReason reason,
        ExtensionResolutionSource source,
        String sourceCode
) {}
