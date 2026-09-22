package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyDecisionOutcome;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyConflict;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyDecisionSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionCommercialPolicyEvaluation;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies one deterministic policy evaluation to a candidate immutable subscription snapshot. */
@Service
@RequiredArgsConstructor
public class CommercialPolicySubscriptionTermsService {

    private final BillingCalculator billingCalculator;

    public AppliedTerms apply(
            Plan targetPlan,
            SubscriptionEntitlementSnapshot catalogueSnapshot,
            CommercialPolicyEvaluator.Evaluation evaluation,
            CommercialPolicySelectionPlanner.PlannedSelection selection
    ) {
        List<CommercialPolicyDecisionSnapshot> decisions = new ArrayList<>();
        List<CommercialPolicyConflict> conflicts = new ArrayList<>();
        Set<java.util.UUID> recordedEffects = new HashSet<>();

        Set<String> grantedAddOns = selection.acceptedGrants().stream()
                .filter(candidate -> candidate.effect().getType() == CommercialPolicyEffectType.GRANT_ADD_ON)
                .map(candidate -> candidate.effect().productCode()).collect(java.util.stream.Collectors.toSet());
        Set<String> grantedPackages = selection.acceptedGrants().stream()
                .filter(candidate -> candidate.effect().getType()
                        == CommercialPolicyEffectType.GRANT_QUOTA_PACKAGE)
                .map(candidate -> candidate.effect().productCode()).collect(java.util.stream.Collectors.toSet());

        List<SubscriptionAddOnSnapshot> addOns = catalogueSnapshot.addOns().stream()
                .map(item -> grantedAddOns.contains(item.code())
                        ? new SubscriptionAddOnSnapshot(
                                item.code(), item.name(), item.definitionVersion(),
                                Money.zero(item.currencyCode()).amount(), item.currencyCode(),
                                item.billingCycle(), item.featureCodes(), item.priceEntryId())
                        : item)
                .toList();
        List<SubscriptionQuotaPackageSnapshot> packages = catalogueSnapshot.quotaPackages().stream()
                .map(item -> grantedPackages.contains(item.code())
                        ? new SubscriptionQuotaPackageSnapshot(
                                item.code(), item.name(), item.definitionVersion(), item.featureCode(),
                                item.resource(), item.capacityPerUnit(), item.quantity(),
                                Money.zero(item.currencyCode()).amount(), item.currencyCode(),
                                item.billingCycle(), item.priceEntryId())
                        : item)
                .toList();

        selection.acceptedGrants().forEach(candidate -> {
            recordedEffects.add(candidate.effect().getId());
            decisions.add(candidate.decision(
                    CommercialPolicyDecisionOutcome.APPLIED,
                    Money.zero(catalogueSnapshot.currencyCode()).amount(),
                    catalogueSnapshot.currencyCode(),
                    candidate.effect().getType() == CommercialPolicyEffectType.GRANT_ADD_ON
                            ? "The AddOn is accepted at zero recurring product price."
                            : "Exactly one quota-package unit is accepted at zero recurring product price."));
        });
        selection.rejectedGrants().forEach(candidate -> {
            recordedEffects.add(candidate.effect().getId());
            decisions.add(candidate.decision(
                    CommercialPolicyDecisionOutcome.REJECTED_INCOMPATIBLE, null, null,
                    "The grant does not satisfy the target Plan's compatibility or V1 quantity rules."));
        });

        recordProductDecisions(targetPlan, catalogueSnapshot, evaluation, decisions, conflicts,
                recordedEffects);

        List<SubscriptionFeatureSnapshot> features = applyQuotaBonuses(
                catalogueSnapshot.features(), evaluation, decisions, conflicts, recordedEffects);
        recordFeatureBlocks(features, evaluation, decisions, conflicts, recordedEffects);

        SubscriptionEntitlementSnapshot pricedCatalogue = new SubscriptionEntitlementSnapshot(
                SubscriptionEntitlementSnapshot.CURRENT_SCHEMA_VERSION,
                catalogueSnapshot.planCode(), catalogueSnapshot.planName(),
                catalogueSnapshot.planDefinitionVersion(), catalogueSnapshot.basePrice(),
                catalogueSnapshot.currencyCode(), catalogueSnapshot.billingCycle(),
                catalogueSnapshot.effectiveFrom(), catalogueSnapshot.effectiveUntil(),
                features, addOns, packages, catalogueSnapshot.planPriceEntryId(), null,
                null, catalogueSnapshot.financialPlanSource());
        Money cataloguePrice = billingCalculator.catalogueMoney(pricedCatalogue);
        Money fixedPrice = applyFixedPrice(
                cataloguePrice, pricedCatalogue, evaluation.fixedPrice(), decisions, recordedEffects);
        Money discount = applyDiscount(
                fixedPrice, evaluation.discount(), decisions, recordedEffects);
        Money finalPrice = fixedPrice.subtract(discount);

        decisions.sort(Comparator
                .comparing((CommercialPolicyDecisionSnapshot decision) -> decision.policyId().toString())
                .thenComparingInt(CommercialPolicyDecisionSnapshot::effectOrder));
        SubscriptionCommercialPolicyEvaluation accepted = new SubscriptionCommercialPolicyEvaluation(
                evaluation.evaluatedAt(), cataloguePrice.amount(), fixedPrice.amount(), discount.amount(),
                finalPrice.amount(), finalPrice.currencyCode(), decisions, conflicts);
        return new AppliedTerms(
                pricedCatalogue.withCommercialPolicyEvaluation(accepted), accepted);
    }

    private void recordProductDecisions(
            Plan targetPlan,
            SubscriptionEntitlementSnapshot snapshot,
            CommercialPolicyEvaluator.Evaluation evaluation,
            List<CommercialPolicyDecisionSnapshot> decisions,
            List<CommercialPolicyConflict> conflicts,
            Set<java.util.UUID> recordedEffects
    ) {
        recordSelectedProduct(
                CommercialPolicyProductType.PLAN, targetPlan.getId(), targetPlan.getCode(),
                evaluation, decisions, conflicts, recordedEffects);
        snapshot.addOns().forEach(item -> evaluation.products().entrySet().stream()
                .filter(entry -> entry.getKey().type() == CommercialPolicyProductType.ADD_ON)
                .map(java.util.Map.Entry::getValue)
                .map(CommercialPolicyEvaluator.WinnerSet::winner)
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> item.code().equals(candidate.effect().productCode()))
                .findFirst().ifPresent(candidate -> recordSelectedProduct(
                        CommercialPolicyProductType.ADD_ON, candidate.effect().productId(), item.code(),
                        evaluation, decisions, conflicts, recordedEffects)));
        snapshot.quotaPackages().forEach(item -> evaluation.products().entrySet().stream()
                .filter(entry -> entry.getKey().type() == CommercialPolicyProductType.QUOTA_PACKAGE)
                .map(java.util.Map.Entry::getValue)
                .map(CommercialPolicyEvaluator.WinnerSet::winner)
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> item.code().equals(candidate.effect().productCode()))
                .findFirst().ifPresent(candidate -> recordSelectedProduct(
                        CommercialPolicyProductType.QUOTA_PACKAGE,
                        candidate.effect().productId(), item.code(),
                        evaluation, decisions, conflicts, recordedEffects)));
    }

    private void recordSelectedProduct(
            CommercialPolicyProductType type,
            java.util.UUID id,
            String code,
            CommercialPolicyEvaluator.Evaluation evaluation,
            List<CommercialPolicyDecisionSnapshot> decisions,
            List<CommercialPolicyConflict> conflicts,
            Set<java.util.UUID> recordedEffects
    ) {
        CommercialPolicyEvaluator.WinnerSet winnerSet = evaluation.product(type, id);
        CommercialPolicyEvaluator.Candidate winner = winnerSet.winner();
        if (winner == null) return;
        if (recordedEffects.add(winner.effect().getId())) {
            if (winner.effect().getType() == CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION) {
                decisions.add(winner.decision(
                        CommercialPolicyDecisionOutcome.BLOCKED_SELECTION, null, null,
                        "The selected product is blocked by an applicable commercial policy."));
                conflicts.add(new CommercialPolicyConflict(
                        "POLICY_PRODUCT_BLOCKED", winner.policy().getId(), winner.effect().getId(),
                        code, null, null,
                        "Product " + code + " is blocked for this Account."));
            } else if (winner.effect().getType() == CommercialPolicyEffectType.ALLOW_PRODUCT_SELECTION) {
                decisions.add(winner.decision(
                        CommercialPolicyDecisionOutcome.APPLIED, null, null,
                        "The policy makes the exact product revision selectable for this Account."));
            }
        }
        recordRejected(winnerSet, decisions, recordedEffects,
                "A more specific or higher-priority product policy wins.");
    }

    private List<SubscriptionFeatureSnapshot> applyQuotaBonuses(
            List<SubscriptionFeatureSnapshot> source,
            CommercialPolicyEvaluator.Evaluation evaluation,
            List<CommercialPolicyDecisionSnapshot> decisions,
            List<CommercialPolicyConflict> conflicts,
            Set<java.util.UUID> recordedEffects
    ) {
        List<SubscriptionFeatureSnapshot> features = new ArrayList<>();
        for (SubscriptionFeatureSnapshot feature : source) {
            List<QuotaLimitEntry> quotas = new ArrayList<>();
            for (QuotaLimitEntry quota : feature.quotaConfigs()) {
                CommercialPolicyEvaluator.WinnerSet winnerSet = evaluation.quotaBonuses()
                        .getOrDefault(new CommercialPolicyEvaluator.QuotaKey(
                                feature.featureCode(), quota.resource()),
                                CommercialPolicyEvaluator.WinnerSet.empty());
                CommercialPolicyEvaluator.Candidate winner = winnerSet.winner();
                if (winner == null) {
                    quotas.add(quota);
                    continue;
                }
                if (quota.mode() != QuotaLimitMode.FINITE) {
                    decisions.add(winner.decision(
                            CommercialPolicyDecisionOutcome.REJECTED_INCOMPATIBLE, null, null,
                            "An additive quota bonus does not alter an unlimited quota."));
                    recordedEffects.add(winner.effect().getId());
                    quotas.add(quota);
                    recordRejected(winnerSet, decisions, recordedEffects,
                            "A more specific or higher-priority quota bonus wins.");
                    continue;
                }
                try {
                    long adjusted = Math.addExact(quota.limit(), winner.effect().getQuantityDelta());
                    quotas.add(new QuotaLimitEntry(quota.resource(), QuotaLimitMode.FINITE, adjusted));
                    decisions.add(winner.decision(
                            CommercialPolicyDecisionOutcome.APPLIED,
                            BigDecimal.valueOf(winner.effect().getQuantityDelta()), null,
                            "The quota limit is increased in the accepted subscription snapshot."));
                    recordedEffects.add(winner.effect().getId());
                } catch (ArithmeticException exception) {
                    quotas.add(quota);
                    decisions.add(winner.decision(
                            CommercialPolicyDecisionOutcome.BLOCKED_SELECTION, null, null,
                            "The quota bonus cannot be represented in the supported integer range."));
                    recordedEffects.add(winner.effect().getId());
                    conflicts.add(new CommercialPolicyConflict(
                            "POLICY_QUOTA_OVERFLOW", winner.policy().getId(), winner.effect().getId(),
                            null, feature.featureCode(), quota.resource(),
                            "The policy quota bonus exceeds the supported integer range."));
                }
                recordRejected(winnerSet, decisions, recordedEffects,
                        "A more specific or higher-priority quota bonus wins.");
            }
            features.add(new SubscriptionFeatureSnapshot(feature.featureCode(), List.copyOf(quotas)));
        }
        evaluation.quotaBonuses().forEach((key, winnerSet) -> {
            boolean represented = source.stream().anyMatch(feature -> feature.featureCode().equals(key.featureCode())
                    && feature.quotaConfigs().stream().anyMatch(quota -> quota.resource().equals(key.resource())));
            if (!represented && winnerSet.winner() != null
                    && recordedEffects.add(winnerSet.winner().effect().getId())) {
                decisions.add(winnerSet.winner().decision(
                        CommercialPolicyDecisionOutcome.REJECTED_INCOMPATIBLE, null, null,
                        "The selected entitlement does not contain the quota resource."));
                recordRejected(winnerSet, decisions, recordedEffects,
                        "A more specific or higher-priority quota bonus wins.");
            }
        });
        return List.copyOf(features);
    }

    private void recordFeatureBlocks(
            List<SubscriptionFeatureSnapshot> features,
            CommercialPolicyEvaluator.Evaluation evaluation,
            List<CommercialPolicyDecisionSnapshot> decisions,
            List<CommercialPolicyConflict> conflicts,
            Set<java.util.UUID> recordedEffects
    ) {
        for (SubscriptionFeatureSnapshot feature : features) {
            CommercialPolicyEvaluator.WinnerSet winnerSet = evaluation.blockedFeatures()
                    .getOrDefault(feature.featureCode(), CommercialPolicyEvaluator.WinnerSet.empty());
            CommercialPolicyEvaluator.Candidate winner = winnerSet.winner();
            if (winner == null || !recordedEffects.add(winner.effect().getId())) continue;
            decisions.add(winner.decision(
                    CommercialPolicyDecisionOutcome.BLOCKED_SELECTION, null, null,
                    "The selected entitlement contains a Feature blocked for this Account."));
            conflicts.add(new CommercialPolicyConflict(
                    "POLICY_FEATURE_BLOCKED", winner.policy().getId(), winner.effect().getId(),
                    null, feature.featureCode(), null,
                    "Feature " + feature.featureCode() + " is blocked for this Account."));
            recordRejected(winnerSet, decisions, recordedEffects,
                    "A more specific or higher-priority Feature restriction wins.");
        }
    }

    private void recordRejected(
            CommercialPolicyEvaluator.WinnerSet winnerSet,
            List<CommercialPolicyDecisionSnapshot> decisions,
            Set<java.util.UUID> recordedEffects,
            String explanation
    ) {
        winnerSet.rejected().forEach(candidate -> {
            if (recordedEffects.add(candidate.effect().getId())) {
                decisions.add(candidate.decision(
                        CommercialPolicyDecisionOutcome.REJECTED_LOWER_PRECEDENCE,
                        null, null, explanation));
            }
        });
    }

    private Money applyFixedPrice(
            Money cataloguePrice,
            SubscriptionEntitlementSnapshot snapshot,
            CommercialPolicyEvaluator.WinnerSet candidates,
            List<CommercialPolicyDecisionSnapshot> decisions,
            Set<java.util.UUID> recordedEffects
    ) {
        CommercialPolicyEvaluator.Candidate accepted = null;
        for (CommercialPolicyEvaluator.Candidate candidate : candidates.ordered()) {
            boolean compatible = candidate.effect().money().currencyCode().equals(cataloguePrice.currencyCode())
                    && candidate.effect().getBillingCycle() == snapshot.billingCycle();
            if (accepted == null && compatible) {
                accepted = candidate;
                recordedEffects.add(candidate.effect().getId());
                decisions.add(candidate.decision(
                        CommercialPolicyDecisionOutcome.APPLIED,
                        candidate.effect().money().amount(), candidate.effect().money().currencyCode(),
                        "The fixed recurring subscription price replaces the catalogue total."));
            } else {
                recordedEffects.add(candidate.effect().getId());
                decisions.add(candidate.decision(
                        compatible
                                ? CommercialPolicyDecisionOutcome.REJECTED_LOWER_PRECEDENCE
                                : CommercialPolicyDecisionOutcome.REJECTED_INCOMPATIBLE,
                        null, null,
                        compatible
                                ? "A more specific or higher-priority fixed price wins."
                                : "The fixed price currency or billing cycle does not match this selection."));
            }
        }
        return accepted == null ? cataloguePrice : accepted.effect().money();
    }

    private Money applyDiscount(
            Money recurringPrice,
            CommercialPolicyEvaluator.WinnerSet candidates,
            List<CommercialPolicyDecisionSnapshot> decisions,
            Set<java.util.UUID> recordedEffects
    ) {
        CommercialPolicyEvaluator.Candidate accepted = null;
        Money acceptedDiscount = Money.zero(recurringPrice.currencyCode());
        for (CommercialPolicyEvaluator.Candidate candidate : candidates.ordered()) {
            Money calculated = compatibleDiscount(candidate, recurringPrice);
            if (accepted == null && calculated != null) {
                accepted = candidate;
                acceptedDiscount = calculated;
                recordedEffects.add(candidate.effect().getId());
                decisions.add(candidate.decision(
                        CommercialPolicyDecisionOutcome.APPLIED,
                        calculated.amount(), calculated.currencyCode(),
                        "This is the one winning discount; discount policies never stack."));
            } else {
                recordedEffects.add(candidate.effect().getId());
                decisions.add(candidate.decision(
                        calculated == null
                                ? CommercialPolicyDecisionOutcome.REJECTED_INCOMPATIBLE
                                : CommercialPolicyDecisionOutcome.REJECTED_LOWER_PRECEDENCE,
                        null, null,
                        calculated == null
                                ? "The discount currency does not match this selection."
                                : "Another more specific or higher-priority discount wins; discounts never stack."));
            }
        }
        return acceptedDiscount;
    }

    private Money compatibleDiscount(
            CommercialPolicyEvaluator.Candidate candidate,
            Money recurringPrice
    ) {
        if (candidate.effect().getType() == CommercialPolicyEffectType.FIXED_DISCOUNT) {
            Money configured = candidate.effect().money();
            if (!configured.currencyCode().equals(recurringPrice.currencyCode())) return null;
            return configured.amount().compareTo(recurringPrice.amount()) > 0 ? recurringPrice : configured;
        }
        Money maximum = candidate.effect().maximumMoney();
        if (!maximum.currencyCode().equals(recurringPrice.currencyCode())) return null;
        int scale = Currency.getInstance(recurringPrice.currencyCode()).getDefaultFractionDigits();
        BigDecimal amount = recurringPrice.amount()
                .multiply(candidate.effect().getPercentage())
                .divide(new BigDecimal("100"), scale, RoundingMode.HALF_UP);
        if (amount.compareTo(maximum.amount()) > 0) amount = maximum.amount();
        if (amount.compareTo(recurringPrice.amount()) > 0) amount = recurringPrice.amount();
        return Money.of(amount, recurringPrice.currencyCode());
    }

    public record AppliedTerms(
            SubscriptionEntitlementSnapshot snapshot,
            SubscriptionCommercialPolicyEvaluation evaluation
    ) {}
}
