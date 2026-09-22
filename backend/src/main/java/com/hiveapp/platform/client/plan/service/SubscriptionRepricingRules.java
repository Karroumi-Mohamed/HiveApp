package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.shared.exception.InvalidRequestException;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Pure copy-on-write rule: only an exact component's tariff identity and amount may change. */
@Component
public class SubscriptionRepricingRules {
  public void requirePair(ProductPrice source, ProductPrice target) {
    if (source.getId().equals(target.getId())
        || source.getOwnerType() != target.getOwnerType()
        || !source.ownerId().equals(target.ownerId())
        || !source.getCurrencyCode().equals(target.getCurrencyCode())
        || source.getBillingCycle() != target.getBillingCycle()) {
      throw new InvalidRequestException(
          "Choose two different tariffs for the same product revision, currency and billing"
              + " cycle.");
    }
  }

  public int quantity(SubscriptionEntitlementSnapshot snapshot, ProductPrice source) {
    if (snapshot == null) return 0;
    return switch (source.getOwnerType()) {
      case PLAN -> Objects.equals(snapshot.planPriceEntryId(), source.getId()) ? 1 : 0;
      case ADD_ON ->
          snapshot.addOns().stream().anyMatch(a -> Objects.equals(a.priceEntryId(), source.getId()))
              ? 1
              : 0;
      case QUOTA_PACKAGE ->
          snapshot.quotaPackages().stream()
              .filter(p -> Objects.equals(p.priceEntryId(), source.getId()))
              .mapToInt(SubscriptionQuotaPackageSnapshot::quantity)
              .sum();
    };
  }

  public SubscriptionEntitlementSnapshot replace(
      SubscriptionEntitlementSnapshot s, ProductPrice source, ProductPrice target) {
    requirePair(source, target);
    if (quantity(s, source) == 0)
      throw new InvalidRequestException("The Account does not hold the selected source tariff.");
    boolean plan =
        source.getOwnerType()
            == com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN;
    return new SubscriptionEntitlementSnapshot(
        s.schemaVersion(),
        s.planCode(),
        s.planName(),
        s.planDefinitionVersion(),
        plan ? target.getAmount() : s.basePrice(),
        s.currencyCode(),
        s.billingCycle(),
        s.effectiveFrom(),
        s.effectiveUntil(),
        s.features(),
        s.addOns().stream()
            .map(
                a ->
                    Objects.equals(a.priceEntryId(), source.getId())
                        ? new SubscriptionAddOnSnapshot(
                            a.code(),
                            a.name(),
                            a.definitionVersion(),
                            target.getAmount(),
                            a.currencyCode(),
                            a.billingCycle(),
                            a.featureCodes(),
                            target.getId())
                        : a)
            .toList(),
        s.quotaPackages().stream()
            .map(
                p ->
                    Objects.equals(p.priceEntryId(), source.getId())
                        ? new SubscriptionQuotaPackageSnapshot(
                            p.code(),
                            p.name(),
                            p.definitionVersion(),
                            p.featureCode(),
                            p.resource(),
                            p.capacityPerUnit(),
                            p.quantity(),
                            target.getAmount(),
                            p.currencyCode(),
                            p.billingCycle(),
                            target.getId())
                        : p)
            .toList(),
        plan ? target.getId() : s.planPriceEntryId(),
        s.commercialPolicyEvaluation(),
        s.offerEvaluation(),
        s.financialPlanSource());
  }

  public Instant effectiveAt(
      Instant periodEnd,
      Instant notBefore,
      com.hiveapp.platform.client.plan.domain.constant.BillingCycle cycle,
      SubscriptionPeriodCalculator periods) {
    Instant result = periodEnd;
    for (int n = 0; notBefore != null && result.isBefore(notBefore); n++) {
      if (n >= 1200) throw new InvalidRequestException("Effective date is too far in the future.");
      result = periods.recurring(cycle, result).endsAt();
    }
    return result;
  }
}
