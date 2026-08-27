package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyDecisionOutcome;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;

/** Policy result safe for an Account member; internal targeting/provenance never crosses this DTO. */
public record ClientCommercialPolicyDecision(
        CommercialPolicyEffectType effectType,
        CommercialPolicyProductType productType,
        String productCode,
        String featureCode,
        String quotaResource,
        Long quantityDelta,
        CommercialPolicyDecisionOutcome outcome,
        @ExactDecimal BigDecimal evaluatedAmount,
        String evaluatedCurrencyCode,
        String explanation
) {
    public static ClientCommercialPolicyDecision from(CommercialPolicyDecisionSnapshot source) {
        return new ClientCommercialPolicyDecision(
                source.effectType(), source.productType(), source.productCode(), source.featureCode(),
                source.quotaResource(), source.quantityDelta(), source.outcome(),
                source.evaluatedAmount(), source.evaluatedCurrencyCode(), source.explanation());
    }
}
