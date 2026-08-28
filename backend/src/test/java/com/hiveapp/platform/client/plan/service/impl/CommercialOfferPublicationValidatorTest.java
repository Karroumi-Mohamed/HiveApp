package com.hiveapp.platform.client.plan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaign;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.CommercialOfferSelection;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommercialOfferPublicationValidatorTest {
  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

  @Mock private CommercialOfferRepository offers;
  @Mock private ProductPriceRepository prices;
  @Mock private PlanFeatureRepository planFeatures;
  @Mock private AddOnRepository addOns;
  @Mock private QuotaPackageRepository quotaPackages;
  @Mock private CommercialCatalogResolver catalogResolver;
  @Mock private CommercialOffer offer;
  @Mock private CommercialCampaign campaign;
  @Mock private Plan plan;
  @Mock private ProductPrice planPrice;

  @Test
  void operationalActionsDeeplyValidateASelectionWithoutMakingListRowsQueryHeavy() {
    UUID planId = UUID.randomUUID();
    UUID priceId = UUID.randomUUID();
    when(offer.getStatus()).thenReturn(CommercialOfferStatus.DRAFT);
    when(offer.getDiscovery()).thenReturn(CommercialOfferDiscovery.CATALOG);
    when(offer.getCampaign()).thenReturn(campaign);
    when(offer.getStartsAt()).thenReturn(NOW.plusSeconds(60));
    when(offer.getEndsAt()).thenReturn(NOW.plusSeconds(3600));
    when(offer.getSelection())
        .thenReturn(
            new CommercialOfferSelection(
                planId, priceId, List.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE));
    when(campaign.getStatus()).thenReturn(CommercialCampaignStatus.SCHEDULED);
    when(campaign.getStartsAt()).thenReturn(NOW);
    when(campaign.getEndsAt()).thenReturn(NOW.plusSeconds(7200));
    when(prices.findOwned(ProductPriceOwnerType.PLAN, planId, priceId))
        .thenReturn(Optional.empty());

    var validator =
        new CommercialOfferPublicationValidator(
            offers,
            prices,
            planFeatures,
            addOns,
            quotaPackages,
            catalogResolver,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(validator.actionBlockers(offer, false))
        .doesNotContain(CommercialOfferBlocker.INVALID_SELECTION);
    assertThat(validator.hasInvalidSelection(offer)).isTrue();
  }

  @Test
  void publicCampaignCannotPublishAnExactDirectOnlyProduct() {
    UUID planId = UUID.randomUUID();
    UUID priceId = UUID.randomUUID();
    when(offer.getSelection())
        .thenReturn(
            new CommercialOfferSelection(
                planId, priceId, List.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE));
    when(offer.getStartsAt()).thenReturn(NOW.plusSeconds(60));
    when(offer.getEndsAt()).thenReturn(NOW.plusSeconds(3600));
    when(offer.getCampaign()).thenReturn(campaign);
    when(campaign.getAudienceMode()).thenReturn(CommercialCampaignAudienceMode.PUBLIC);
    when(prices.findOwned(ProductPriceOwnerType.PLAN, planId, priceId))
        .thenReturn(Optional.of(planPrice));
    when(planPrice.getStatus()).thenReturn(ProductPriceStatus.ACTIVE);
    when(planPrice.getEffectiveFrom()).thenReturn(NOW);
    when(planPrice.getEffectiveUntil()).thenReturn(NOW.plusSeconds(7200));
    when(planPrice.getPlan()).thenReturn(plan);
    when(plan.getSalesVisibility()).thenReturn(ProductSalesVisibility.DIRECT_ONLY);

    var validator =
        new CommercialOfferPublicationValidator(
            offers,
            prices,
            planFeatures,
            addOns,
            quotaPackages,
            catalogResolver,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(validator.hasInvalidSelection(offer)).isTrue();
  }
}
