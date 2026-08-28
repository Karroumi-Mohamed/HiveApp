package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaign;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.dto.CommercialOfferViews;
import com.hiveapp.platform.client.plan.dto.SubscriptionOfferEvaluation;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Keeps privileged response projection separate from authorization and transaction orchestration.
 */
@Component
class CommercialOfferAdminProjectionMapper {

  CommercialOfferViews.ComparisonDefinition comparisonDefinition(CommercialOffer offer) {
    return new CommercialOfferViews.ComparisonDefinition(
        offer.getId(),
        offer.getBusinessCode(),
        offer.getName(),
        offer.getDescription(),
        offer.getStatus(),
        offer.getCampaign().getId(),
        offer.getCampaign().getCode(),
        offer.getDiscovery(),
        offer.getAcceptance(),
        offer.getCustomerCodeHash() != null,
        offer.getStartsAt(),
        offer.getEndsAt(),
        offer.getRevisionNumber(),
        offer.getGlobalLimit(),
        offer.getPerAccountLimit(),
        offer.getSelection(),
        offer.getEffects(),
        offer.getVersion());
  }

  CommercialOfferViews.PricedChoice pricedChoice(ProductPrice price) {
    return switch (price.getOwnerType()) {
      case PLAN ->
          new CommercialOfferViews.PricedChoice(
              price.getPlan().getId(),
              price.getPlan().getCode(),
              price.getPlan().getName(),
              price.getPlan().getRevisionNumber(),
              price.getId(),
              price.getRevisionNumber(),
              price.getAmount(),
              price.getCurrencyCode(),
              price.getBillingCycle(),
              price.getEffectiveFrom(),
              price.getEffectiveUntil(),
              price.getPlan().getStatus().name(),
              price.getStatus().name(),
              new CommercialOfferViews.ProductCompatibility(
                  price.getPlan().getSalesVisibility(),
                  price.getPlan().getExtensionPolicy().name(),
                  Set.of(),
                  Set.of(),
                  Set.of(),
                  Set.of(),
                  Set.of(),
                  null,
                  null,
                  null,
                  null,
                  null));
      case ADD_ON ->
          new CommercialOfferViews.PricedChoice(
              price.getAddOn().getId(),
              price.getAddOn().getCode(),
              price.getAddOn().getName(),
              price.getAddOn().getRevisionNumber(),
              price.getId(),
              price.getRevisionNumber(),
              price.getAmount(),
              price.getCurrencyCode(),
              price.getBillingCycle(),
              price.getEffectiveFrom(),
              price.getEffectiveUntil(),
              price.getAddOn().getStatus().name(),
              price.getStatus().name(),
              new CommercialOfferViews.ProductCompatibility(
                  price.getAddOn().getSalesVisibility(),
                  null,
                  price.getAddOn().getAllowedPlanCodes(),
                  price.getAddOn().getBlockedPlanCodes(),
                  price.getAddOn().getDependencyCodes(),
                  price.getAddOn().getExclusionCodes(),
                  Set.of(),
                  null,
                  null,
                  null,
                  null,
                  null));
      case QUOTA_PACKAGE ->
          new CommercialOfferViews.PricedChoice(
              price.getQuotaPackage().getId(),
              price.getQuotaPackage().getCode(),
              price.getQuotaPackage().getName(),
              price.getQuotaPackage().getRevisionNumber(),
              price.getId(),
              price.getRevisionNumber(),
              price.getAmount(),
              price.getCurrencyCode(),
              price.getBillingCycle(),
              price.getEffectiveFrom(),
              price.getEffectiveUntil(),
              price.getQuotaPackage().getStatus().name(),
              price.getStatus().name(),
              new CommercialOfferViews.ProductCompatibility(
                  price.getQuotaPackage().getSalesVisibility(),
                  null,
                  price.getQuotaPackage().getAllowedPlanCodes(),
                  Set.of(),
                  Set.of(),
                  Set.of(),
                  price.getQuotaPackage().getAllowedAddOnCodes(),
                  price.getQuotaPackage().getFeature().getCode(),
                  price.getQuotaPackage().getResource(),
                  price.getQuotaPackage().getCapacityPerUnit(),
                  price.getQuotaPackage().isRepeatable(),
                  price.getQuotaPackage().getMaximumQuantity()));
    };
  }

  CommercialOfferViews.OwnerChoice ownerChoice(AdminUser owner) {
    var user = owner.getUser();
    return new CommercialOfferViews.OwnerChoice(
        owner.getId(), user.getEmail(), user.getUsername(), user.getFullName());
  }

  CommercialOfferViews.CampaignChoice campaignChoice(CommercialCampaign campaign) {
    return new CommercialOfferViews.CampaignChoice(
        campaign.getId(),
        campaign.getCode(),
        campaign.getName(),
        campaign.getStatus(),
        campaign.getAudienceMode(),
        campaign.getRevisionNumber(),
        campaign.getStartsAt(),
        campaign.getEndsAt());
  }

  CommercialOfferViews.Redemption redemption(CommercialOfferRedemption redemption) {
    return new CommercialOfferViews.Redemption(
        redemption.getId(),
        redemption.getOffer().getId(),
        redemption.getOffer().getName(),
        redemption.getOfferLineageId(),
        redemption.getOffer().getRevisionNumber(),
        redemption.getCampaignId(),
        redemption.getOffer().getSelection(),
        redemption.getStatus(),
        redemption.getSurface(),
        redemption.getSubscriptionOperationId(),
        redemption.getReservedAt(),
        redemption.getAppliedAt(),
        redemption.getReleasedAt(),
        redemption.getTerminalReason(),
        accepted(redemption.getCommercialEvaluation()));
  }

  CommercialOfferViews.AcceptedTerms accepted(SubscriptionOfferEvaluation evaluation) {
    return new CommercialOfferViews.AcceptedTerms(
        evaluation.catalogueSubtotal(),
        evaluation.policyPrice(),
        evaluation.offerPrice(),
        evaluation.finalPrice(),
        evaluation.currencyCode(),
        evaluation.discountWinner(),
        evaluation.winnerReason(),
        evaluation.quotaBonuses());
  }
}
