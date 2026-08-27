package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyDecisionOutcome;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicyEffect;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommercialPolicySubscriptionTermsServiceTest {

    private final CommercialPolicySubscriptionTermsService service =
            new CommercialPolicySubscriptionTermsService(new BillingCalculator(null));

    @Test
    void fixedPriceIsResolvedBeforeOneRoundedPercentageDiscount() {
        var fixedPrice = monetaryCandidate(
                CommercialPolicyEffectType.FIXED_SUBSCRIPTION_PRICE,
                "10.00", "USD", BillingCycle.MONTHLY, null, null, 0);
        var percentage = monetaryCandidate(
                CommercialPolicyEffectType.PERCENTAGE_DISCOUNT,
                null, null, null, "33.3333", "100.00", 1);

        var result = apply(new CommercialPolicyEvaluator.WinnerSet(fixedPrice, List.of()),
                new CommercialPolicyEvaluator.WinnerSet(percentage, List.of()));

        assertThat(result.evaluation().fixedRecurringPrice()).isEqualByComparingTo("10.00");
        assertThat(result.evaluation().discountAmount()).isEqualByComparingTo("3.33");
        assertThat(result.evaluation().finalRecurringPrice()).isEqualByComparingTo("6.67");
    }

    @Test
    void percentageCapIsExactAndDiscountCandidatesNeverStack() {
        var fixedPrice = monetaryCandidate(
                CommercialPolicyEffectType.FIXED_SUBSCRIPTION_PRICE,
                "10.00", "USD", BillingCycle.MONTHLY, null, null, 0);
        var cappedPercentage = monetaryCandidate(
                CommercialPolicyEffectType.PERCENTAGE_DISCOUNT,
                null, null, null, "50.0000", "1.25", 1);
        var lowerFixed = monetaryCandidate(
                CommercialPolicyEffectType.FIXED_DISCOUNT,
                "4.00", "USD", null, null, null, 2);

        var result = apply(new CommercialPolicyEvaluator.WinnerSet(fixedPrice, List.of()),
                new CommercialPolicyEvaluator.WinnerSet(cappedPercentage, List.of(lowerFixed)));

        assertThat(result.evaluation().discountAmount()).isEqualByComparingTo("1.25");
        assertThat(result.evaluation().finalRecurringPrice()).isEqualByComparingTo("8.75");
        assertThat(result.evaluation().decisions())
                .filteredOn(decision -> decision.effectType() == CommercialPolicyEffectType.PERCENTAGE_DISCOUNT)
                .singleElement().extracting(decision -> decision.outcome())
                .isEqualTo(CommercialPolicyDecisionOutcome.APPLIED);
        assertThat(result.evaluation().decisions())
                .filteredOn(decision -> decision.effectType() == CommercialPolicyEffectType.FIXED_DISCOUNT)
                .singleElement().extracting(decision -> decision.outcome())
                .isEqualTo(CommercialPolicyDecisionOutcome.REJECTED_LOWER_PRECEDENCE);
    }

    @Test
    void incompatibleDiscountCurrencyIsRejectedWithoutChangingGrossPrice() {
        var fixedPrice = monetaryCandidate(
                CommercialPolicyEffectType.FIXED_SUBSCRIPTION_PRICE,
                "10.00", "USD", BillingCycle.MONTHLY, null, null, 0);
        var incompatible = monetaryCandidate(
                CommercialPolicyEffectType.FIXED_DISCOUNT,
                "2.00", "EUR", null, null, null, 1);

        var result = apply(new CommercialPolicyEvaluator.WinnerSet(fixedPrice, List.of()),
                new CommercialPolicyEvaluator.WinnerSet(incompatible, List.of()));

        assertThat(result.evaluation().discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.evaluation().finalRecurringPrice()).isEqualByComparingTo("10.00");
        assertThat(result.evaluation().decisions())
                .filteredOn(decision -> decision.effectType() == CommercialPolicyEffectType.FIXED_DISCOUNT)
                .singleElement().extracting(decision -> decision.outcome())
                .isEqualTo(CommercialPolicyDecisionOutcome.REJECTED_INCOMPATIBLE);
    }

    @Test
    void blockedFeatureIsAConflictAndCannotEnterAcceptedTerms() {
        var blocked = featureCandidate("platform.test-feature");
        SubscriptionEntitlementSnapshot snapshot = new SubscriptionEntitlementSnapshot(
                "TEST", new BigDecimal("25.00"), "USD", BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot("platform.test-feature", List.of())),
                List.of(), List.of());
        CommercialPolicyEvaluator.Evaluation evaluation = new CommercialPolicyEvaluator.Evaluation(
                Instant.parse("2026-08-27T10:00:00Z"),
                CommercialPolicyEvaluator.WinnerSet.empty(),
                CommercialPolicyEvaluator.WinnerSet.empty(), Map.of(), Map.of(),
                Map.of("platform.test-feature",
                        new CommercialPolicyEvaluator.WinnerSet(blocked, List.of())));

        var result = apply(snapshot, evaluation);

        assertThat(result.evaluation().blocked()).isTrue();
        assertThat(result.evaluation().conflicts())
                .singleElement().extracting(conflict -> conflict.code())
                .isEqualTo("POLICY_FEATURE_BLOCKED");
        assertThat(result.evaluation().decisions())
                .singleElement().extracting(decision -> decision.outcome())
                .isEqualTo(CommercialPolicyDecisionOutcome.BLOCKED_SELECTION);
    }

    private CommercialPolicySubscriptionTermsService.AppliedTerms apply(
            CommercialPolicyEvaluator.WinnerSet fixedPrice,
            CommercialPolicyEvaluator.WinnerSet discount
    ) {
        SubscriptionEntitlementSnapshot snapshot = SubscriptionEntitlementSnapshot.empty(
                "TEST", new BigDecimal("25.00"), "USD", BillingCycle.MONTHLY);
        CommercialPolicyEvaluator.Evaluation evaluation = new CommercialPolicyEvaluator.Evaluation(
                Instant.parse("2026-08-27T10:00:00Z"), fixedPrice, discount,
                Map.of(), Map.of(), Map.of());
        return apply(snapshot, evaluation);
    }

    private CommercialPolicySubscriptionTermsService.AppliedTerms apply(
            SubscriptionEntitlementSnapshot snapshot,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        Plan plan = mock(Plan.class);
        when(plan.getId()).thenReturn(UUID.randomUUID());
        when(plan.getCode()).thenReturn("TEST");
        var selection = new CommercialPolicySelectionPlanner.PlannedSelection(
                null, Set.of(), List.of(), List.of(), List.of());
        return service.apply(plan, snapshot, evaluation, selection);
    }

    private CommercialPolicyEvaluator.Candidate featureCandidate(String featureCode) {
        CommercialPolicy policy = mock(CommercialPolicy.class);
        when(policy.getId()).thenReturn(UUID.randomUUID());
        when(policy.getLineageId()).thenReturn(UUID.randomUUID());
        when(policy.getRevisionNumber()).thenReturn(1);
        when(policy.getCode()).thenReturn("BLOCK_FEATURE_POLICY");
        when(policy.getName()).thenReturn("Block feature policy");
        when(policy.getTargetKind()).thenReturn(CommercialPolicyTargetKind.ACCOUNT_SET);
        when(policy.getPriority()).thenReturn(10);

        Feature feature = mock(Feature.class);
        when(feature.getCode()).thenReturn(featureCode);
        CommercialPolicyEffect effect = mock(CommercialPolicyEffect.class);
        when(effect.getId()).thenReturn(UUID.randomUUID());
        when(effect.getEffectOrder()).thenReturn(0);
        when(effect.getType()).thenReturn(CommercialPolicyEffectType.BLOCK_FEATURE);
        when(effect.getFeature()).thenReturn(feature);
        return new CommercialPolicyEvaluator.Candidate(policy, effect, UUID.randomUUID());
    }

    private CommercialPolicyEvaluator.Candidate monetaryCandidate(
            CommercialPolicyEffectType type,
            String amount,
            String currency,
            BillingCycle cycle,
            String percentage,
            String maximum,
            int order
    ) {
        CommercialPolicy policy = mock(CommercialPolicy.class);
        UUID policyId = UUID.randomUUID();
        when(policy.getId()).thenReturn(policyId);
        when(policy.getLineageId()).thenReturn(UUID.randomUUID());
        when(policy.getRevisionNumber()).thenReturn(1);
        when(policy.getCode()).thenReturn("POLICY_" + order);
        when(policy.getName()).thenReturn("Policy " + order);
        when(policy.getTargetKind()).thenReturn(CommercialPolicyTargetKind.ACCOUNT_SET);
        when(policy.getPriority()).thenReturn(100 - order);

        CommercialPolicyEffect effect = mock(CommercialPolicyEffect.class);
        when(effect.getId()).thenReturn(UUID.randomUUID());
        when(effect.getEffectOrder()).thenReturn(order);
        when(effect.getType()).thenReturn(type);
        when(effect.getBillingCycle()).thenReturn(cycle);
        when(effect.getAmount()).thenReturn(amount == null ? null : new BigDecimal(amount));
        when(effect.getCurrencyCode()).thenReturn(currency);
        when(effect.money()).thenReturn(amount == null ? null : Money.of(new BigDecimal(amount), currency));
        when(effect.getPercentage()).thenReturn(
                percentage == null ? null : new BigDecimal(percentage));
        when(effect.getMaximumAmount()).thenReturn(
                maximum == null ? null : new BigDecimal(maximum));
        when(effect.getMaximumCurrencyCode()).thenReturn(maximum == null ? null : "USD");
        when(effect.maximumMoney()).thenReturn(
                maximum == null ? null : Money.of(new BigDecimal(maximum), "USD"));
        return new CommercialPolicyEvaluator.Candidate(policy, effect, UUID.randomUUID());
    }
}
