package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.CommercialOfferCodeHasher;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.quota.QuotaLimitMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** One semantic assessment shared by Offer authoring, persistence, and publication. */
@Component
@RequiredArgsConstructor
class CommercialOfferDefinitionAssessor {
  private static final Set<CommercialCampaignStatus> USABLE_CAMPAIGNS =
      Set.of(
          CommercialCampaignStatus.SCHEDULED,
          CommercialCampaignStatus.ACTIVE,
          CommercialCampaignStatus.PAUSED);

  private final CommercialCampaignRepository campaigns;
  private final CommercialOfferCodeReservationRepository codes;
  private final ProductPriceRepository prices;
  private final PlanFeatureRepository planFeatures;
  private final AddOnRepository addOns;
  private final QuotaPackageRepository quotaPackages;
  private final CommercialCatalogResolver catalogResolver;
  private final CommercialOfferCodeHasher codeHasher;
  private final CommercialOfferClientProjectionMapper clientProjection;
  private final CommercialOfferAdminProjectionMapper adminProjection;
  private final Clock clock;

  Assessment assessCreate(CommercialOfferRequests.Create request) {
    CommercialCampaign campaign = campaigns.findDetailById(request.campaignId()).orElse(null);
    List<CommercialOfferViews.DefinitionIssue> issues = new ArrayList<>();
    if (campaign == null) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.CAMPAIGN_UNAVAILABLE,
          "campaignId",
          "The selected Campaign revision is unavailable.");
    }
    CodeState code = codeState(request.customerCode(), null, null, issues);
    return assess(
        campaign,
        request.discovery(),
        code.configured(),
        request.startsAt(),
        request.endsAt(),
        request.selection(),
        request.effects(),
        true,
        issues);
  }

  Assessment assessUpdate(CommercialOffer offer, CommercialOfferRequests.Update request) {
    boolean editable = lineageTermsEditable(offer);
    List<CommercialOfferViews.DefinitionIssue> issues = new ArrayList<>();
    CommercialOfferDiscovery discovery = offer.getDiscovery();
    boolean codeConfigured = offer.getCustomerCodeHash() != null;
    if (request.lineageTerms() != null) {
      if (!editable) {
        issue(
            issues,
            CommercialOfferDefinitionIssueCode.LINEAGE_TERMS_IMMUTABLE,
            "lineageTerms",
            "Published Offer lineage terms are immutable.");
      } else {
        discovery = request.lineageTerms().discovery();
        var change = request.lineageTerms().customerCodeChange();
        switch (change.mode()) {
          case KEEP -> codeConfigured = offer.getCustomerCodeHash() != null;
          case REMOVE -> codeConfigured = false;
          case REPLACE ->
              codeConfigured =
                  codeState(
                          change.value(),
                          offer.getLineageId(),
                          "lineageTerms.customerCodeChange.value",
                          issues)
                      .configured();
        }
      }
    }
    return assess(
        offer.getCampaign(),
        discovery,
        codeConfigured,
        request.startsAt(),
        request.endsAt(),
        request.selection(),
        request.effects(),
        editable,
        issues);
  }

  Assessment assessOffer(CommercialOffer offer) {
    return assess(
        offer.getCampaign(),
        offer.getDiscovery(),
        offer.getCustomerCodeHash() != null,
        offer.getStartsAt(),
        offer.getEndsAt(),
        offer.getSelection(),
        offer.getEffects(),
        lineageTermsEditable(offer),
        new ArrayList<>());
  }

  void requireValid(Assessment assessment) {
    if (!assessment.valid()) {
      throw new InvalidRequestException(assessment.issues().getFirst().message());
    }
  }

  boolean lineageTermsEditable(CommercialOffer offer) {
    return offer.getRevisionNumber() == 1 && offer.getLineage().getFirstPublishedAt() == null;
  }

  private Assessment assess(
      CommercialCampaign campaign,
      CommercialOfferDiscovery discovery,
      boolean codeConfigured,
      Instant startsAt,
      Instant endsAt,
      CommercialOfferSelection selection,
      CommercialOfferEffectSnapshot effects,
      boolean lineageTermsEditable,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    validateWindow(campaign, startsAt, endsAt, issues);
    if (discovery == CommercialOfferDiscovery.CODE_ONLY && !codeConfigured) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.CUSTOMER_CODE_REQUIRED,
          "customerCode",
          "A code-only Offer requires a customer code.");
    }
    validateEffects(effects, issues);
    CommercialOfferViews.ClientSelection resolved =
        validateSelection(campaign, startsAt, endsAt, selection, effects, issues);
    CommercialOfferViews.CampaignChoice campaignView =
        campaign == null ? null : adminProjection.campaignChoice(campaign);
    return new Assessment(
        campaignView,
        resolved,
        lineageTermsEditable,
        List.copyOf(new LinkedHashSet<>(issues)));
  }

  private void validateWindow(
      CommercialCampaign campaign,
      Instant startsAt,
      Instant endsAt,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.INVALID_WINDOW,
          "endsAt",
          "Offer end must be after start.");
      return;
    }
    if (campaign == null) return;
    if (!USABLE_CAMPAIGNS.contains(campaign.getStatus())) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.CAMPAIGN_UNAVAILABLE,
          "campaignId",
          "The Campaign is not available for an Offer.");
    }
    if (startsAt.isBefore(campaign.getStartsAt()) || endsAt.isAfter(campaign.getEndsAt())) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.WINDOW_OUTSIDE_CAMPAIGN,
          "startsAt",
          "The Offer window must stay inside the exact Campaign revision window.");
    }
    if (!clock.instant().isBefore(endsAt)) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.WINDOW_ENDED,
          "endsAt",
          "The Offer window has already ended.");
    }
  }

  private CommercialOfferViews.ClientSelection validateSelection(
      CommercialCampaign campaign,
      Instant startsAt,
      Instant endsAt,
      CommercialOfferSelection selection,
      CommercialOfferEffectSnapshot effects,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    if (selection == null) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.SELECTION_INCOMPATIBLE,
          "selection",
          "An exact commercial selection is required.");
      return null;
    }
    Map<UUID, ProductPrice> byId = clientProjection.exactPrices(selection);
    ProductPrice planPrice = byId.get(selection.planPriceId());
    if (planPrice == null
        || planPrice.getOwnerType() != ProductPriceOwnerType.PLAN
        || !Objects.equals(planPrice.getPlan().getId(), selection.planId())) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.PRICE_OWNER_MISMATCH,
          "selection.planPriceId",
          "The exact Plan price does not belong to the selected Plan revision.");
      return null;
    }

    String currency = planPrice.getCurrencyCode();
    BillingCycle cycle = planPrice.getBillingCycle();
    validatePrice(planPrice, startsAt, endsAt, "selection.planPriceId", issues);
    validateDirectOnly(campaign, planPrice.getPlan().getSalesVisibility(), "selection.planId", issues);

    Set<UUID> productIds = new HashSet<>();
    Set<String> productCodes = new HashSet<>();
    Set<UUID> selectedAddOnIds = new LinkedHashSet<>();
    Set<String> addOnCodes = new LinkedHashSet<>();
    boolean structurallyValid = true;
    for (int i = 0; i < selection.addOns().size(); i++) {
      var selected = selection.addOns().get(i);
      ProductPrice price = byId.get(selected.priceId());
      String path = "selection.addOns[" + i + "]";
      if (price == null
          || price.getOwnerType() != ProductPriceOwnerType.ADD_ON
          || !Objects.equals(price.getAddOn().getId(), selected.addOnId())) {
        issue(
            issues,
            CommercialOfferDefinitionIssueCode.PRICE_OWNER_MISMATCH,
            path + ".priceId",
            "An exact Add-on price does not belong to the selected Add-on revision.");
        structurallyValid = false;
        continue;
      }
      if (!productIds.add(selected.addOnId()) || !productCodes.add(price.getAddOn().getCode())) {
        issue(
            issues,
            CommercialOfferDefinitionIssueCode.DUPLICATE_PRODUCT_REVISION,
            path,
            "Only one revision of an Add-on may be selected.");
      }
      selectedAddOnIds.add(selected.addOnId());
      addOnCodes.add(price.getAddOn().getCode());
      validateTerms(price, currency, cycle, startsAt, endsAt, path, issues);
      validateDirectOnly(campaign, price.getAddOn().getSalesVisibility(), path, issues);
    }

    productIds.clear();
    productCodes.clear();
    List<QuotaPackageSelection> packageSelections = new ArrayList<>();
    for (int i = 0; i < selection.quotaPackages().size(); i++) {
      var selected = selection.quotaPackages().get(i);
      ProductPrice price = byId.get(selected.priceId());
      String path = "selection.quotaPackages[" + i + "]";
      if (price == null
          || price.getOwnerType() != ProductPriceOwnerType.QUOTA_PACKAGE
          || !Objects.equals(price.getQuotaPackage().getId(), selected.quotaPackageId())) {
        issue(
            issues,
            CommercialOfferDefinitionIssueCode.PRICE_OWNER_MISMATCH,
            path + ".priceId",
            "An exact capacity-package price does not belong to the selected revision.");
        structurallyValid = false;
        continue;
      }
      if (!productIds.add(selected.quotaPackageId())
          || !productCodes.add(price.getQuotaPackage().getCode())) {
        issue(
            issues,
            CommercialOfferDefinitionIssueCode.DUPLICATE_PRODUCT_REVISION,
            path,
            "Only one revision of a capacity package may be selected.");
      }
      packageSelections.add(
          new QuotaPackageSelection(price.getQuotaPackage().getCode(), selected.quantity()));
      validateTerms(price, currency, cycle, startsAt, endsAt, path, issues);
      validateDirectOnly(campaign, price.getQuotaPackage().getSalesVisibility(), path, issues);
    }

    validateQuotaBonuses(selection.planId(), selectedAddOnIds, effects, issues);
    if (structurallyValid) {
      try {
        var candidate =
            new CommercialCatalogResolver.ExactSelectionCandidate(
                UUID.randomUUID(),
                selection.planId(),
                CommercialCatalogResolver.PriceTuple.from(planPrice),
                addOnCodes,
                packageSelections);
        var resolution =
            catalogResolver
                .resolveExactSelectionCandidates(
                    List.of(candidate), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR)
                .get(candidate.key());
        if (resolution == null) throw new IllegalArgumentException("Plan revision is unavailable.");
        CommercialCatalogResolver.requireSelectable(
            resolution, CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
      } catch (RuntimeException incompatible) {
        issue(
            issues,
            CommercialOfferDefinitionIssueCode.SELECTION_INCOMPATIBLE,
            "selection",
            "The exact selection is not commercially compatible.");
      }
    }
    try {
      return clientProjection.selection(selection, byId);
    } catch (RuntimeException unresolved) {
      return null;
    }
  }

  private void validatePrice(
      ProductPrice price,
      Instant startsAt,
      Instant endsAt,
      String fieldPath,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    if (startsAt == null
        || endsAt == null
        || price.getStatus() != ProductPriceStatus.ACTIVE
        || price.getEffectiveFrom().isAfter(startsAt)
        || price.getEffectiveUntil() != null && price.getEffectiveUntil().isBefore(endsAt)) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.PRICE_INACTIVE_OR_WINDOW_UNCOVERED,
          fieldPath,
          "The exact price must remain active for the Offer window.");
    }
  }

  private void validateTerms(
      ProductPrice price,
      String currency,
      BillingCycle cycle,
      Instant startsAt,
      Instant endsAt,
      String fieldPath,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    validatePrice(price, startsAt, endsAt, fieldPath + ".priceId", issues);
    if (!Objects.equals(price.getCurrencyCode(), currency)) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.CURRENCY_MISMATCH,
          fieldPath + ".priceId",
          "All exact prices must share one currency.");
    }
    if (price.getBillingCycle() != cycle) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.BILLING_CYCLE_MISMATCH,
          fieldPath + ".priceId",
          "All exact prices must share one billing cycle.");
    }
  }

  private void validateDirectOnly(
      CommercialCampaign campaign,
      ProductSalesVisibility visibility,
      String fieldPath,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    if (visibility == ProductSalesVisibility.DIRECT_ONLY
        && (campaign == null
            || campaign.getAudienceMode() == CommercialCampaignAudienceMode.PUBLIC)) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.DIRECT_ONLY_REQUIRES_TARGETED_CAMPAIGN,
          fieldPath,
          "DIRECT_ONLY products require a targeted frozen Campaign.");
    }
  }

  private void validateQuotaBonuses(
      UUID planId,
      Set<UUID> addOnIds,
      CommercialOfferEffectSnapshot effects,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    if (effects == null) return;
    Map<String, List<com.hiveapp.shared.quota.QuotaLimitEntry>> quotas = new HashMap<>();
    planFeatures.findAllByPlanId(planId).stream()
        .filter(item -> item.getMode() == PlanFeatureMode.INCLUDED)
        .forEach(item -> quotas.put(item.getFeature().getCode(), item.getQuotaConfigs()));
    if (!addOnIds.isEmpty()) {
      addOns.findAllDetailedByIdIn(addOnIds).stream()
          .flatMap(addOn -> addOn.getFeatures().stream())
          .forEach(feature -> quotas.put(feature.getFeature().getCode(), feature.getQuotaConfigs()));
    }
    for (int i = 0; i < effects.finiteQuotaBonuses().size(); i++) {
      var bonus = effects.finiteQuotaBonuses().get(i);
      boolean finite =
          quotas.getOrDefault(bonus.featureCode(), List.of()).stream()
              .anyMatch(
                  limit ->
                      Objects.equals(limit.resource(), bonus.resource())
                          && limit.mode() == QuotaLimitMode.FINITE);
      if (!finite) {
        issue(
            issues,
            CommercialOfferDefinitionIssueCode.QUOTA_BONUS_NOT_IN_SELECTION,
            "effects.finiteQuotaBonuses[" + i + "]",
            "An Offer quota bonus must target a finite quota in the exact selection.");
      }
    }
  }

  private void validateEffects(
      CommercialOfferEffectSnapshot effects,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    if (effects == null) return;
    boolean valid =
        switch (effects.discountType()) {
          case NONE ->
              effects.discountAmount() == null
                  && effects.percentage() == null
                  && effects.percentageCap() == null;
          case FIXED ->
              effects.discountAmount() != null
                  && effects.discountAmount().signum() > 0
                  && effects.percentage() == null
                  && effects.percentageCap() == null;
          case PERCENTAGE_WITH_CAP ->
              effects.percentage() != null
                  && effects.percentage().signum() > 0
                  && effects.percentage().compareTo(new java.math.BigDecimal("100")) <= 0
                  && effects.percentageCap() != null
                  && effects.percentageCap().signum() > 0
                  && effects.discountAmount() == null;
        };
    if (!valid) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.INVALID_DISCOUNT,
          "effects",
          "Offer discount terms are invalid.");
    }
  }

  private CodeState codeState(
      String raw,
      UUID allowedLineageId,
      String fieldPath,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    String path = fieldPath == null ? "customerCode" : fieldPath;
    String hash;
    try {
      hash = codeHasher.hash(raw);
    } catch (IllegalArgumentException invalid) {
      issue(
          issues,
          CommercialOfferDefinitionIssueCode.CUSTOMER_CODE_INVALID,
          path,
          "Customer code format is invalid.");
      return new CodeState(false);
    }
    if (hash != null) {
      codes.findByNormalizedCodeHash(hash)
          .filter(existing -> !Objects.equals(existing.getLineageId(), allowedLineageId))
          .ifPresent(
              ignored ->
                  issue(
                      issues,
                      CommercialOfferDefinitionIssueCode.CUSTOMER_CODE_UNAVAILABLE,
                      path,
                      "Customer code is unavailable."));
    }
    return new CodeState(hash != null);
  }

  private void issue(
      List<CommercialOfferViews.DefinitionIssue> issues,
      CommercialOfferDefinitionIssueCode code,
      String fieldPath,
      String message) {
    issues.add(new CommercialOfferViews.DefinitionIssue(code, fieldPath, message));
  }

  record Assessment(
      CommercialOfferViews.CampaignChoice campaign,
      CommercialOfferViews.ClientSelection resolvedSelection,
      boolean lineageTermsEditable,
      List<CommercialOfferViews.DefinitionIssue> issues) {
    Assessment {
      issues = List.copyOf(issues);
    }

    boolean valid() {
      return issues.isEmpty();
    }
  }

  private record CodeState(boolean configured) {}
}
