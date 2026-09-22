package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reuses catalogue and usage enforcement, but never reprices or evaluates a new marketing award.
 */
@Component
@RequiredArgsConstructor
public class PlanVersionApplicationAssessor {
  private final PlanVersionContentRules rules;
  private final PlanFeatureRepository features;
  private final CommercialCatalogResolver catalogue;
  private final SubscriptionImpactAnalyzer impacts;
  private final CommercialPolicyEvaluator policies;
  private final SpecialCommercialAgreementRepository agreements;
  private final SubscriptionChangeOperationRepository operations;
  private final SubscriptionRepricingItemRepository repricing;
  private final SubscriptionChangeJobItemRepository contentJobs;

  public record Assessment(
      Reviewed reviewed,
      List<SubscriptionChangeConflict> conflicts,
      List<EffectiveQuotaLimit> beforeLimits,
      List<EffectiveQuotaLimit> afterLimits,
      List<String> removedFeatures) {
    public Assessment {
      conflicts = List.copyOf(conflicts);
    }
  }

  public Assessment assess(Subscription current, Plan target, Request request, Instant now) {
    return assess(current, target, request, now, null);
  }

  public Assessment assess(
      Subscription current,
      Plan target,
      Request request,
      Instant now,
      UUID ignoredContentCommandId) {
    List<SubscriptionChangeConflict> conflicts = new ArrayList<>();
    var before = current.getEntitlementSnapshot();
    UUID accountId = current.getAccount().getId();
    if (contentJobs.countOtherPendingContent(accountId, ignoredContentCommandId) > 0)
      add(
          conflicts,
          "CONTENT_CHANGE_PENDING",
          "Another confirmed content change targets this Account.");
    if (!current.getAccount().isActive())
      add(conflicts, "ACCOUNT_INACTIVE", "The Account is inactive.");
    if (current.getStatus() != SubscriptionStatus.ACTIVE
        && current.getStatus() != SubscriptionStatus.TRIALING)
      add(
          conflicts,
          "SUBSCRIPTION_NOT_ENTITLED",
          "Only active or explicitly selected trial subscriptions can change content.");
    if (current.isCancelAtPeriodEnd())
      add(conflicts, "CANCELLATION_PENDING", "Resolve the scheduled cancellation first.");
    if (request.timing() == Timing.AT_RENEWAL && current.getStatus() == SubscriptionStatus.TRIALING)
      add(
          conflicts,
          "TRIAL_HAS_NO_RENEWAL",
          "A trial must become a paid or free subscription before scheduling renewal application.");
    if (!current.getCurrentPeriodEnd().isAfter(now))
      add(conflicts, "PERIOD_NOT_RENEWED", "Wait for the normal renewal to complete.");
    if (!target.isActive())
      add(conflicts, "TARGET_NOT_ACTIVE", "The target version must be active.");
    if (current.getCurrentPrice() == null)
      add(conflicts, "FINANCIAL_TERMS_MISSING", "Accepted financial terms are missing.");
    if (agreements.existsByAccountIdAndStatusIn(
        accountId,
        List.of(
            SpecialAgreementStatus.ACTIVE,
            SpecialAgreementStatus.SCHEDULED,
            SpecialAgreementStatus.AWAITING_SETTLEMENT,
            SpecialAgreementStatus.NEEDS_ATTENTION)))
      add(
          conflicts,
          "PRIVATE_AGREEMENT_REQUIRES_REVIEW",
          "Amend or complete the Account's private agreement before applying this version.");
    if (operations.existsByAccountIdAndStatusIn(
        accountId,
        List.of(SubscriptionChangeStatus.PENDING, SubscriptionChangeStatus.AWAITING_CONFIRMATION)))
      add(conflicts, "OTHER_CHANGE_PENDING", "Another subscription or payment change is pending.");
    if (repricing.existsByPendingAccountId(accountId))
      add(
          conflicts,
          "REPRICING_PENDING",
          "Resolve the scheduled price change before reviewing a content change.");

    var built =
        rules.build(current.getPlan(), target, features.findAllByPlanId(target.getId()), before);
    conflicts.addAll(built.conflicts());
    if (built.snapshot() == null)
      return new Assessment(
          null, conflicts, impacts.effectiveQuotaLimits(before), List.of(), List.of());
    var after = built.snapshot();
    if (after.features().stream()
        .noneMatch(
            feature ->
                feature
                    .featureCode()
                    .equals(
                        com.hiveapp.platform.registry.definition.ClientSubscriptionFeature.CODE)))
      add(
          conflicts,
          "SUBSCRIPTION_PORTAL_REQUIRED",
          "Retain subscription-management access so the Account can read its commercial notices."
              + " This flow does not bypass Plan access controls.");
    Set<String> addOnCodes =
        before.addOns().stream().map(SubscriptionAddOnSnapshot::code).collect(Collectors.toSet());
    var packQuantities =
        before.quotaPackages().stream()
            .collect(
                Collectors.toMap(
                    SubscriptionQuotaPackageSnapshot::code,
                    SubscriptionQuotaPackageSnapshot::quantity));
    var resolved =
        catalogue.resolveSelection(
            target,
            new CommercialCatalogResolver.PriceTuple(before.currencyCode(), before.billingCycle()),
            addOnCodes,
            before.quotaPackages().stream()
                .map(pack -> new QuotaPackageSelection(pack.code(), pack.quantity()))
                .toList(),
            CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
            new CommercialCatalogResolver.RetainedSelection(
                addOnCodes,
                packQuantities,
                before.planPriceEntryId(),
                CommercialCatalogResolver.RetentionMode.CONTENT_VERSION));
    java.util.stream.Stream.concat(resolved.plan().issues().stream(), resolved.issues().stream())
        .distinct()
        .forEach(
            issue ->
                add(
                    conflicts,
                    issue.reason().name(),
                    "Incompatible catalogue rule: " + issue.reason() + "."));

    // Compare immutable purchased versions, not mutable current prices. A definition changed in
    // place must not be silently adopted simply because its commercial code still exists.
    var addOns =
        resolved.plan().addOns().stream()
            .collect(Collectors.toMap(item -> item.code(), item -> item.addOn()));
    before
        .addOns()
        .forEach(
            held -> {
              var live = addOns.get(held.code());
              if (live == null || live.getDefinitionVersion() != held.definitionVersion())
                add(
                    conflicts,
                    "PURCHASED_ADD_ON_CHANGED",
                    "Purchased AddOn " + held.code() + " requires a separate review.");
            });
    before
        .quotaPackages()
        .forEach(
            held -> {
              var live = resolved.packageResolutions().get(held.code());
              if (live == null
                  || live.quotaPackage().getDefinitionVersion() != held.definitionVersion())
                add(
                    conflicts,
                    "PURCHASED_PACK_CHANGED",
                    "Purchased pack " + held.code() + " requires a separate review.");
            });
    var restrictions = policies.evaluate(accountId, now);
    if (restrictions.blocksProduct(CommercialPolicyProductType.PLAN, target.getId()))
      add(
          conflicts,
          "POLICY_BLOCKS_TARGET",
          "An applicable commercial policy blocks this version.");
    addOns.values().stream()
        .filter(item -> addOnCodes.contains(item.getCode()))
        .forEach(
            item -> {
              if (restrictions.blocksProduct(CommercialPolicyProductType.ADD_ON, item.getId()))
                add(
                    conflicts,
                    "POLICY_BLOCKS_PURCHASE",
                    "An applicable policy blocks a retained AddOn.");
            });
    before
        .quotaPackages()
        .forEach(
            held -> {
              var live = resolved.packageResolutions().get(held.code());
              if (live != null
                  && restrictions.blocksProduct(
                      CommercialPolicyProductType.QUOTA_PACKAGE, live.quotaPackage().getId()))
                add(
                    conflicts,
                    "POLICY_BLOCKS_PURCHASE",
                    "An applicable policy blocks a retained capacity pack.");
            });
    after.features().stream()
        .filter(feature -> restrictions.blockedFeatures().containsKey(feature.featureCode()))
        .forEach(
            feature ->
                conflicts.add(
                    new SubscriptionChangeConflict(
                        "POLICY_BLOCKS_FEATURE",
                        feature.featureCode(),
                        null,
                        null,
                        null,
                        "An applicable policy blocks this feature.")));
    conflicts.addAll(impacts.analyze(accountId, current, after));
    var reviewed =
        new Reviewed(
            accountId,
            current.getId(),
            current.getPlan().getId(),
            target.getId(),
            current.termsIdentity(),
            current.getContentEvidenceId(),
            before,
            after,
            current.getCustomOverrides(),
            current.getCurrentPrice(),
            current.getCurrentPriceCurrencyCode(),
            current.getCurrentPeriodStart(),
            current.getCurrentPeriodEnd(),
            request);
    var afterCodes =
        after.features().stream()
            .map(SubscriptionFeatureSnapshot::featureCode)
            .collect(Collectors.toSet());
    return new Assessment(
        reviewed,
        conflicts,
        impacts.effectiveQuotaLimits(before),
        impacts.effectiveQuotaLimits(after),
        before.features().stream()
            .map(SubscriptionFeatureSnapshot::featureCode)
            .filter(code -> !afterCodes.contains(code))
            .sorted()
            .toList());
  }

  private void add(List<SubscriptionChangeConflict> conflicts, String code, String message) {
    conflicts.add(new SubscriptionChangeConflict(code, null, null, null, null, message));
  }
}
