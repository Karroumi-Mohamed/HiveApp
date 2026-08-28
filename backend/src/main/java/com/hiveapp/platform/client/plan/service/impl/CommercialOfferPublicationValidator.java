package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.shared.exception.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Owns the exact product, price, quota, window, and Campaign checks for Offer publication. */
@Component
@RequiredArgsConstructor
class CommercialOfferPublicationValidator {
  private final CommercialOfferRepository offers;
  private final ProductPriceRepository prices;
  private final PlanFeatureRepository planFeatures;
  private final AddOnRepository addOns;
  private final QuotaPackageRepository quotaPackages;
  private final CommercialCatalogResolver catalogResolver;
  private final Clock clock;

  List<String> blockers(CommercialOffer offer) {
    List<String> blockers = new ArrayList<>();
    if (offer.getStatus() != CommercialOfferStatus.DRAFT
        && offer.getStatus() != CommercialOfferStatus.RETIRED) {
      blockers.add("NOT_DRAFT_OR_RETIRED");
    }
    if (offer.getStatus() == CommercialOfferStatus.RETIRED
        && offers
            .findFirstByLineage_IdAndStatus(offer.getLineageId(), CommercialOfferStatus.PUBLISHED)
            .isPresent()) {
      blockers.add(CommercialOfferBlocker.PUBLISHED_SUCCESSOR_EXISTS.name());
    }
    blockers.addAll(basicBlockers(offer));
    try {
      validateSelection(offer);
    } catch (InvalidRequestException
        | InvalidStateException
        | OperationBlockedException
        | ResourceNotFoundException
        | IllegalArgumentException expectedInvalidDefinition) {
      blockers.add(CommercialOfferBlocker.INVALID_SELECTION.name());
    }
    return List.copyOf(new LinkedHashSet<>(blockers));
  }

  List<CommercialOfferBlocker> actionBlockers(
      CommercialOffer offer, boolean publishedSuccessorExists) {
    List<CommercialOfferBlocker> blockers = new ArrayList<>();
    for (String blocker : basicBlockers(offer)) {
      blockers.add(CommercialOfferBlocker.valueOf(blocker));
    }
    if (offer.getStatus() == CommercialOfferStatus.RETIRED && publishedSuccessorExists) {
      blockers.add(CommercialOfferBlocker.PUBLISHED_SUCCESSOR_EXISTS);
    }
    return List.copyOf(new LinkedHashSet<>(blockers));
  }

  boolean hasInvalidSelection(CommercialOffer offer) {
    try {
      validateSelection(offer);
      return false;
    } catch (InvalidRequestException
        | InvalidStateException
        | OperationBlockedException
        | ResourceNotFoundException
        | IllegalArgumentException invalidDefinition) {
      return true;
    }
  }

  void validateSelection(CommercialOffer offer) {
    var selection = offer.getSelection();
    var planPrice =
        prices
            .findOwned(ProductPriceOwnerType.PLAN, selection.planId(), selection.planPriceId())
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "Exact Plan price does not belong to the selected Plan."));
    validatePrice(planPrice, offer);
    requireDirectOnlyAudience(offer, planPrice.getPlan().getSalesVisibility());
    String currency = planPrice.getCurrencyCode();
    var cycle = planPrice.getBillingCycle();
    Set<UUID> addOnIds = new HashSet<>();
    Set<String> addOnProductCodes = new HashSet<>();
    for (var item : selection.addOns()) {
      if (!addOnIds.add(item.addOnId())) {
        throw new InvalidRequestException("An Add-on can be selected only once.");
      }
      var price =
          prices
              .findOwned(ProductPriceOwnerType.ADD_ON, item.addOnId(), item.priceId())
              .orElseThrow(() -> new InvalidRequestException("Exact Add-on price is invalid."));
      if (!addOnProductCodes.add(price.getAddOn().getCode())) {
        throw new InvalidRequestException("Only one revision of an Add-on may be selected.");
      }
      sameTerms(price, currency, cycle, offer);
      requireDirectOnlyAudience(offer, price.getAddOn().getSalesVisibility());
    }
    Set<UUID> packageIds = new HashSet<>();
    Set<String> packageProductCodes = new HashSet<>();
    for (var item : selection.quotaPackages()) {
      if (!packageIds.add(item.quotaPackageId())) {
        throw new InvalidRequestException("A capacity package can be selected only once.");
      }
      var price =
          prices
              .findOwned(ProductPriceOwnerType.QUOTA_PACKAGE, item.quotaPackageId(), item.priceId())
              .orElseThrow(
                  () -> new InvalidRequestException("Exact capacity-package price is invalid."));
      if (!packageProductCodes.add(price.getQuotaPackage().getCode())) {
        throw new InvalidRequestException(
            "Only one revision of a capacity package may be selected.");
      }
      sameTerms(price, currency, cycle, offer);
      requireDirectOnlyAudience(offer, price.getQuotaPackage().getSalesVisibility());
    }
    validateQuotaBonuses(offer, addOnIds);
    Set<String> addOnCodes =
        addOnIds.isEmpty()
            ? Set.of()
            : addOns.findAllDetailedByIdIn(addOnIds).stream()
                .map(AddOn::getCode)
                .collect(java.util.stream.Collectors.toSet());
    List<QuotaPackageSelection> selectedPackages =
        selection.quotaPackages().stream()
            .map(
                item ->
                    new QuotaPackageSelection(
                        quotaPackages
                            .findById(item.quotaPackageId())
                            .orElseThrow(
                                () -> new InvalidRequestException("Capacity package is missing."))
                            .getCode(),
                        item.quantity()))
            .toList();
    CommercialCatalogResolver.requireSelectable(
        catalogResolver.resolveSelection(
            planPrice.getPlan(),
            planPrice,
            addOnCodes,
            selectedPackages,
            CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR),
        CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
  }

  private List<String> basicBlockers(CommercialOffer offer) {
    List<String> blockers = new ArrayList<>();
    if (offer.getDiscovery() == CommercialOfferDiscovery.CODE_ONLY
        && offer.getCustomerCodeHash() == null) {
      blockers.add(CommercialOfferBlocker.CODE_REQUIRED.name());
    }
    if (!Set.of(
            CommercialCampaignStatus.SCHEDULED,
            CommercialCampaignStatus.ACTIVE,
            CommercialCampaignStatus.PAUSED)
        .contains(offer.getCampaign().getStatus())) {
      blockers.add(CommercialOfferBlocker.CAMPAIGN_NOT_LIVE.name());
    }
    if (offer.getStartsAt().isBefore(offer.getCampaign().getStartsAt())
        || offer.getEndsAt().isAfter(offer.getCampaign().getEndsAt())) {
      blockers.add(CommercialOfferBlocker.WINDOW_OUTSIDE_CAMPAIGN.name());
    }
    if (!clock.instant().isBefore(offer.getEndsAt())) {
      blockers.add(CommercialOfferBlocker.WINDOW_ENDED.name());
    }
    return blockers;
  }

  private void requireDirectOnlyAudience(CommercialOffer offer, ProductSalesVisibility visibility) {
    if (visibility == ProductSalesVisibility.DIRECT_ONLY
        && offer.getCampaign().getAudienceMode() == CommercialCampaignAudienceMode.PUBLIC) {
      throw new InvalidRequestException("DIRECT_ONLY products require a targeted frozen Campaign.");
    }
  }

  private void validateQuotaBonuses(CommercialOffer offer, Set<UUID> addOnIds) {
    Map<String, List<com.hiveapp.shared.quota.QuotaLimitEntry>> quotas = new HashMap<>();
    planFeatures.findAllByPlanId(offer.getSelection().planId()).stream()
        .filter(item -> item.getMode() == PlanFeatureMode.INCLUDED)
        .forEach(item -> quotas.put(item.getFeature().getCode(), item.getQuotaConfigs()));
    if (!addOnIds.isEmpty()) {
      for (var addOn : addOns.findAllDetailedByIdIn(addOnIds)) {
        for (var feature : addOn.getFeatures()) {
          quotas.put(feature.getFeature().getCode(), feature.getQuotaConfigs());
        }
      }
    }
    for (var bonus : offer.getEffects().finiteQuotaBonuses()) {
      boolean finite =
          quotas.getOrDefault(bonus.featureCode(), List.of()).stream()
              .anyMatch(
                  item ->
                      item.resource().equals(bonus.resource())
                          && item.mode() == com.hiveapp.shared.quota.QuotaLimitMode.FINITE);
      if (!finite) {
        throw new InvalidRequestException(
            "Offer quota bonus must target a finite quota in the exact selection: "
                + bonus.featureCode()
                + "."
                + bonus.resource());
      }
    }
  }

  private void validatePrice(ProductPrice price, CommercialOffer offer) {
    if (price.getStatus() != ProductPriceStatus.ACTIVE
        || price.getEffectiveFrom().isAfter(offer.getStartsAt())
        || price.getEffectiveUntil() != null
            && price.getEffectiveUntil().isBefore(offer.getEndsAt())) {
      throw new InvalidRequestException("Exact price must remain active for the Offer window.");
    }
  }

  private void sameTerms(
      ProductPrice price, String currency, BillingCycle cycle, CommercialOffer offer) {
    validatePrice(price, offer);
    if (!price.getCurrencyCode().equals(currency) || price.getBillingCycle() != cycle) {
      throw new InvalidRequestException(
          "Offer exact prices must share currency and billing cycle.");
    }
  }
}
