package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyDecisionOutcome;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable, privacy-safe provenance for one policy effect considered during subscription review.
 * It deliberately omits owner, approval, contract, and internal-reason fields.
 */
public record CommercialPolicyDecisionSnapshot(
        UUID policyId,
        UUID activationId,
        UUID lineageId,
        int policyRevisionNumber,
        String policyCode,
        String policyName,
        CommercialPolicyTargetKind targetKind,
        int priority,
        UUID effectId,
        int effectOrder,
        CommercialPolicyEffectType effectType,
        CommercialPolicyProductType productType,
        UUID productId,
        String productCode,
        String featureCode,
        String quotaResource,
        Long quantityDelta,
        @ExactDecimal BigDecimal configuredAmount,
        String configuredCurrencyCode,
        @ExactDecimal BigDecimal percentage,
        @ExactDecimal BigDecimal maximumAmount,
        String maximumCurrencyCode,
        CommercialPolicyDecisionOutcome outcome,
        @ExactDecimal BigDecimal evaluatedAmount,
        String evaluatedCurrencyCode,
        String explanation
) {}
