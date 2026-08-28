package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.dto.CommercialOfferSelection;
import com.hiveapp.platform.client.plan.dto.CommercialOfferViews;
import com.hiveapp.shared.exception.OfferNotAvailableException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds customer-safe projections and validates that every stored price matches its product. */
@Component
@RequiredArgsConstructor
class CommercialOfferClientProjectionMapper {
  private final ProductPriceRepository prices;

  Map<UUID, ProductPrice> exactPrices(Collection<CommercialOffer> offerPage) {
    var ids = new LinkedHashSet<UUID>();
    for (CommercialOffer offer : offerPage) {
      ids.add(offer.getSelection().planPriceId());
      offer.getSelection().addOns().forEach(item -> ids.add(item.priceId()));
      offer.getSelection().quotaPackages().forEach(item -> ids.add(item.priceId()));
    }
    if (ids.isEmpty()) return Map.of();
    Map<UUID, ProductPrice> result =
        prices.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(ProductPrice::getId, price -> price));
    if (result.size() != ids.size()) throw new OfferNotAvailableException();
    return result;
  }

  CommercialOfferViews.ClientOffer client(
      CommercialOffer offer, Map<UUID, ProductPrice> exactPrices) {
    var selection = offer.getSelection();
    ProductPrice planPrice = exactPrice(exactPrices, selection.planPriceId());
    if (planPrice.getOwnerType() != ProductPriceOwnerType.PLAN
        || !planPrice.getPlan().getId().equals(selection.planId())) {
      throw new OfferNotAvailableException();
    }
    var addOnProducts =
        selection.addOns().stream()
            .map(
                item -> {
                  ProductPrice price = exactPrice(exactPrices, item.priceId());
                  if (price.getOwnerType() != ProductPriceOwnerType.ADD_ON
                      || !price.getAddOn().getId().equals(item.addOnId())) {
                    throw new OfferNotAvailableException();
                  }
                  return clientProduct(
                      price.getAddOn().getCode(),
                      price.getAddOn().getName(),
                      price.getAddOn().getRevisionNumber(),
                      price,
                      item.pricingMode(),
                      1);
                })
            .toList();
    var packageProducts =
        selection.quotaPackages().stream()
            .map(
                item -> {
                  ProductPrice price = exactPrice(exactPrices, item.priceId());
                  if (price.getOwnerType() != ProductPriceOwnerType.QUOTA_PACKAGE
                      || !price.getQuotaPackage().getId().equals(item.quotaPackageId())) {
                    throw new OfferNotAvailableException();
                  }
                  return clientProduct(
                      price.getQuotaPackage().getCode(),
                      price.getQuotaPackage().getName(),
                      price.getQuotaPackage().getRevisionNumber(),
                      price,
                      item.pricingMode(),
                      item.quantity());
                })
            .toList();
    return new CommercialOfferViews.ClientOffer(
        offer.getId(),
        offer.getName(),
        offer.getDescription(),
        offer.getEndsAt(),
        planPrice.getPlan().getCode(),
        offer.getEffects().discountType(),
        offer.getEffects().discountAmount(),
        offer.getEffects().percentage(),
        offer.getEffects().percentageCap(),
        new CommercialOfferViews.ClientSelection(
            clientProduct(
                planPrice.getPlan().getCode(),
                planPrice.getPlan().getName(),
                planPrice.getPlan().getRevisionNumber(),
                planPrice,
                CommercialOfferSelection.PricingMode.PAID,
                1),
            addOnProducts,
            packageProducts,
            selection.timing()),
        offer.getEffects());
  }

  CommercialOfferViews.AcceptedTerms accepted(CommercialOfferRedemption redemption) {
    var evaluation = redemption.getCommercialEvaluation();
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

  CommercialOfferViews.ClientRedemption clientRedemption(
      CommercialOfferRedemption redemption, Map<UUID, ProductPrice> exactPrices) {
    CommercialOffer offer = redemption.getOffer();
    return new CommercialOfferViews.ClientRedemption(
        redemption.getId(),
        offer.getId(),
        offer.getName(),
        offer.getRevisionNumber(),
        client(offer, exactPrices).selection(),
        redemption.getStatus(),
        redemption.getSubscriptionOperationId(),
        redemption.getReservedAt(),
        redemption.getAppliedAt(),
        redemption.getReleasedAt(),
        accepted(redemption));
  }

  private ProductPrice exactPrice(Map<UUID, ProductPrice> pricesById, UUID id) {
    ProductPrice price = pricesById.get(id);
    if (price == null) throw new OfferNotAvailableException();
    return price;
  }

  private CommercialOfferViews.ClientProduct clientProduct(
      String code,
      String name,
      int revision,
      ProductPrice price,
      CommercialOfferSelection.PricingMode pricingMode,
      int quantity) {
    return new CommercialOfferViews.ClientProduct(
        code,
        name,
        revision,
        price.getAmount(),
        price.getCurrencyCode(),
        price.getBillingCycle(),
        pricingMode,
        quantity);
  }
}
