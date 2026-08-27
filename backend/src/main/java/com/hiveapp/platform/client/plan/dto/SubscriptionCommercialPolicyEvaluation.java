package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Exact policy review accepted into a subscription operation and entitlement snapshot. */
public record SubscriptionCommercialPolicyEvaluation(
        Instant evaluatedAt,
        @ExactDecimal BigDecimal catalogueRecurringPrice,
        @ExactDecimal BigDecimal fixedRecurringPrice,
        @ExactDecimal BigDecimal discountAmount,
        @ExactDecimal BigDecimal finalRecurringPrice,
        String currencyCode,
        List<CommercialPolicyDecisionSnapshot> decisions,
        List<CommercialPolicyConflict> conflicts
) {
    public SubscriptionCommercialPolicyEvaluation {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public boolean blocked() {
        return !conflicts.isEmpty();
    }
}
