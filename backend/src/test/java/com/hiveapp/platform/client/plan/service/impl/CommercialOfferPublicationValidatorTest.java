package com.hiveapp.platform.client.plan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaign;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.CommercialOfferSelection;
import com.hiveapp.platform.client.plan.dto.CommercialOfferViews;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
  @Mock private CommercialOfferDefinitionAssessor definitionAssessor;
  @Mock private CommercialOffer offer;
  @Mock private CommercialCampaign campaign;
  @Mock private Plan plan;
  @Mock private ProductPrice planPrice;

  @BeforeEach
  void persistedPublicationState() {
    lenient().when(offer.getDiscovery()).thenReturn(CommercialOfferDiscovery.CATALOG);
    lenient().when(offer.getCampaign()).thenReturn(campaign);
    lenient().when(offer.getStartsAt()).thenReturn(NOW.plusSeconds(60));
    lenient().when(offer.getEndsAt()).thenReturn(NOW.plusSeconds(3600));
    lenient().when(campaign.getStatus()).thenReturn(CommercialCampaignStatus.SCHEDULED);
    lenient().when(campaign.getStartsAt()).thenReturn(NOW);
    lenient().when(campaign.getEndsAt()).thenReturn(NOW.plusSeconds(7200));
  }

  @Test
  void operationalActionsDeeplyValidateASelectionWithoutMakingListRowsQueryHeavy() {
    when(definitionAssessor.assessOffer(offer))
        .thenReturn(
            new CommercialOfferDefinitionAssessor.Assessment(
                null,
                null,
                true,
                List.of(
                    new CommercialOfferViews.DefinitionIssue(
                        CommercialOfferDefinitionIssueCode.PRICE_OWNER_MISMATCH,
                        "selection.planPriceId",
                        "The exact Plan price is unavailable."))));
    var validator =
        new CommercialOfferPublicationValidator(
            offers, definitionAssessor, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(validator.actionBlockers(offer, false))
        .contains(CommercialOfferBlocker.INVALID_SELECTION);
    assertThat(validator.hasInvalidSelection(offer)).isTrue();
  }

  @Test
  void publicCampaignCannotPublishAnExactDirectOnlyProduct() {
    when(definitionAssessor.assessOffer(offer))
        .thenReturn(
            new CommercialOfferDefinitionAssessor.Assessment(
                null,
                null,
                true,
                List.of(
                    new CommercialOfferViews.DefinitionIssue(
                        CommercialOfferDefinitionIssueCode.DIRECT_ONLY_REQUIRES_TARGETED_CAMPAIGN,
                        "selection.planId",
                        "DIRECT_ONLY products require a targeted Campaign."))));
    var validator =
        new CommercialOfferPublicationValidator(
            offers, definitionAssessor, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(validator.hasInvalidSelection(offer)).isTrue();
  }

  @Test
  void listSafePublicationBlockersUseOnlyPersistedState() {
    when(offer.getDiscovery()).thenReturn(CommercialOfferDiscovery.CODE_ONLY);
    when(offer.getCustomerCodeHash()).thenReturn(null);
    when(campaign.getStatus()).thenReturn(CommercialCampaignStatus.DRAFT);
    when(offer.getStartsAt()).thenReturn(NOW.minusSeconds(7200));
    when(offer.getEndsAt()).thenReturn(NOW);
    when(campaign.getStartsAt()).thenReturn(NOW.minusSeconds(3600));
    var validator =
        new CommercialOfferPublicationValidator(
            offers, definitionAssessor, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(validator.cheapActionBlockers(offer, false))
        .containsExactlyInAnyOrder(
            CommercialOfferBlocker.CODE_REQUIRED,
            CommercialOfferBlocker.CAMPAIGN_NOT_LIVE,
            CommercialOfferBlocker.WINDOW_OUTSIDE_CAMPAIGN,
            CommercialOfferBlocker.WINDOW_ENDED);
  }
}
