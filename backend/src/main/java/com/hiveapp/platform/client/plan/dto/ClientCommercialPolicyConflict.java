package com.hiveapp.platform.client.plan.dto;

/** Policy conflict safe for an Account member; internal policy/effect identities are omitted. */
public record ClientCommercialPolicyConflict(
        String code,
        String productCode,
        String featureCode,
        String quotaResource,
        String message
) {
    public static ClientCommercialPolicyConflict from(CommercialPolicyConflict source) {
        return new ClientCommercialPolicyConflict(
                source.code(), source.productCode(), source.featureCode(),
                source.quotaResource(), source.message());
    }
}
