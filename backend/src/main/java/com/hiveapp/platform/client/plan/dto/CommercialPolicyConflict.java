package com.hiveapp.platform.client.plan.dto;

import java.util.UUID;

/** Stable conflict shown during review without exposing policy ownership or contract metadata. */
public record CommercialPolicyConflict(
        String code,
        UUID policyId,
        UUID effectId,
        String productCode,
        String featureCode,
        String quotaResource,
        String message
) {}
