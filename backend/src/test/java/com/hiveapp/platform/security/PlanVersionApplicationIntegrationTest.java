package com.hiveapp.platform.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.*;
import com.hiveapp.platform.client.plan.service.*;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.quota.*;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PlanVersionApplicationIntegrationTest extends PlatformShellIntegrationTestSupport {
  @Autowired PlanRepository plans;
  @Autowired PlanFeatureRepository features;
  @Autowired SubscriptionRepository subscriptions;
  @Autowired SubscriptionPeriodRepository periods;
  @Autowired ProductPriceRepository prices;
  @Autowired AccountRepository accounts;
  @Autowired UserRepository users;
  @Autowired AdminUserRepository admins;
  @Autowired FeatureRepository registry;
  @Autowired SubscriptionSnapshotFactory snapshots;
  @Autowired SubscriptionContentEvidenceRepository evidence;
  @Autowired SubscriptionChangeOperationRepository operations;
  @Autowired SubscriptionCheckoutRepository checkouts;
  @Autowired BillingInvoiceRepository invoices;
  @Autowired PlanVersionApplicationOperations applications;
  @Autowired PlanVersionApplicationAssessor assessor;
  @Autowired SubscriptionBillingRenewalService billing;
  @Autowired SubscriptionCheckoutService checkoutService;
  @Autowired SubscriptionRenewalChangeProcessor renewals;
  @Autowired SubscriptionRepricingService repricing;
  @Autowired EntityManager em;
  @Autowired MutableClock clock;

  @org.springframework.boot.test.context.TestConfiguration
  static class TimeConfiguration {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    MutableClock applicationTestClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    private Instant now = Instant.now();

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }

    void set(Instant now) {
      this.now = now;
    }
  }

  @org.junit.jupiter.api.BeforeEach
  void resetClock() {
    clock.set(Instant.now());
  }

  private final Request now =
      new Request(Timing.NOW, null, "Apply reviewed content and retain the price");

  @Test
  void immediateApplicationKeepsMoneyPaidPeriodIdentityAndBillingHistoryAndIsIdempotent()
      throws Exception {
    var f = fixture();
    var before = f.subscription.getEntitlementSnapshot();
    UUID terms = f.subscription.termsIdentity();
    UUID subscriptionId = f.subscription.getId();
    var period =
        periods
            .findBySubscriptionIdAndStatus(subscriptionId, SubscriptionPeriodStatus.OPEN)
            .orElseThrow();
    UUID periodId = period.getId();
    var periodSnapshot = period.getEntitlementSnapshot();
    long invoiceCount = invoices.count(),
        checkoutCount = checkouts.count(),
        periodCount = periods.count();
    var preview = preview(f, now);
    assertThat(preview.conflicts()).isEmpty();
    UUID commandId = UUID.randomUUID();
    var result = apply(f, new ApplyNow(commandId, now, preview.previewToken()));
    em.flush();
    em.clear();
    var current = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
    assertThat(current.getId()).isEqualTo(subscriptionId);
    assertThat(current.termsIdentity()).isEqualTo(terms);
    assertThat(current.getCurrentPrice()).isEqualByComparingTo("100");
    assertThat(current.getCurrentPriceCurrencyCode()).isEqualTo("MAD");
    assertThat(current.getCurrentPeriodStart()).isEqualTo(before.effectiveFrom());
    assertThat(current.getCurrentPeriodEnd()).isEqualTo(before.effectiveUntil());
    assertThat(current.getEntitlementSnapshot().planCode()).isEqualTo(f.target.getCode());
    assertThat(current.getEntitlementSnapshot().planPriceEntryId())
        .isEqualTo(before.planPriceEntryId());
    assertThat(current.getEntitlementSnapshot().financialPlanSource().planId())
        .isEqualTo(f.source.getId());
    assertThat(periods.findById(periodId).orElseThrow().getEntitlementSnapshot())
        .isEqualTo(periodSnapshot);
    assertThat(invoices.count()).isEqualTo(invoiceCount);
    assertThat(checkouts.count()).isEqualTo(checkoutCount);
    assertThat(periods.count()).isEqualTo(periodCount);
    assertThat(evidence.findById(result.evidenceId()).orElseThrow().getBillingPeriodId())
        .isEqualTo(periodId);
    assertThat(
            operations.findById(result.operationId()).orElseThrow().getBeforeSnapshot().planCode())
        .isEqualTo(f.source.getCode());
    assertThat(apply(f, new ApplyNow(commandId, now, preview.previewToken()))).isEqualTo(result);
  }

  @Test
  void changedMoneyAfterPreviewIsRejectedWithoutChangingContent() throws Exception {
    var f = fixture();
    var preview = preview(f, now);
    f.subscription.setCurrentMoney(Money.of(new BigDecimal("80"), "MAD"));
    subscriptions.saveAndFlush(f.subscription);
    applyRequest(f, new ApplyNow(UUID.randomUUID(), now, preview.previewToken()))
        .andExpect(status().isConflict());
    assertThat(f.subscription.getPlan().getId()).isEqualTo(f.source.getId());
  }

  @Test
  void scheduledRepricingMustBeResolvedBeforeContentApplication() throws Exception {
    var f = fixture();
    var sourcePrice = f.subscription.getEntitlementSnapshot().planPriceEntryId();
    var targetPrice =
        ProductPrice.draft(
            f.source,
            Money.of(new BigDecimal("120"), "MAD"),
            BillingCycle.MONTHLY,
            f.subscription.getCurrentPeriodEnd(),
            null);
    targetPrice.activate();
    prices.saveAndFlush(targetPrice);
    var request =
        new com.hiveapp.platform.client.plan.dto.RepricingModels.Request(
            sourcePrice,
            targetPrice.getId(),
            com.hiveapp.platform.client.plan.dto.RepricingModels.Audience.SELECTED,
            List.of(f.accountId),
            List.of(),
            null,
            null,
            null,
            null,
            false,
            "Reviewed next-renewal price change");
    var review = repricing.preview(actor(), request);
    repricing.confirm(
        review.summary().id(),
        actor(),
        new com.hiveapp.platform.client.plan.dto.RepricingModels.Confirm(review.previewToken()));
    assertThat(preview(f, now).conflicts())
        .extracting(item -> item.code())
        .contains("REPRICING_PENDING");
  }

  @Test
  void followingPaidRenewalKeepsTheContentEvidenceAndOriginalFinancialSource() throws Exception {
    var f = fixture();
    var preview = preview(f, now);
    var applied = apply(f, new ApplyNow(UUID.randomUUID(), now, preview.previewToken()));
    var current = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
    UUID terms = current.termsIdentity();
    Instant renewalAt = current.getCurrentPeriodEnd();
    var payment = billing.ensureCharge(current);
    assertThat(payment.getCheckout().getAmount()).isEqualByComparingTo("100");
    assertThat(payment.getTargetSnapshot().financialPlanSource().planId())
        .isEqualTo(f.source.getId());
    var invoice = invoices.findByCheckoutId(payment.getCheckout().getId()).orElseThrow();
    var planLine =
        invoice.getLines().stream()
            .filter(
                line ->
                    line.getType()
                        == com.hiveapp.platform.client.plan.domain.constant.BillingLineType.PLAN)
            .findFirst()
            .orElseThrow();
    assertThat(planLine.getSourceCode()).isEqualTo(f.source.getCode());
    assertThat(planLine.getSourceVersion()).isEqualTo(f.source.getRevisionNumber());
    assertThat(planLine.getPriceEntryId())
        .isEqualTo(current.getEntitlementSnapshot().planPriceEntryId());
    checkoutService.confirmManual(
        payment.getCheckout().getId(), actor(), "renew-" + UUID.randomUUID(), "Received payment");
    renewals.processDue(renewalAt);
    var renewed = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
    assertThat(renewed.getId()).isNotEqualTo(current.getId());
    assertThat(renewed.getPlan().getId()).isEqualTo(f.target.getId());
    assertThat(renewed.termsIdentity()).isEqualTo(terms);
    assertThat(renewed.getContentEvidenceId()).isEqualTo(applied.evidenceId());
    assertThat(renewed.getCurrentPrice()).isEqualByComparingTo("100");
    assertThat(renewed.getEntitlementSnapshot().financialPlanSource().planId())
        .isEqualTo(f.source.getId());
  }

  @Test
  void laterExtensionFlowCanRetainAV1TariffOnV2WithoutMislabelingItsOwner() throws Exception {
    var f = fixture();
    var preview = preview(f, now);
    apply(f, new ApplyNow(UUID.randomUUID(), now, preview.previewToken()));
    em.flush();
    em.clear();
    // Exercise both ordinary preview and locked finalization, not only the snapshot copier.
    var response =
        applyReviewedAdminSubscriptionChange(
            f.admin,
            f.accountId,
            new com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest(
                f.target.getCode(), Set.of(), List.of()));
    assertThat(response).isNotNull();
    var current = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
    assertThat(current.getEntitlementSnapshot().financialPlanSource().planId())
        .isEqualTo(f.source.getId());
    assertThat(current.getCurrentPrice()).isEqualByComparingTo("100");
  }

  @Test
  void reducedQuotaBelowCurrentUsageIsAVisibleConflict() throws Exception {
    var f = fixture();
    var staff =
        features.findByPlanIdAndFeature_Code(f.target.getId(), "platform.staff").orElseThrow();
    staff.setQuotaConfigs(List.of(new QuotaLimitEntry("members", QuotaLimitMode.FINITE, 0L)));
    features.saveAndFlush(staff);
    assertThat(preview(f, now).conflicts())
        .extracting(item -> item.code())
        .contains("QUOTA_BELOW_USAGE");
    assertThat(subscriptions.findCurrentByAccountId(f.accountId).orElseThrow().getPlan().getId())
        .isEqualTo(f.source.getId());
  }

  @Test
  void removedFeatureActuallyRevokesClientAccessAndReversalIsANewReviewedOperation()
      throws Exception {
    var f = fixture();
    mockMvc
        .perform(get("/api/v1/companies").header("Authorization", bearer(f.client)))
        .andExpect(status().isOk());
    var companies =
        features
            .findByPlanIdAndFeature_Code(
                f.target.getId(), com.hiveapp.platform.registry.definition.CompanyFeature.CODE)
            .orElseThrow();
    features.delete(companies);
    features.flush();
    var preview = preview(f, now);
    assertThat(preview.conflicts()).isEmpty();
    assertThat(preview.removedFeatures())
        .contains(com.hiveapp.platform.registry.definition.CompanyFeature.CODE);
    var first = apply(f, new ApplyNow(UUID.randomUUID(), now, preview.previewToken()));
    mockMvc
        .perform(get("/api/v1/companies").header("Authorization", bearer(f.client)))
        .andExpect(status().isForbidden());
    var reverse = new Fixture(f.accountId, f.subscription, f.target, f.source, f.admin, f.client);
    var reviewedReverse = preview(reverse, now);
    var second =
        apply(reverse, new ApplyNow(UUID.randomUUID(), now, reviewedReverse.previewToken()));
    assertThat(second.operationId()).isNotEqualTo(first.operationId());
    assertThat(evidence.findById(second.evidenceId()).orElseThrow().getPreviousEvidenceId())
        .isEqualTo(first.evidenceId());
    mockMvc
        .perform(
            get("/api/v1/subscriptions/content-notices").header("Authorization", bearer(f.client)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("content[0].addedFeatures")
                .value(
                    org.hamcrest.Matchers.hasItem(
                        com.hiveapp.platform.registry.definition.CompanyFeature.CODE)));
    mockMvc
        .perform(get("/api/v1/companies").header("Authorization", bearer(f.client)))
        .andExpect(status().isOk());
  }

  @Test
  void crossFamilyDraftAndSameVersionCannotBeApplied() throws Exception {
    var f = fixture();
    // Identity is immutable in storage, so test the independent family through a new record.
    Plan unrelated = plan(null);
    copyFeatures(f.source, unrelated);
    var foreign = new Fixture(f.accountId, f.subscription, f.source, unrelated, f.admin, f.client);
    assertThat(preview(foreign, now).conflicts())
        .extracting(item -> item.code())
        .contains("DIFFERENT_OR_SAME_VERSION");
    f.target.setStatus(PlanStatus.DRAFT);
    plans.saveAndFlush(f.target);
    assertThat(preview(f, now).conflicts())
        .extracting(item -> item.code())
        .contains("TARGET_NOT_ACTIVE");
  }

  @Test
  void clientCannotPreviewOrApplyAnAdministratorVersionOperation() throws Exception {
    var f = fixture();
    mockMvc
        .perform(
            post(path(f, "version-preview"))
                .header("Authorization", bearer(f.client))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(now)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post(path(f, "version-preview"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(now)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void renewalInstructionWaitsForSuccessfulRenewalAndDoesNotBlockBillingSlot() throws Exception {
    var f = fixture();
    var request = new Request(Timing.AT_RENEWAL, null, "On successful renewal");
    var reviewed = assessor.assess(f.subscription, f.target, request, clock.instant()).reviewed();
    var result = applications.executeReviewed(UUID.randomUUID(), actor(), reviewed);
    assertThat(result.outcome()).isEqualTo(Outcome.WAITING);
    assertThat(
            operations.existsByAccountIdAndStatusIn(
                f.accountId,
                List.of(
                    SubscriptionChangeStatus.PENDING,
                    SubscriptionChangeStatus.AWAITING_CONFIRMATION)))
        .isFalse();
    assertThat(f.subscription.getPlan().getId()).isEqualTo(f.source.getId());
  }

  @Test
  void fixedDateInstructionWaitsAndAChangedContractNeedsNewReview() throws Exception {
    var f = fixture();
    var request =
        new Request(Timing.AT_DATE, clock.instant().plusSeconds(3600), "Scheduled rollout");
    var reviewed = assessor.assess(f.subscription, f.target, request, clock.instant()).reviewed();
    assertThat(applications.executeReviewed(UUID.randomUUID(), actor(), reviewed).outcome())
        .isEqualTo(Outcome.WAITING);
    f.subscription.setCurrentMoney(Money.of(new BigDecimal("90"), "MAD"));
    subscriptions.saveAndFlush(f.subscription);
    assertThat(applications.executeReviewed(UUID.randomUUID(), actor(), reviewed).conflicts())
        .extracting(item -> item.code())
        .contains("SUBSCRIPTION_CHANGED");
  }

  @Test
  void renewalRolloutWaitsForPaymentThenAppliesWithoutASecondCharge() throws Exception {
    var f = fixture();
    var request = new Request(Timing.AT_RENEWAL, null, "Apply after paid renewal");
    var reviewed = assessor.assess(f.subscription, f.target, request, clock.instant()).reviewed();
    UUID command = UUID.randomUUID();
    var payment = billing.ensureCharge(f.subscription);
    long invoiceCount = invoices.count(), checkoutCount = checkouts.count();
    clock.set(f.subscription.getCurrentPeriodEnd().plusSeconds(1));
    assertThat(applications.executeReviewed(command, actor(), reviewed).outcome())
        .isEqualTo(Outcome.WAITING);
    assertThat(f.subscription.getPlan().getId()).isEqualTo(f.source.getId());
    checkoutService.confirmManual(
        payment.getCheckout().getId(), actor(), "paid-" + command, "Bank payment received");
    var renewed = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
    UUID periodId =
        periods
            .findBySubscriptionIdAndStatus(renewed.getId(), SubscriptionPeriodStatus.OPEN)
            .orElseThrow()
            .getId();
    Instant start = renewed.getCurrentPeriodStart(), end = renewed.getCurrentPeriodEnd();
    var result = applications.executeReviewed(command, actor(), reviewed);
    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(renewed.getPlan().getId()).isEqualTo(f.target.getId());
    assertThat(renewed.getCurrentPeriodStart()).isEqualTo(start);
    assertThat(renewed.getCurrentPeriodEnd()).isEqualTo(end);
    assertThat(evidence.findById(result.evidenceId()).orElseThrow().getBillingPeriodId())
        .isEqualTo(periodId);
    assertThat(invoices.count()).isEqualTo(invoiceCount);
    assertThat(checkouts.count()).isEqualTo(checkoutCount);
  }

  @Test
  void fixedDateSurvivesOrdinaryRenewalAndRecordsPlannedVersusActualTime() throws Exception {
    var f = fixture();
    Instant due = f.subscription.getCurrentPeriodEnd().plusSeconds(3600);
    var request = new Request(Timing.AT_DATE, due, "Same time across subscribers");
    var reviewed = assessor.assess(f.subscription, f.target, request, clock.instant()).reviewed();
    var payment = billing.ensureCharge(f.subscription);
    clock.set(f.subscription.getCurrentPeriodEnd().plusSeconds(1));
    checkoutService.confirmManual(
        payment.getCheckout().getId(),
        actor(),
        "fixed-" + UUID.randomUUID(),
        "Bank payment received");
    UUID command = UUID.randomUUID();
    assertThat(applications.executeReviewed(command, actor(), reviewed).outcome())
        .isEqualTo(Outcome.WAITING);
    clock.set(due.plusSeconds(10));
    var result = applications.executeReviewed(command, actor(), reviewed);
    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    var proof = evidence.findById(result.evidenceId()).orElseThrow();
    assertThat(proof.getPlannedAt()).isEqualTo(due);
    assertThat(proof.getEffectiveAt()).isEqualTo(clock.instant());
    assertThat(subscriptions.findCurrentByAccountId(f.accountId).orElseThrow().getCurrentPrice())
        .isEqualByComparingTo("100");
  }

  @Test
  void revokedAdministratorAndRuntimeVetoAreCheckedAgainByWorker() throws Exception {
    var f = fixture();
    var reviewed = assessor.assess(f.subscription, f.target, now, clock.instant()).reviewed();
    UUID actor = actor();
    var admin = admins.findByUserId(actor).orElseThrow();
    admin.setActive(false);
    admins.saveAndFlush(admin);
    assertThatThrownBy(() -> applications.executeReviewed(UUID.randomUUID(), actor, reviewed))
        .isInstanceOf(ForbiddenException.class);
    admin.setActive(true);
    admins.saveAndFlush(admin);
    var feature = registry.findByCode("platform.plans").orElseThrow();
    feature.setRuntimeEnabled(false);
    registry.saveAndFlush(feature);
    assertThatThrownBy(() -> applications.executeReviewed(UUID.randomUUID(), actor, reviewed))
        .isInstanceOf(ForbiddenException.class);
  }

  private UUID actor() {
    return users.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
  }

  private Preview preview(Fixture f, Request request) throws Exception {
    return objectMapper.readValue(
        mockMvc
            .perform(
                post(path(f, "version-preview"))
                    .header("Authorization", bearer(f.admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        Preview.class);
  }

  private org.springframework.test.web.servlet.ResultActions applyRequest(
      Fixture f, ApplyNow request) throws Exception {
    return mockMvc.perform(
        post(path(f, "apply-version"))
            .header("Authorization", bearer(f.admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
  }

  private Result apply(Fixture f, ApplyNow request) throws Exception {
    return objectMapper.readValue(
        applyRequest(f, request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        Result.class);
  }

  private String path(Fixture f, String operation) {
    return "/api/admin/plans/" + f.target.getId() + "/subscribers/" + f.accountId + "/" + operation;
  }

  private Fixture fixture() throws Exception {
    String admin = loginAdminAndGetToken();
    String email = "content-" + UUID.randomUUID() + "@example.com";
    var registered =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RegisterRequest(email, CLIENT_PASSWORD, "Content", "Owner", null))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String client = objectMapper.readTree(registered).path("accessToken").asText();
    UUID account =
        accounts
            .findByOwner_Id(users.findByEmail(email).orElseThrow().getId())
            .orElseThrow()
            .getId();
    var subscription = subscriptions.findCurrentByAccountId(account).orElseThrow();
    Plan source = plan(null);
    copyFeatures(subscription.getPlan(), source);
    Plan target = plan(source);
    copyFeatures(source, target);
    var price =
        ProductPrice.draft(source, source.money(), BillingCycle.MONTHLY, Instant.EPOCH, null);
    price.activate();
    prices.saveAndFlush(price);
    subscription.setPlan(source);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    subscription.setEntitlementSnapshot(
        snapshots
            .fromPlan(source)
            .withEffectivePeriod(
                subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd()));
    subscription.setCurrentMoney(source.money());
    subscriptions.saveAndFlush(subscription);
    var period =
        periods
            .findBySubscriptionIdAndStatus(subscription.getId(), SubscriptionPeriodStatus.OPEN)
            .orElseThrow();
    period.setEntitlementSnapshot(subscription.getEntitlementSnapshot());
    periods.saveAndFlush(period);
    return new Fixture(account, subscription, source, target, admin, client);
  }

  private Plan plan(Plan source) {
    Plan plan = new Plan();
    plan.setCode("CONTENT_" + UUID.randomUUID().toString().replace("-", ""));
    plan.setName("Content test");
    plan.setMoney(Money.of(new BigDecimal("100"), "MAD"));
    plan.setBillingCycle(BillingCycle.MONTHLY);
    plan.setStatus(PlanStatus.ACTIVE);
    if (source != null) {
      plan.setLineageId(source.getLineageId());
      plan.setRevisionNumber(2);
      plan.setSourcePlan(source);
    }
    return plans.saveAndFlush(plan);
  }

  private void copyFeatures(Plan source, Plan target) {
    for (var row : features.findAllByPlanId(source.getId())) {
      var copy = new PlanFeature();
      copy.setPlan(target);
      copy.setFeature(row.getFeature());
      copy.setMode(row.getMode());
      copy.setQuotaConfigs(row.getQuotaConfigs());
      features.save(copy);
    }
    features.flush();
  }

  private record Fixture(
      UUID accountId,
      Subscription subscription,
      Plan source,
      Plan target,
      String admin,
      String client) {}
}
