package com.hiveapp.platform.client.plan.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferAcceptance;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferDiscovery;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaign;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferCapacity;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferLineage;
import com.hiveapp.platform.client.plan.dto.CommercialOfferEffectSnapshot;
import com.hiveapp.platform.client.plan.dto.CommercialOfferViews;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOfferEvaluation;
import com.hiveapp.shared.exception.OfferRedemptionBlockedException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommercialOfferContractTest {
  @Test
  void codeDigestIsNormalizedKeyedAndNeverPlaintext() {
    var hasher =
        new CommercialOfferCodeHasher(
            new CommercialOfferCodeProperties("test-pepper-that-is-at-least-thirty-two-bytes"));
    String first = hasher.hash("  summer_25 ");
    assertThat(first).isEqualTo(hasher.hash("SUMMER_25")).hasSize(64);
    assertThat(first).doesNotContain("SUMMER");
  }

  @Test
  void offerQuotaBonusBecomesPartOfImmutableEntitlementSnapshot() {
    var source =
        new SubscriptionEntitlementSnapshot(
            "PRO",
            new BigDecimal("25.00"),
            "USD",
            BillingCycle.MONTHLY,
            List.of(
                new SubscriptionFeatureSnapshot(
                    "platform.staff", List.of(new QuotaLimitEntry("members", 10L)))),
            List.of());
    var evaluation =
        evaluation(
            List.of(new CommercialOfferEffectSnapshot.QuotaBonus("platform.staff", "members", 5)));
    var accepted = source.withOfferEvaluation(evaluation);
    // Catalogue price remains factual; billing consumes the separately persisted Offer result.
    assertThat(accepted.basePrice()).isEqualByComparingTo("25.00");
    assertThat(accepted.features().getFirst().quotaConfigs().getFirst().limit()).isEqualTo(15);
    assertThat(accepted.offerEvaluation().offerRevisionId())
        .isEqualTo(evaluation.offerRevisionId());
  }

  @Test
  void bonusCannotTargetAResourceOutsideTheAcceptedSelection() {
    var source =
        SubscriptionEntitlementSnapshot.empty(
            "PRO", new BigDecimal("25.00"), "USD", BillingCycle.MONTHLY);
    assertThatThrownBy(
            () ->
                source.withOfferEvaluation(
                    evaluation(
                        List.of(
                            new CommercialOfferEffectSnapshot.QuotaBonus(
                                "platform.staff", "members", 5)))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void capacityReservationIsReleasedOrConsumedExactlyOnce() {
    var capacity = CommercialOfferCapacity.create(UUID.randomUUID());
    capacity.reserve(1, 0, 1);
    assertThatThrownBy(() -> capacity.reserve(1, 0, 1))
        .isInstanceOf(OfferRedemptionBlockedException.class);
    capacity.apply();
    assertThat(capacity.getReservedCount()).isZero();
    assertThat(capacity.getAppliedCount()).isOne();
    assertThatThrownBy(capacity::apply).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void lineageTermsFreezeAtFirstPublicationEvenWhenNoCustomerCodeExists() {
    var lineage =
        CommercialOfferLineage.create(
            "NO_CODE",
            mock(CommercialCampaign.class),
            CommercialOfferDiscovery.CATALOG,
            CommercialOfferAcceptance.CLIENT_OR_OPERATOR,
            null,
            null,
            null,
            mock(AdminUser.class));

    lineage.editBeforeFirstPublication(
        CommercialOfferDiscovery.CATALOG,
        CommercialOfferAcceptance.CLIENT_OR_OPERATOR,
        null,
        10L,
        2L);
    lineage.markFirstPublished(java.time.Instant.parse("2030-01-01T00:00:00Z"));

    assertThatThrownBy(
            () ->
                lineage.editBeforeFirstPublication(
                    CommercialOfferDiscovery.CODE_ONLY,
                    CommercialOfferAcceptance.OPERATOR_ONLY,
                    null,
                    20L,
                    3L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("immutable");
  }

  @Test
  void offerCodePepperIsMandatoryAndLongEnough() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(new CommercialOfferCodeProperties(null))).isNotEmpty();
      assertThat(validator.validate(new CommercialOfferCodeProperties("too-short"))).isNotEmpty();
      assertThat(
              validator.validate(
                  new CommercialOfferCodeProperties("a-production-sized-offer-pepper-value")))
          .isEmpty();
    }
  }

  @Test
  void mutationAcknowledgementCannotLeakOfferDefinitionOrEvidence() throws Exception {
    String json =
        new ObjectMapper()
            .writeValueAsString(
                new CommercialOfferViews.Mutation(
                    UUID.randomUUID(), CommercialOfferStatus.PUBLISHED, 7));

    assertThat(json)
        .contains("offerId", "status", "version")
        .doesNotContain(
            "selection", "effects", "campaignId", "previewToken", "customerCode", "businessCode");
  }

  private SubscriptionOfferEvaluation evaluation(
      List<CommercialOfferEffectSnapshot.QuotaBonus> bonuses) {
    return new SubscriptionOfferEvaluation(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        2,
        new BigDecimal("25.00"),
        new BigDecimal("20.00"),
        new BigDecimal("18.00"),
        new BigDecimal("18.00"),
        "USD",
        "OFFER",
        "Offer is lower",
        bonuses);
  }
}
