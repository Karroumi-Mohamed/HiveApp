package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyDecisionOutcome;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
import java.util.*;
import org.springframework.stereotype.Component;

/** Pure content-only projection. It never resolves a new price, purchase or marketing award. */
@Component
public class PlanVersionContentRules {
  public record Result(
      SubscriptionEntitlementSnapshot snapshot, List<SubscriptionChangeConflict> conflicts) {
    public Result {
      conflicts = List.copyOf(conflicts);
    }
  }

  public Result build(
      Plan source,
      Plan target,
      List<PlanFeature> composition,
      SubscriptionEntitlementSnapshot held) {
    List<SubscriptionChangeConflict> conflicts = new ArrayList<>();
    if (!Objects.equals(source.getLineageId(), target.getLineageId())
        || source.getId().equals(target.getId())) {
      return conflict(
          "DIFFERENT_OR_SAME_VERSION",
          null,
          null,
          "Choose a different version in the same Plan family.");
    }
    if (!source.getCode().equals(held.planCode())
        || source.getRevisionNumber() != held.planDefinitionVersion()) {
      return conflict(
          "SOURCE_CONTENT_CHANGED",
          null,
          null,
          "The source content no longer matches the subscription.");
    }
    Map<String, SubscriptionFeatureSnapshot> features = new TreeMap<>();
    Map<String, PlanFeatureMode> modes = new HashMap<>();
    for (PlanFeature row : composition) {
      String code = row.getFeature().getCode();
      modes.put(code, row.getMode());
      if (row.getMode() == PlanFeatureMode.INCLUDED) {
        features.put(
            code,
            new SubscriptionFeatureSnapshot(
                code,
                List.copyOf(row.getQuotaConfigs() == null ? List.of() : row.getQuotaConfigs())));
      }
    }
    Set<String> included = Set.copyOf(features.keySet());
    Map<String, SubscriptionFeatureSnapshot> heldFeatures = new HashMap<>();
    held.features().forEach(feature -> heldFeatures.put(feature.featureCode(), feature));
    for (SubscriptionAddOnSnapshot addOn : held.addOns()) {
      for (String code : addOn.featureCodes()) {
        if (modes.get(code) != PlanFeatureMode.OPTIONAL_ADD_ON) {
          conflicts.add(
              issue(
                  included.contains(code)
                      ? "DUPLICATE_PAID_CAPABILITY"
                      : "ADD_ON_FEATURE_UNAVAILABLE",
                  code,
                  null,
                  "A purchased AddOn must remain optional on the target version."));
        } else if (!heldFeatures.containsKey(code)) {
          conflicts.add(
              issue(
                  "HELD_ENTITLEMENT_MISSING",
                  code,
                  null,
                  "The accepted AddOn content is missing; it cannot be reconstructed silently."));
        } else if (features.putIfAbsent(code, heldFeatures.get(code)) != null) {
          conflicts.add(
              issue("DUPLICATE_PAID_CAPABILITY", code, null, "Purchased AddOns overlap."));
        }
      }
    }

    // AddOn quotas already include their accepted bonuses. Only rebuild bonuses on Plan-owned
    // features, from immutable accepted evidence, without evaluating a new promotion.
    List<CommercialOfferEffectSnapshot.QuotaBonus> bonuses = new ArrayList<>();
    if (held.commercialPolicyEvaluation() != null) {
      held.commercialPolicyEvaluation().decisions().stream()
          .filter(
              item ->
                  item.outcome() == CommercialPolicyDecisionOutcome.APPLIED
                      && item.effectType() == CommercialPolicyEffectType.ADDITIVE_QUOTA_BONUS)
          .forEach(
              item -> {
                if (item.quantityDelta() == null || item.quantityDelta() <= 0)
                  conflicts.add(
                      issue(
                          "ACCEPTED_BONUS_INVALID",
                          item.featureCode(),
                          item.quotaResource(),
                          "The accepted capacity bonus needs a separate review."));
                else
                  bonuses.add(
                      new CommercialOfferEffectSnapshot.QuotaBonus(
                          item.featureCode(), item.quotaResource(), item.quantityDelta()));
              });
    }
    if (held.offerEvaluation() != null) bonuses.addAll(held.offerEvaluation().quotaBonuses());
    for (var bonus : bonuses) {
      var feature = features.get(bonus.featureCode());
      var quota =
          feature == null
              ? Optional.<QuotaLimitEntry>empty()
              : feature.quotaConfigs().stream()
                  .filter(item -> item.resource().equals(bonus.resource()))
                  .findFirst();
      if (quota.isEmpty() || quota.get().mode() != QuotaLimitMode.FINITE) {
        conflicts.add(
            issue(
                "ACCEPTED_BONUS_INCOMPATIBLE",
                bonus.featureCode(),
                bonus.resource(),
                "The target must retain a finite owner for the accepted capacity bonus."));
      } else if (included.contains(bonus.featureCode())) {
        try {
          List<QuotaLimitEntry> adjusted =
              feature.quotaConfigs().stream()
                  .map(
                      item ->
                          item.resource().equals(bonus.resource())
                              ? new QuotaLimitEntry(
                                  item.resource(),
                                  item.mode(),
                                  Math.addExact(item.limit(), bonus.quantity()))
                              : item)
                  .toList();
          features.put(
              feature.featureCode(),
              new SubscriptionFeatureSnapshot(feature.featureCode(), adjusted));
        } catch (ArithmeticException exception) {
          conflicts.add(
              issue(
                  "QUOTA_OVERFLOW",
                  bonus.featureCode(),
                  bonus.resource(),
                  "Capacity exceeds the supported range."));
        }
      }
    }
    for (var pack : held.quotaPackages()) {
      var owner = features.get(pack.featureCode());
      if (owner == null
          || owner.quotaConfigs().stream()
              .noneMatch(
                  quota ->
                      quota.resource().equals(pack.resource())
                          && quota.mode() == QuotaLimitMode.FINITE)) {
        conflicts.add(
            issue(
                "PURCHASED_CAPACITY_OWNER_LOST",
                pack.featureCode(),
                pack.resource(),
                "A purchased capacity pack would lose its finite quota owner."));
      }
    }
    if (!conflicts.isEmpty()) return new Result(null, conflicts);
    var sourceTerms =
        held.financialPlanSource() != null
            ? held.financialPlanSource()
            : new SubscriptionFinancialPlanSource(
                source.getId(), source.getCode(), source.getRevisionNumber());
    var snapshot =
        new SubscriptionEntitlementSnapshot(
            SubscriptionEntitlementSnapshot.CURRENT_SCHEMA_VERSION,
            target.getCode(),
            target.getName(),
            target.getRevisionNumber(),
            held.basePrice(),
            held.currencyCode(),
            held.billingCycle(),
            held.effectiveFrom(),
            held.effectiveUntil(),
            List.copyOf(features.values()),
            held.addOns(),
            held.quotaPackages(),
            held.planPriceEntryId(),
            held.commercialPolicyEvaluation(),
            held.offerEvaluation(),
            sourceTerms);
    return new Result(snapshot, List.of());
  }

  /** Defensive execution check; any financial or paid-period change belongs to another workflow. */
  public void requirePreservedTerms(
      SubscriptionEntitlementSnapshot before, SubscriptionEntitlementSnapshot after) {
    if (before.basePrice().compareTo(after.basePrice()) != 0
        || !Objects.equals(before.currencyCode(), after.currencyCode())
        || before.billingCycle() != after.billingCycle()
        || !Objects.equals(before.planPriceEntryId(), after.planPriceEntryId())
        || !before.addOns().equals(after.addOns())
        || !before.quotaPackages().equals(after.quotaPackages())
        || !Objects.equals(before.commercialPolicyEvaluation(), after.commercialPolicyEvaluation())
        || !Objects.equals(before.offerEvaluation(), after.offerEvaluation())
        || !Objects.equals(before.effectiveFrom(), after.effectiveFrom())
        || !Objects.equals(before.effectiveUntil(), after.effectiveUntil())) {
      throw new IllegalStateException(
          "A content-version operation cannot change accepted financial terms or period.");
    }
  }

  private Result conflict(String code, String feature, String resource, String message) {
    return new Result(null, List.of(issue(code, feature, resource, message)));
  }

  private SubscriptionChangeConflict issue(
      String code, String feature, String resource, String message) {
    return new SubscriptionChangeConflict(code, feature, resource, null, null, message);
  }
}
