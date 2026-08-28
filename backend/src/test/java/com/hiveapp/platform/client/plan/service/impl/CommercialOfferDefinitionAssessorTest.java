package com.hiveapp.platform.client.plan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.CommercialOfferEffectSnapshot;
import com.hiveapp.platform.client.plan.dto.CommercialOfferRequests;
import com.hiveapp.platform.client.plan.dto.CommercialOfferSelection;
import com.hiveapp.platform.client.plan.dto.CommercialOfferViews;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.CommercialOfferCodeHasher;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommercialOfferDefinitionAssessorTest {
  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

  @Mock private CommercialCampaignRepository campaigns;
  @Mock private CommercialOfferCodeReservationRepository codes;
  @Mock private PlanFeatureRepository planFeatures;
  @Mock private AddOnRepository addOns;
  @Mock private CommercialCatalogResolver catalogResolver;
  @Mock private CommercialOfferCodeHasher codeHasher;
  @Mock private CommercialOfferClientProjectionMapper clientProjection;
  @Mock private CommercialOfferAdminProjectionMapper adminProjection;
  @Mock private Clock clock;
  @InjectMocks private CommercialOfferDefinitionAssessor assessor;

  @Test
  void missingExactPlanPriceIsAStableTypedIssue() {
    CommercialOfferSelection selection = selection(UUID.randomUUID(), UUID.randomUUID());
    when(campaigns.findDetailById(any())).thenReturn(Optional.empty());
    when(clientProjection.exactPrices(selection)).thenReturn(Map.of());

    var assessment = assessor.assessCreate(request(selection, null, CommercialOfferDiscovery.CATALOG));

    assertThat(assessment.issues())
        .extracting(CommercialOfferViews.DefinitionIssue::code)
        .contains(CommercialOfferDefinitionIssueCode.PRICE_OWNER_MISMATCH);
    assertThat(assessment.resolvedSelection()).isNull();
  }

  @Test
  void mismatchedExactPlanPriceOwnerIsAStableTypedIssue() {
    UUID selectedPlanId = UUID.randomUUID();
    UUID priceId = UUID.randomUUID();
    CommercialOfferSelection selection = selection(selectedPlanId, priceId);
    ProductPrice price = mock(ProductPrice.class);
    Plan otherPlan = mock(Plan.class);
    when(campaigns.findDetailById(any())).thenReturn(Optional.empty());
    when(clientProjection.exactPrices(selection)).thenReturn(Map.of(priceId, price));
    when(price.getOwnerType()).thenReturn(ProductPriceOwnerType.PLAN);
    when(price.getPlan()).thenReturn(otherPlan);
    when(otherPlan.getId()).thenReturn(UUID.randomUUID());

    var assessment = assessor.assessCreate(request(selection, null, CommercialOfferDiscovery.CATALOG));

    assertThat(assessment.issues())
        .extracting(CommercialOfferViews.DefinitionIssue::code)
        .contains(CommercialOfferDefinitionIssueCode.PRICE_OWNER_MISMATCH);
  }

  @Test
  void codeOnlyMissingOrInvalidCodeReturnsIssuesWithoutEchoingSecret() throws Exception {
    UUID campaignId = UUID.randomUUID();
    when(campaigns.findDetailById(campaignId)).thenReturn(Optional.empty());
    when(codeHasher.hash(org.mockito.ArgumentMatchers.nullable(String.class)))
        .thenAnswer(
            invocation -> {
              String raw = invocation.getArgument(0);
              if (raw == null) return null;
              throw new IllegalArgumentException("invalid code " + raw);
            });

    var missing =
        assessor.assessCreate(
            request(campaignId, null, null, CommercialOfferDiscovery.CODE_ONLY));
    var invalid =
        assessor.assessCreate(
            request(campaignId, null, "RAW-SECRET-CODE", CommercialOfferDiscovery.CODE_ONLY));
    var preview =
        new CommercialOfferViews.DefinitionPreview(
            null,
            null,
            invalid.valid(),
            invalid.lineageTermsEditable(),
            invalid.campaign(),
            invalid.resolvedSelection(),
            invalid.issues());
    String json = new ObjectMapper().writeValueAsString(preview);

    assertThat(missing.issues())
        .extracting(CommercialOfferViews.DefinitionIssue::code)
        .contains(CommercialOfferDefinitionIssueCode.CUSTOMER_CODE_REQUIRED);
    assertThat(invalid.issues())
        .extracting(CommercialOfferViews.DefinitionIssue::code)
        .contains(CommercialOfferDefinitionIssueCode.CUSTOMER_CODE_INVALID)
        .doesNotContain(CommercialOfferDefinitionIssueCode.CUSTOMER_CODE_REQUIRED);
    assertThat(json).doesNotContain("RAW-SECRET-CODE");
  }

  @Test
  void combinedQuotaBonusOverflowIsAStableTypedIssue() {
    UUID planId = UUID.randomUUID();
    UUID priceId = UUID.randomUUID();
    CommercialOfferSelection selection = selection(planId, priceId);
    ProductPrice price = mock(ProductPrice.class);
    Plan plan = mock(Plan.class);
    PlanFeature feature = mock(PlanFeature.class);
    Feature definition = mock(Feature.class);
    when(campaigns.findDetailById(any())).thenReturn(Optional.empty());
    when(clientProjection.exactPrices(selection)).thenReturn(Map.of(priceId, price));
    when(price.getOwnerType()).thenReturn(ProductPriceOwnerType.PLAN);
    when(price.getPlan()).thenReturn(plan);
    when(plan.getId()).thenReturn(planId);
    when(plan.getSalesVisibility()).thenReturn(ProductSalesVisibility.PUBLIC);
    when(price.getCurrencyCode()).thenReturn("USD");
    when(price.getBillingCycle()).thenReturn(BillingCycle.MONTHLY);
    when(price.getStatus()).thenReturn(ProductPriceStatus.ACTIVE);
    when(price.getEffectiveFrom()).thenReturn(NOW.minusSeconds(60));
    when(feature.getMode()).thenReturn(PlanFeatureMode.INCLUDED);
    when(feature.getFeature()).thenReturn(definition);
    when(definition.getCode()).thenReturn("workspace");
    when(feature.getQuotaConfigs())
        .thenReturn(List.of(new QuotaLimitEntry("members", Long.MAX_VALUE - 10)));
    when(planFeatures.findAllByPlanId(planId)).thenReturn(List.of(feature));
    when(catalogResolver.resolveExactSelectionCandidates(any(), any())).thenReturn(Map.of());
    CommercialOfferEffectSnapshot effects =
        new CommercialOfferEffectSnapshot(
            CommercialOfferDiscountType.NONE,
            null,
            null,
            null,
            List.of(
                new CommercialOfferEffectSnapshot.QuotaBonus("workspace", "members", 6),
                new CommercialOfferEffectSnapshot.QuotaBonus("workspace", "members", 6)));

    var assessment =
        assessor.assessCreate(
            new CommercialOfferRequests.Create(
                "UI-ready Offer",
                "Definition preview",
                UUID.randomUUID(),
                NOW.plusSeconds(60),
                NOW.plusSeconds(3600),
                CommercialOfferDiscovery.CATALOG,
                CommercialOfferAcceptance.CLIENT_OR_OPERATOR,
                null,
                null,
                null,
                selection,
                effects));

    assertThat(assessment.issues())
        .extracting(CommercialOfferViews.DefinitionIssue::code)
        .contains(CommercialOfferDefinitionIssueCode.QUOTA_BONUS_OVERFLOW);
  }

  @Test
  void quotaBonusPreflightIncludesSelectedPackageCapacity() {
    UUID planId = UUID.randomUUID();
    UUID packageId = UUID.randomUUID();
    UUID packagePriceId = UUID.randomUUID();
    PlanFeature feature = mock(PlanFeature.class);
    Feature definition = mock(Feature.class);
    ProductPrice packagePrice = mock(ProductPrice.class);
    QuotaPackage quotaPackage = mock(QuotaPackage.class);
    when(feature.getMode()).thenReturn(PlanFeatureMode.INCLUDED);
    when(feature.getFeature()).thenReturn(definition);
    when(feature.getQuotaConfigs()).thenReturn(List.of(new QuotaLimitEntry("members", 10L)));
    when(definition.getCode()).thenReturn("workspace");
    when(planFeatures.findAllByPlanId(planId)).thenReturn(List.of(feature));
    when(packagePrice.getOwnerType()).thenReturn(ProductPriceOwnerType.QUOTA_PACKAGE);
    when(packagePrice.getQuotaPackage()).thenReturn(quotaPackage);
    when(quotaPackage.getId()).thenReturn(packageId);
    when(quotaPackage.getFeature()).thenReturn(definition);
    when(quotaPackage.getResource()).thenReturn("members");
    when(quotaPackage.getCapacityPerUnit()).thenReturn(Long.MAX_VALUE);
    var selected =
        new CommercialOfferSelection.PackageSelection(
            packageId, packagePriceId, 1, CommercialOfferSelection.PricingMode.PAID);
    var effects =
        new CommercialOfferEffectSnapshot(
            CommercialOfferDiscountType.NONE,
            null,
            null,
            null,
            List.of(new CommercialOfferEffectSnapshot.QuotaBonus("workspace", "members", 1)));
    List<CommercialOfferViews.DefinitionIssue> issues = new ArrayList<>();

    ReflectionTestUtils.invokeMethod(
        assessor,
        "validateQuotaBonuses",
        planId,
        Set.of(),
        List.of(selected),
        Map.of(packagePriceId, packagePrice),
        effects,
        issues);

    assertThat(issues)
        .extracting(CommercialOfferViews.DefinitionIssue::code)
        .containsExactly(CommercialOfferDefinitionIssueCode.QUOTA_BONUS_OVERFLOW);
  }

  @Test
  void selectedPackageCapacityOverflowIsTypedWithoutAnOfferBonus() {
    UUID planId = UUID.randomUUID();
    UUID packageId = UUID.randomUUID();
    UUID packagePriceId = UUID.randomUUID();
    PlanFeature feature = mock(PlanFeature.class);
    Feature definition = mock(Feature.class);
    ProductPrice packagePrice = mock(ProductPrice.class);
    QuotaPackage quotaPackage = mock(QuotaPackage.class);
    when(feature.getMode()).thenReturn(PlanFeatureMode.INCLUDED);
    when(feature.getFeature()).thenReturn(definition);
    when(feature.getQuotaConfigs()).thenReturn(List.of(new QuotaLimitEntry("members", 10L)));
    when(definition.getCode()).thenReturn("workspace");
    when(planFeatures.findAllByPlanId(planId)).thenReturn(List.of(feature));
    when(packagePrice.getOwnerType()).thenReturn(ProductPriceOwnerType.QUOTA_PACKAGE);
    when(packagePrice.getQuotaPackage()).thenReturn(quotaPackage);
    when(quotaPackage.getId()).thenReturn(packageId);
    when(quotaPackage.getFeature()).thenReturn(definition);
    when(quotaPackage.getResource()).thenReturn("members");
    when(quotaPackage.getCapacityPerUnit()).thenReturn(Long.MAX_VALUE);
    var selected =
        new CommercialOfferSelection.PackageSelection(
            packageId, packagePriceId, 1, CommercialOfferSelection.PricingMode.PAID);
    var effects =
        new CommercialOfferEffectSnapshot(
            CommercialOfferDiscountType.NONE, null, null, null, List.of());
    List<CommercialOfferViews.DefinitionIssue> issues = new ArrayList<>();

    ReflectionTestUtils.invokeMethod(
        assessor,
        "validateQuotaBonuses",
        planId,
        Set.of(),
        List.of(selected),
        Map.of(packagePriceId, packagePrice),
        effects,
        issues);

    assertThat(issues)
        .extracting(CommercialOfferViews.DefinitionIssue::code)
        .containsExactly(CommercialOfferDefinitionIssueCode.QUOTA_BONUS_OVERFLOW);
  }

  private CommercialOfferSelection selection(UUID planId, UUID priceId) {
    return new CommercialOfferSelection(
        planId, priceId, List.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE);
  }

  private CommercialOfferRequests.Create request(
      CommercialOfferSelection selection,
      String customerCode,
      CommercialOfferDiscovery discovery) {
    return request(UUID.randomUUID(), selection, customerCode, discovery);
  }

  private CommercialOfferRequests.Create request(
      UUID campaignId,
      CommercialOfferSelection selection,
      String customerCode,
      CommercialOfferDiscovery discovery) {
    return new CommercialOfferRequests.Create(
        "UI-ready Offer",
        "Definition preview",
        campaignId,
        NOW.plusSeconds(60),
        NOW.plusSeconds(3600),
        discovery,
        CommercialOfferAcceptance.CLIENT_OR_OPERATOR,
        customerCode,
        null,
        null,
        selection,
        null);
  }
}
