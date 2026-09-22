package com.hiveapp.platform.client.plan.service;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.quota.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlanVersionContentRulesTest {
  private final PlanVersionContentRules rules = new PlanVersionContentRules();
  private final UUID family = UUID.randomUUID();
  private final Plan source = plan("FLEX", 1), target = plan("FLEX_V2", 2);

  private Plan plan(String code, int version) {
    Plan plan = new Plan();
    org.springframework.test.util.ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
    plan.setCode(code);
    plan.setName("Flex");
    plan.setLineageId(family);
    plan.setRevisionNumber(version);
    plan.setStatus(PlanStatus.ACTIVE);
    return plan;
  }

  private PlanFeature feature(String code, PlanFeatureMode mode, Long limit) {
    Feature definition = new Feature();
    definition.setCode(code);
    PlanFeature feature = new PlanFeature();
    feature.setPlan(target);
    feature.setFeature(definition);
    feature.setMode(mode);
    feature.setQuotaConfigs(
        code.equals("platform.staff")
            ? List.of(
                new QuotaLimitEntry(
                    "members",
                    limit == null ? QuotaLimitMode.UNLIMITED : QuotaLimitMode.FINITE,
                    limit))
            : List.of());
    return feature;
  }

  private List<PlanFeature> composition(Long limit) {
    return List.of(
        feature("platform.staff", PlanFeatureMode.INCLUDED, limit),
        feature("platform.roles", PlanFeatureMode.OPTIONAL_ADD_ON, null));
  }

  private SubscriptionEntitlementSnapshot held() {
    return new SubscriptionEntitlementSnapshot(
        4,
        "FLEX",
        "Flex",
        1,
        new BigDecimal("100.00"),
        "MAD",
        BillingCycle.MONTHLY,
        Instant.parse("2026-09-01T00:00:00Z"),
        Instant.parse("2026-10-01T00:00:00Z"),
        List.of(
            new SubscriptionFeatureSnapshot(
                "platform.staff",
                List.of(new QuotaLimitEntry("members", QuotaLimitMode.FINITE, 5L))),
            new SubscriptionFeatureSnapshot("platform.roles", List.of())),
        List.of(
            new SubscriptionAddOnSnapshot(
                "ROLES",
                "Roles",
                1,
                BigDecimal.TEN,
                "MAD",
                BillingCycle.MONTHLY,
                List.of("platform.roles"),
                UUID.randomUUID())),
        List.of(
            new SubscriptionQuotaPackageSnapshot(
                "PACK",
                "Pack",
                1,
                "platform.staff",
                "members",
                5,
                2,
                new BigDecimal("7.00"),
                "MAD",
                BillingCycle.MONTHLY,
                UUID.randomUUID())),
        UUID.randomUUID(),
        null,
        null);
  }

  @Test
  void copiesOnlyContentAndRetainsExactMoneyPurchasesAndPaidPeriod() {
    var before = held();
    var result = rules.build(source, target, composition(10L), before);
    assertThat(result.conflicts()).isEmpty();
    var after = result.snapshot();
    rules.requirePreservedTerms(before, after);
    assertThat(after.planCode()).isEqualTo("FLEX_V2");
    assertThat(after.financialPlanSource().planId()).isEqualTo(source.getId());
    assertThat(after.addOns()).isEqualTo(before.addOns());
    assertThat(after.quotaPackages()).isEqualTo(before.quotaPackages());
    assertThat(
            after.features().stream()
                .filter(f -> f.featureCode().equals("platform.staff"))
                .findFirst()
                .orElseThrow()
                .quotaConfigs()
                .getFirst()
                .limit())
        .isEqualTo(10L);
  }

  @Test
  void subsequentContentVersionsKeepTheOriginalFinancialSource() {
    var second = rules.build(source, target, composition(10L), held()).snapshot();
    var third = rules.build(target, plan("FLEX_V3", 3), composition(15L), second).snapshot();
    assertThat(third.financialPlanSource()).isEqualTo(second.financialPlanSource());
    assertThat(third.planDefinitionVersion()).isEqualTo(3);
  }

  @Test
  void crossFamilyAndSameVersionAreRejected() {
    assertThat(rules.build(source, source, composition(10L), held()).conflicts())
        .extracting(SubscriptionChangeConflict::code)
        .contains("DIFFERENT_OR_SAME_VERSION");
    target.setLineageId(UUID.randomUUID());
    assertThat(rules.build(source, target, composition(10L), held()).snapshot()).isNull();
  }

  @Test
  void purchasedFeatureCannotBecomeIncludedOrBlocked() {
    for (var mode : List.of(PlanFeatureMode.INCLUDED, PlanFeatureMode.BLOCKED_FOR_PLAN)) {
      var result =
          rules.build(
              source,
              target,
              List.of(
                  feature("platform.staff", PlanFeatureMode.INCLUDED, 10L),
                  feature("platform.roles", mode, null)),
              held());
      assertThat(result.snapshot()).isNull();
      assertThat(result.conflicts()).isNotEmpty();
    }
  }

  @Test
  void purchasedPackCannotLoseOwnerOrBecomeUnlimited() {
    assertThat(rules.build(source, target, composition(null), held()).conflicts())
        .extracting(SubscriptionChangeConflict::code)
        .contains("PURCHASED_CAPACITY_OWNER_LOST");
    assertThat(
            rules
                .build(
                    source,
                    target,
                    List.of(feature("platform.roles", PlanFeatureMode.OPTIONAL_ADD_ON, null)),
                    held())
                .conflicts())
        .extracting(SubscriptionChangeConflict::code)
        .contains("PURCHASED_CAPACITY_OWNER_LOST");
  }

  @Test
  void acceptedOfferBonusesArePreservedExactlyOnceAcrossVersions() {
    var evaluation =
        new SubscriptionOfferEvaluation(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.ONE,
            "MAD",
            "OFFER",
            "accepted",
            List.of(new CommercialOfferEffectSnapshot.QuotaBonus("platform.staff", "members", 3)));
    var before = held().withOfferEvaluation(evaluation);
    var second = rules.build(source, target, composition(10L), before).snapshot();
    var third = rules.build(target, plan("FLEX_V3", 3), composition(20L), second).snapshot();
    assertThat(
            third.features().stream()
                .filter(f -> f.featureCode().equals("platform.staff"))
                .findFirst()
                .orElseThrow()
                .quotaConfigs()
                .getFirst()
                .limit())
        .isEqualTo(23L);
    assertThat(third.offerEvaluation()).isEqualTo(evaluation);
    rules.requirePreservedTerms(before, third);
  }

  @Test
  void changingPeriodOrPriceIsNotAContentOnlyOperation() {
    var before = held();
    var after = rules.build(source, target, composition(10L), before).snapshot();
    assertThatThrownBy(
            () -> rules.requirePreservedTerms(before, after.withEffectivePeriod(null, null)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void schemaV5RoundTripsAndCopyMethodsKeepFinancialProvenance() throws Exception {
    var snapshot = rules.build(source, target, composition(10L), held()).snapshot();
    var mapper = new ObjectMapper().findAndRegisterModules();
    assertThat(
            mapper.readValue(
                mapper.writeValueAsString(snapshot), SubscriptionEntitlementSnapshot.class))
        .isEqualTo(snapshot);
    assertThat(snapshot.withEffectivePeriod(null, null).financialPlanSource())
        .isEqualTo(snapshot.financialPlanSource());
    assertThat(snapshot.withCommercialPolicyEvaluation(null).financialPlanSource())
        .isEqualTo(snapshot.financialPlanSource());
    assertThat(snapshot.withOfferProvenance(null).financialPlanSource())
        .isEqualTo(snapshot.financialPlanSource());
  }

  @Test
  void legacySchemaFourRemainsReadableWithoutInventedProvenance() throws Exception {
    var mapper = new ObjectMapper().findAndRegisterModules();
    var snapshot =
        mapper.readValue(mapper.writeValueAsString(held()), SubscriptionEntitlementSnapshot.class);
    assertThat(snapshot.schemaVersion()).isEqualTo(4);
    assertThat(snapshot.financialPlanSource()).isNull();
  }
}
