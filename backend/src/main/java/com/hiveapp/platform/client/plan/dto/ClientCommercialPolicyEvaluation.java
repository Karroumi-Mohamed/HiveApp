package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyDecisionOutcome;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Effective commercial terms without internal audience, ownership, priority, or provenance. */
public record ClientCommercialPolicyEvaluation(
        Instant evaluatedAt,
        @ExactDecimal BigDecimal catalogueRecurringPrice,
        @ExactDecimal BigDecimal fixedRecurringPrice,
        @ExactDecimal BigDecimal discountAmount,
        @ExactDecimal BigDecimal finalRecurringPrice,
        String currencyCode,
        List<ClientCommercialPolicyDecision> decisions,
        List<ClientCommercialPolicyConflict> conflicts
) {
    public ClientCommercialPolicyEvaluation {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public static ClientCommercialPolicyEvaluation from(
            SubscriptionCommercialPolicyEvaluation source
    ) {
        if (source == null) return null;
        return new ClientCommercialPolicyEvaluation(
                source.evaluatedAt(), source.catalogueRecurringPrice(), source.fixedRecurringPrice(),
                source.discountAmount(), source.finalRecurringPrice(), source.currencyCode(),
                source.decisions().stream()
                        .filter(decision -> decision.outcome()
                                != CommercialPolicyDecisionOutcome.REJECTED_LOWER_PRECEDENCE)
                        .map(ClientCommercialPolicyDecision::from).toList(),
                source.conflicts().stream().map(ClientCommercialPolicyConflict::from).toList());
    }
}
