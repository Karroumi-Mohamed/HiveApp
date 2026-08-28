package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.money.ExactDecimal;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SubscriptionEntitlementSnapshot(
    int schemaVersion,
    String planCode,
    String planName,
    long planDefinitionVersion,
    @ExactDecimal BigDecimal basePrice,
    String currencyCode,
    BillingCycle billingCycle,
    Instant effectiveFrom,
    Instant effectiveUntil,
    List<SubscriptionFeatureSnapshot> features,
    List<SubscriptionAddOnSnapshot> addOns,
    List<SubscriptionQuotaPackageSnapshot> quotaPackages,
    UUID planPriceEntryId,
    SubscriptionCommercialPolicyEvaluation commercialPolicyEvaluation,
    SubscriptionOfferEvaluation offerEvaluation) {
  public static final int SCHEMA_VERSION_V1 = 1;
  public static final int SCHEMA_VERSION_V2 = 2;
  public static final int SCHEMA_VERSION_V3 = 3;
  public static final int CURRENT_SCHEMA_VERSION = 4;

  public SubscriptionEntitlementSnapshot {
    schemaVersion = schemaVersion == 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
    if (schemaVersion != SCHEMA_VERSION_V1
        && schemaVersion != SCHEMA_VERSION_V2
        && schemaVersion != SCHEMA_VERSION_V3
        && schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Unsupported subscription snapshot schema version: " + schemaVersion);
    }
    if (effectiveFrom != null && effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "Subscription snapshot effectiveUntil must be after effectiveFrom");
    }
    features = features == null ? List.of() : List.copyOf(features);
    addOns = addOns == null ? List.of() : List.copyOf(addOns);
    quotaPackages = quotaPackages == null ? List.of() : List.copyOf(quotaPackages);
  }

  /** Source-compatible constructor for schema V1/V2 callers that predate policy terms. */
  public SubscriptionEntitlementSnapshot(
      int schemaVersion,
      String planCode,
      String planName,
      long planDefinitionVersion,
      BigDecimal basePrice,
      String currencyCode,
      BillingCycle billingCycle,
      Instant effectiveFrom,
      Instant effectiveUntil,
      List<SubscriptionFeatureSnapshot> features,
      List<SubscriptionAddOnSnapshot> addOns,
      List<SubscriptionQuotaPackageSnapshot> quotaPackages,
      UUID planPriceEntryId) {
    this(
        schemaVersion,
        planCode,
        planName,
        planDefinitionVersion,
        basePrice,
        currencyCode,
        billingCycle,
        effectiveFrom,
        effectiveUntil,
        features,
        addOns,
        quotaPackages,
        planPriceEntryId,
        null,
        null);
  }

  /** Source-compatible constructor for persisted and test-owned schema-V1 snapshots. */
  public SubscriptionEntitlementSnapshot(
      int schemaVersion,
      String planCode,
      String planName,
      long planDefinitionVersion,
      BigDecimal basePrice,
      String currencyCode,
      BillingCycle billingCycle,
      Instant effectiveFrom,
      Instant effectiveUntil,
      List<SubscriptionFeatureSnapshot> features,
      List<SubscriptionAddOnSnapshot> addOns,
      List<SubscriptionQuotaPackageSnapshot> quotaPackages) {
    this(
        schemaVersion,
        planCode,
        planName,
        planDefinitionVersion,
        basePrice,
        currencyCode,
        billingCycle,
        effectiveFrom,
        effectiveUntil,
        features,
        addOns,
        quotaPackages,
        null,
        null,
        null);
  }

  public SubscriptionEntitlementSnapshot(
      String planCode,
      BigDecimal basePrice,
      String currencyCode,
      BillingCycle billingCycle,
      List<SubscriptionFeatureSnapshot> features,
      List<SubscriptionAddOnSnapshot> addOns,
      List<SubscriptionQuotaPackageSnapshot> quotaPackages) {
    this(
        SCHEMA_VERSION_V1,
        planCode,
        null,
        0L,
        basePrice,
        currencyCode,
        billingCycle,
        null,
        null,
        features,
        addOns,
        quotaPackages,
        null,
        null);
  }

  public SubscriptionEntitlementSnapshot(
      String planCode,
      BigDecimal basePrice,
      String currencyCode,
      BillingCycle billingCycle,
      List<SubscriptionFeatureSnapshot> features,
      List<SubscriptionAddOnSnapshot> addOns) {
    this(planCode, basePrice, currencyCode, billingCycle, features, addOns, List.of());
  }

  public static SubscriptionEntitlementSnapshot empty(
      String planCode, BigDecimal basePrice, String currencyCode, BillingCycle billingCycle) {
    return new SubscriptionEntitlementSnapshot(
        planCode, basePrice, currencyCode, billingCycle, List.of(), List.of(), List.of());
  }

  public SubscriptionEntitlementSnapshot withEffectivePeriod(Instant from, Instant until) {
    return new SubscriptionEntitlementSnapshot(
        schemaVersion,
        planCode,
        planName,
        planDefinitionVersion,
        basePrice,
        currencyCode,
        billingCycle,
        from,
        until,
        features,
        addOns,
        quotaPackages,
        planPriceEntryId,
        commercialPolicyEvaluation,
        offerEvaluation);
  }

  public SubscriptionEntitlementSnapshot withCommercialPolicyEvaluation(
      SubscriptionCommercialPolicyEvaluation evaluation) {
    return new SubscriptionEntitlementSnapshot(
        CURRENT_SCHEMA_VERSION,
        planCode,
        planName,
        planDefinitionVersion,
        basePrice,
        currencyCode,
        billingCycle,
        effectiveFrom,
        effectiveUntil,
        features,
        addOns,
        quotaPackages,
        planPriceEntryId,
        evaluation,
        offerEvaluation);
  }

  /** Source-compatible schema-V3 constructor for callers that predate Offer provenance. */
  public SubscriptionEntitlementSnapshot(
      int schemaVersion,
      String planCode,
      String planName,
      long planDefinitionVersion,
      BigDecimal basePrice,
      String currencyCode,
      BillingCycle billingCycle,
      Instant effectiveFrom,
      Instant effectiveUntil,
      List<SubscriptionFeatureSnapshot> features,
      List<SubscriptionAddOnSnapshot> addOns,
      List<SubscriptionQuotaPackageSnapshot> quotaPackages,
      UUID planPriceEntryId,
      SubscriptionCommercialPolicyEvaluation commercialPolicyEvaluation) {
    this(
        schemaVersion,
        planCode,
        planName,
        planDefinitionVersion,
        basePrice,
        currencyCode,
        billingCycle,
        effectiveFrom,
        effectiveUntil,
        features,
        addOns,
        quotaPackages,
        planPriceEntryId,
        commercialPolicyEvaluation,
        null);
  }

  public SubscriptionEntitlementSnapshot withOfferEvaluation(
      SubscriptionOfferEvaluation evaluation) {
    List<SubscriptionFeatureSnapshot> adjustedFeatures = applyOfferQuotaBonuses(evaluation);
    return new SubscriptionEntitlementSnapshot(
        CURRENT_SCHEMA_VERSION,
        planCode,
        planName,
        planDefinitionVersion,
        basePrice,
        currencyCode,
        billingCycle,
        effectiveFrom,
        effectiveUntil,
        adjustedFeatures,
        addOns,
        quotaPackages,
        planPriceEntryId,
        commercialPolicyEvaluation,
        evaluation);
  }

  private List<SubscriptionFeatureSnapshot> applyOfferQuotaBonuses(
      SubscriptionOfferEvaluation evaluation) {
    if (evaluation.quotaBonuses().isEmpty()) return features;
    List<SubscriptionFeatureSnapshot> adjusted = new ArrayList<>();
    for (SubscriptionFeatureSnapshot feature : features) {
      List<QuotaLimitEntry> quotas = new ArrayList<>();
      for (QuotaLimitEntry quota : feature.quotaConfigs()) {
        long bonus =
            evaluation.quotaBonuses().stream()
                .filter(
                    item ->
                        item.featureCode().equals(feature.featureCode())
                            && item.resource().equals(quota.resource()))
                .mapToLong(CommercialOfferEffectSnapshot.QuotaBonus::quantity)
                .reduce(0L, Math::addExact);
        if (bonus > 0 && quota.mode() != QuotaLimitMode.FINITE) {
          throw new IllegalStateException("Offer quota bonus targets a non-finite quota.");
        }
        quotas.add(
            bonus == 0
                ? quota
                : new QuotaLimitEntry(
                    quota.resource(), quota.mode(), Math.addExact(quota.limit(), bonus)));
      }
      adjusted.add(new SubscriptionFeatureSnapshot(feature.featureCode(), quotas));
    }
    for (var bonus : evaluation.quotaBonuses()) {
      boolean found =
          features.stream()
              .anyMatch(
                  feature ->
                      feature.featureCode().equals(bonus.featureCode())
                          && feature.quotaConfigs().stream()
                              .anyMatch(quota -> quota.resource().equals(bonus.resource())));
      if (!found)
        throw new IllegalStateException(
            "Offer quota bonus target is absent from the accepted selection.");
    }
    return List.copyOf(adjusted);
  }
}
