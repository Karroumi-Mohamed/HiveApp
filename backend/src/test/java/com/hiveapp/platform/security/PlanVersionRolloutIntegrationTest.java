package com.hiveapp.platform.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Timing;
import com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.*;
import com.hiveapp.platform.client.plan.service.*;
import com.hiveapp.shared.money.Money;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(
    properties = "spring.datasource.url=jdbc:h2:mem:plan-rollouts;DB_CLOSE_DELAY=-1")
@Import(PlanVersionApplicationIntegrationTest.TimeConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlanVersionRolloutIntegrationTest extends PlatformShellIntegrationTestSupport {
  @Autowired PlanRepository plans;
  @Autowired PlanFeatureRepository features;
  @Autowired ProductPriceRepository prices;
  @Autowired SubscriptionRepository subscriptions;
  @Autowired SubscriptionPeriodRepository periods;
  @Autowired SubscriptionContentEvidenceRepository evidence;
  @Autowired SubscriptionChangeJobRepository jobs;
  @Autowired SubscriptionChangeJobItemRepository items;
  @Autowired BillingInvoiceRepository invoices;
  @Autowired AccountRepository accounts;
  @Autowired UserRepository users;
  @Autowired com.hiveapp.platform.admin.domain.repository.AdminUserRepository admins;
  @Autowired SubscriptionSnapshotFactory snapshots;
  @Autowired SubscriptionChangeJobProcessor processor;
  @Autowired SubscriptionChangeJobTransitionService transitions;
  @Autowired PlanVersionRolloutWorker worker;
  @Autowired PlanContentNoticeRepository notices;
  @Autowired PlanContentNoticeDeliverySource noticeDelivery;
  @Autowired com.hiveapp.shared.audit.domain.AuditLogRepository auditLogs;
  @Autowired SubscriptionChangeJobService legacyJobs;
  @Autowired CommercialCatalogVersionService catalogue;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired jakarta.persistence.EntityManager entityManager;
  @Autowired PlanVersionApplicationIntegrationTest.MutableClock clock;
  TransactionTemplate tx;
  String admin;
  final Map<UUID, String> clientTokens = new HashMap<>();
  static final String BASE = "/api/admin/plan-version-applications";

  @BeforeEach
  void setup() throws Exception {
    tx = new TransactionTemplate(transactionManager);
    clock.set(Instant.now());
    admin = loginAdminAndGetToken();
  }

  @Test
  void familyHistoryIsBoundedPrivateAndIncludesSystemEvents() throws Exception {
    var f = fixture(1);
    var other = fixture(1);
    var at = Instant.now().minusSeconds(10);
    auditLogs.saveAndFlush(
        com.hiveapp.shared.audit.domain.AuditLog.builder()
            .occurredAt(at)
            .actorSurface(com.hiveapp.shared.audit.domain.AuditActorSurface.SYSTEM)
            .resourceType("PLAN")
            .resourceId(f.target.toString())
            .action("PLAN_VERSION_TEST")
            .outcome(com.hiveapp.shared.audit.domain.AuditOutcome.SUCCEEDED)
            .requestData("private request payload")
            .resultData("private result payload")
            .build());
    var body =
        mockMvc
            .perform(
                get("/api/admin/plans/{id}/version-history", f.source)
                    .param("kind", "VERSION")
                    .param("from", at.minusSeconds(1).toString())
                    .param("until", at.plusSeconds(1).toString())
                    .header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("totalElements").value(1))
            .andExpect(jsonPath("content[0].productVersionNumber").value(2))
            .andExpect(jsonPath("content[0].actorLabel").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body)
        .doesNotContain("private request", "private result", "requestData", "resultData");
    mockMvc
        .perform(
            get("/api/admin/plans/{id}/version-history", other.source)
                .header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(0));
    mockMvc
        .perform(
            get("/api/admin/plans/{id}/version-history", f.source)
                .param("size", "101")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/admin/plans/{id}/version-history", f.source)
                .header("Authorization", bearer(clientTokens.get(f.accounts.getFirst()))))
        .andExpect(status().isForbidden());
    var job = create(f, request(f, Audience.ALL, Timing.NOW, List.of()));
    mockMvc
        .perform(
            get("/api/admin/plans/{id}/version-history", f.source)
                .param("kind", "APPLICATION")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].resourceId").value(job.summary().id().toString()));
  }

  @Test
  void unrelatedExclusionsAreRejectedRatherThanSilentlyDropped() throws Exception {
    var f = fixture(1);
    var outsider = fixture(1);
    mockMvc
        .perform(
            post(BASE)
                .param("targetPlanId", f.target.toString())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        request(f, Audience.ALL, Timing.NOW, outsider.accounts))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void audienceIsAssessedBeforeConfirmationAndAppliesWithoutNewBilling() throws Exception {
    var f = fixture(1);
    long invoicesBefore = invoices.count();
    var before = subscriptions.findCurrentByAccountId(f.accounts.getFirst()).orElseThrow();
    var created = create(f, request(f, Audience.SELECTED, Timing.NOW, List.of()));
    assertThat(created.summary().status()).isEqualTo(SubscriptionChangeJobStatus.ASSESSING);
    assertThat(created.previewToken()).isNull();
    assertThat(
            subscriptions
                .findCurrentByAccountId(f.accounts.getFirst())
                .orElseThrow()
                .getPlan()
                .getId())
        .isEqualTo(f.source);
    processor.processDue(clock.instant());
    var review = detail(created.summary().id());
    assertThat(review.summary().status()).isEqualTo(SubscriptionChangeJobStatus.PREVIEWED);
    assertThat(review.summary().counts()).containsEntry(SubscriptionChangeJobItemStatus.READY, 1L);
    confirm(review, false, 200);
    processor.processDue(clock.instant());
    var result = detail(review.summary().id());
    assertThat(result.summary().status()).isEqualTo(SubscriptionChangeJobStatus.COMPLETED);
    assertThat(result.summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.APPLIED, 1L);
    var current = subscriptions.findCurrentByAccountId(f.accounts.getFirst()).orElseThrow();
    assertThat(current.getId()).isEqualTo(before.getId());
    assertThat(current.getPlan().getId()).isEqualTo(f.target);
    assertThat(current.getCurrentPrice()).isEqualByComparingTo(before.getCurrentPrice());
    assertThat(current.getCurrentPeriodEnd()).isEqualTo(before.getCurrentPeriodEnd());
    assertThat(current.termsIdentity()).isEqualTo(before.termsIdentity());
    assertThat(invoices.count()).isEqualTo(invoicesBefore);
    processor.processDue(clock.instant());
    assertThat(
            items
                .findAllByJobId(
                    result.summary().id(), org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent())
        .allMatch(item -> item.getAttempts() == 1);
  }

  @Test
  void mixedAudienceRequiresExplicitPartialConfirmationAndExcludesLaterArrivals() throws Exception {
    var f = fixture(3);
    tx.executeWithoutResult(
        ignored -> {
          var conflict = subscriptions.findCurrentByAccountId(f.accounts.get(1)).orElseThrow();
          conflict.setCancelAtPeriodEnd(true);
          subscriptions.saveAndFlush(conflict);
        });
    var created = create(f, request(f, Audience.ALL, Timing.NOW, List.of(f.accounts.get(2))));
    UUID later = addAccount(f.source);
    processor.processDue(clock.instant());
    var review = detail(created.summary().id());
    assertThat(review.summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.READY, 1L)
        .containsEntry(SubscriptionChangeJobItemStatus.CONFLICT, 1L)
        .containsEntry(SubscriptionChangeJobItemStatus.EXCLUDED, 1L);
    confirm(review, false, 400);
    assertThat(review.conflicts().getFirst().resolutionKind())
        .isEqualTo(ResolutionKind.REVIEW_SUBSCRIPTION);
    mockMvc
        .perform(
            get(BASE + "/{id}/results", review.summary().id())
                .param("reason", "CANCELLATION_PENDING")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(1));
    confirm(review, true, 200);
    processor.processDue(clock.instant());
    assertThat(detail(created.summary().id()).summary().status())
        .isEqualTo(SubscriptionChangeJobStatus.COMPLETED_WITH_ERRORS);
    assertThat(
            subscriptions.findCurrentByAccountId(f.accounts.get(0)).orElseThrow().getPlan().getId())
        .isEqualTo(f.target);
    for (UUID account : List.of(f.accounts.get(1), f.accounts.get(2), later))
      assertThat(subscriptions.findCurrentByAccountId(account).orElseThrow().getPlan().getId())
          .isEqualTo(f.source);
    mockMvc
        .perform(
            post(BASE + "/" + created.summary().id() + "/retry")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Try conflicts again\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void cancellationKeepsAppliedAccountsAndCancelsOnlyRemainingWork() throws Exception {
    var f = fixture(2);
    var created = create(f, request(f, Audience.SELECTED, Timing.NOW, List.of()));
    processor.processDue(clock.instant());
    var review = detail(created.summary().id());
    confirm(review, false, 200);
    transitions.claimOrResume(review.summary().id(), clock.instant());
    var rows =
        items.findAllByJobId(
            review.summary().id(), org.springframework.data.domain.PageRequest.of(0, 10));
    worker.execute(review.summary().id(), rows.getContent().getFirst().getId());
    mockMvc
        .perform(
            post(BASE + "/" + review.summary().id() + "/cancel")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Stop the remaining Accounts\"}"))
        .andExpect(status().isOk());
    processor.processDue(clock.instant());
    var stopped = detail(review.summary().id());
    assertThat(stopped.summary().status()).isEqualTo(SubscriptionChangeJobStatus.CANCELLED);
    assertThat(stopped.summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.APPLIED, 1L)
        .containsEntry(SubscriptionChangeJobItemStatus.CANCELLED, 1L);
  }

  @Test
  void renewalWaitsWithoutPaymentSlotAndCanBeCancelled() throws Exception {
    var f = fixture(1);
    var created = create(f, request(f, Audience.SELECTED, Timing.AT_RENEWAL, List.of()));
    processor.processDue(clock.instant());
    confirm(detail(created.summary().id()), false, 200);
    processor.processDue(clock.instant());
    var waiting = detail(created.summary().id());
    assertThat(waiting.summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.WAITING, 1L);
    assertThat(waiting.summary().executeAt()).isAfter(clock.instant());
    tx.executeWithoutResult(
        ignored ->
            assertThat(items.countOtherPendingContent(f.accounts.getFirst(), null)).isEqualTo(1));
    mockMvc
        .perform(
            post(BASE + "/" + created.summary().id() + "/cancel")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Cancel pending content\"}"))
        .andExpect(status().isOk());
    assertThat(items.countOtherPendingContent(f.accounts.getFirst(), null)).isZero();
  }

  @Test
  void technicalRetryRetainsReviewAndAppliesExactlyOnce() throws Exception {
    var f = fixture(1);
    var created = create(f, request(f, Audience.SELECTED, Timing.NOW, List.of()));
    processor.processDue(clock.instant());
    confirm(detail(created.summary().id()), false, 200);
    transitions.claimOrResume(created.summary().id(), clock.instant());
    UUID item =
        items
            .findAllByJobId(
                created.summary().id(), org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent()
            .getFirst()
            .getId();
    worker.failed(
        created.summary().id(), item, new RuntimeException("Simulated transaction failure"));
    worker.finishExecution(created.summary().id());
    mockMvc
        .perform(
            post(BASE + "/" + created.summary().id() + "/retry")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Retry after recovery\"}"))
        .andExpect(status().isOk());
    processor.processDue(clock.instant());
    assertThat(detail(created.summary().id()).summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.APPLIED, 1L);
    assertThat(evidence.findByCommandId(item)).isPresent();
    worker.failed(created.summary().id(), item, new RuntimeException("Late response lost"));
    assertThat(items.findById(item).orElseThrow().getStatus())
        .isEqualTo(SubscriptionChangeJobItemStatus.APPLIED);
  }

  @Test
  void staleReviewAndClientRequestsCannotConfirmAndLegacyJobsCannotReadContent() throws Exception {
    var f = fixture(1);
    var created = create(f, request(f, Audience.SELECTED, Timing.NOW, List.of()));
    processor.processDue(clock.instant());
    var review = detail(created.summary().id());
    assertThatThrownBy(() -> legacyJobs.get(created.summary().id()))
        .isInstanceOf(com.hiveapp.shared.exception.ResourceNotFoundException.class);
    assertThat(
            legacyJobs
                .list(null, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent())
        .noneMatch(job -> job.id().equals(created.summary().id()));
    String client = registerClientAndGetToken();
    mockMvc
        .perform(get(BASE + "/" + created.summary().id()).header("Authorization", bearer(client)))
        .andExpect(status().isForbidden());
    tx.executeWithoutResult(ignored -> catalogue.bump(catalogue.lockForMutation().getId()));
    confirm(review, false, 409);
    assertThat(detail(created.summary().id()).reviewInvalidated()).isTrue();
  }

  @Test
  void fixedDateDoesNotRunEarlyAndCompetingConfirmationIsBlocked() throws Exception {
    var f = fixture(1);
    var request = request(f, Audience.SELECTED, Timing.AT_DATE, List.of());
    var first = create(f, request);
    var second = create(f, request);
    processor.processDue(clock.instant());
    var reviewedFirst = detail(first.summary().id());
    var reviewedSecond = detail(second.summary().id());
    confirm(reviewedFirst, false, 200);
    confirm(reviewedSecond, false, 409);
    processor.processDue(clock.instant());
    assertThat(detail(first.summary().id()).summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.WAITING, 1L);
    assertThat(
            subscriptions
                .findCurrentByAccountId(f.accounts.getFirst())
                .orElseThrow()
                .getPlan()
                .getId())
        .isEqualTo(f.source);
    assertThat(notice(first.summary().id()).getState())
        .isEqualTo(com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.State.SCHEDULED);
    clock.set(request.application().notBefore().plusSeconds(1));
    processor.processDue(clock.instant());
    assertThat(detail(first.summary().id()).summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.APPLIED, 1L);
  }

  @Test
  void revokedActorIsAConflictNotAnAutomaticallyRetryableFailure() throws Exception {
    var f = fixture(1);
    var created = create(f, request(f, Audience.SELECTED, Timing.NOW, List.of()));
    processor.processDue(clock.instant());
    confirm(detail(created.summary().id()), false, 200);
    tx.executeWithoutResult(
        ignored -> {
          var actor = admins.findByUser_Email(ADMIN_EMAIL).orElseThrow();
          actor.setActive(false);
          admins.saveAndFlush(actor);
        });
    try {
      processor.processDue(clock.instant());
    } finally {
      tx.executeWithoutResult(
          ignored -> {
            var actor = admins.findByUser_Email(ADMIN_EMAIL).orElseThrow();
            actor.setActive(true);
            admins.saveAndFlush(actor);
          });
    }
    assertThat(detail(created.summary().id()).summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.CONFLICT, 1L);
    assertThat(
            items
                .findAllByJobId(
                    created.summary().id(), org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent())
        .allMatch(item -> "ACTOR_NO_LONGER_AUTHORIZED".equals(item.getOutcomeCode()));
  }

  @Test
  void concurrentWorkersApplyTheSameInstructionOnlyOnce() throws Exception {
    var f = fixture(1);
    var created = create(f, request(f, Audience.SELECTED, Timing.NOW, List.of()));
    processor.processDue(clock.instant());
    confirm(detail(created.summary().id()), false, 200);
    transitions.claimOrResume(created.summary().id(), clock.instant());
    UUID item =
        items
            .findAllByJobId(
                created.summary().id(), org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent()
            .getFirst()
            .getId();
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      var one =
          executor.submit(
              () -> {
                start.await();
                worker.execute(created.summary().id(), item);
                return true;
              });
      var two =
          executor.submit(
              () -> {
                start.await();
                worker.execute(created.summary().id(), item);
                return true;
              });
      start.countDown();
      assertThat(one.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      assertThat(two.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    } finally {
      executor.shutdownNow();
    }
    assertThat(items.findById(item).orElseThrow().getAttempts()).isEqualTo(1);
    assertThat(evidence.findByCommandId(item)).isPresent();
  }

  @Test
  void assessmentIsBatchedAndPublicResultsDoNotExposeInternalBillingSnapshots() throws Exception {
    var f = fixture(PlanVersionRolloutProcessor.ITEM_BATCH_SIZE + 1);
    var excluded = bulkAccounts(f.source, 200);
    var combined = new ArrayList<>(f.accounts);
    combined.addAll(excluded);
    f = new Fixture(f.source, f.target, combined);
    var created = create(f, request(f, Audience.SELECTED, Timing.NOW, excluded));
    processor.processDue(clock.instant());
    var partial = detail(created.summary().id());
    assertThat(partial.summary().status()).isEqualTo(SubscriptionChangeJobStatus.ASSESSING);
    assertThat(partial.summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.ASSESSING, 1L)
        .containsEntry(
            SubscriptionChangeJobItemStatus.READY,
            (long) PlanVersionRolloutProcessor.ITEM_BATCH_SIZE)
        .containsEntry(SubscriptionChangeJobItemStatus.EXCLUDED, 200L);
    processor.processDue(clock.instant());
    assertThat(detail(created.summary().id()).summary().status())
        .isEqualTo(SubscriptionChangeJobStatus.PREVIEWED);
    mockMvc
        .perform(
            get(BASE + "/" + created.summary().id() + "/results")
                .param("size", "1")
                .param("status", "READY")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].impact.retainedTotal").value("100.0000"))
        .andExpect(jsonPath("content[0].assessment").doesNotExist())
        .andExpect(jsonPath("content[0].impact.reviewed").doesNotExist());
  }

  /** Cheap synthetic audience; authentication and actual mutation use registered Accounts above. */
  @Test
  @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
      named = "hiveapp.test.content-scale",
      matches = "true")
  void measureFrozenPopulationSizes() throws Exception {
    for (int size : List.of(100, 1000, 10000)) {
      var f = fixture(0);
      bulkAccounts(f.source, size);
      long start = System.nanoTime();
      var created = create(f, request(f, Audience.ALL, Timing.NOW, List.of()));
      long frozen = System.nanoTime();
      Detail review = created;
      int passes = 0;
      while (review.summary().status() == SubscriptionChangeJobStatus.ASSESSING) {
        assertThat(++passes)
            .isLessThanOrEqualTo(size / PlanVersionRolloutProcessor.ITEM_BATCH_SIZE + 2);
        processor.processDue(clock.instant());
        review = detail(created.summary().id());
      }
      long assessed = System.nanoTime();
      assertThat(review.summary().counts())
          .containsEntry(SubscriptionChangeJobItemStatus.READY, (long) size);
      confirm(review, false, 200);
      passes = 0;
      do {
        assertThat(++passes)
            .isLessThanOrEqualTo(size / PlanVersionRolloutProcessor.ITEM_BATCH_SIZE + 2);
        processor.processDue(clock.instant());
        review = detail(created.summary().id());
      } while (review.summary().status() == SubscriptionChangeJobStatus.RUNNING
          || review.summary().status() == SubscriptionChangeJobStatus.QUEUED);
      long applied = System.nanoTime();
      assertThat(review.summary().status()).isEqualTo(SubscriptionChangeJobStatus.COMPLETED);
      assertThat(review.summary().counts())
          .containsEntry(SubscriptionChangeJobItemStatus.APPLIED, (long) size);
      assertThat(
              entityManager
                  .createQuery(
                      "select count(n) from PlanContentNotice n where n.jobId = :job", Long.class)
                  .setParameter("job", created.summary().id())
                  .getSingleResult())
          .isEqualTo(size);
      long readStart = System.nanoTime();
      mockMvc
          .perform(
              get("/api/admin/plans/{id}/family-subscribers", f.target)
                  .param("size", "20")
                  .header("Authorization", bearer(admin)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("totalElements").value(size))
          .andExpect(jsonPath("content.length()").value(20));
      long readEnd = System.nanoTime();
      System.out.printf(
          "CONTENT_SCALE accounts=%d freeze_ms=%d assess_ms=%d apply_ms=%d subscriber_page_ms=%d%n",
          size,
          (frozen - start) / 1_000_000,
          (assessed - frozen) / 1_000_000,
          (applied - assessed) / 1_000_000,
          (readEnd - readStart) / 1_000_000);
    }
  }

  @Test
  void inAppNoticeIsPrivateReadIsIdempotentAndDoesNotChangeConsent() throws Exception {
    var f = fixture(1);
    var job = create(f, request(f, Audience.SELECTED, Timing.NOW, List.of()));
    processor.processDue(clock.instant());
    confirm(detail(job.summary().id()), false, 200);
    processor.processDue(clock.instant());
    var notice = notice(job.summary().id());
    String client = clientTokens.get(f.accounts.getFirst());
    mockMvc
        .perform(
            get("/api/v1/subscriptions/content-notices").header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].state").value("APPLIED"))
        .andExpect(jsonPath("content[0].financialTermsRetained").value(true))
        .andExpect(jsonPath("content[0].read").value(false))
        .andExpect(jsonPath("content[0].impact").doesNotExist());
    for (int i = 0; i < 2; i++)
      mockMvc
          .perform(
              post("/api/v1/subscriptions/content-notices/{id}/read", notice.getId())
                  .header("Authorization", bearer(client)))
          .andExpect(status().isNoContent());
    mockMvc
        .perform(
            get("/api/v1/subscriptions/content-notices").header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].read").value(true));
    String outsider = registerClientAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/subscriptions/content-notices/{id}/read", notice.getId())
                .header("Authorization", bearer(outsider)))
        .andExpect(status().isNotFound());
    assertThat(notices.findById(notice.getId()).orElseThrow().getDelivery().getDelivery())
        .isEqualTo(com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.NOT_REQUESTED);
  }

  @Test
  void requiredEmailWaitsThenFailureNeedsExplicitRetryAndSuccessfulDispatch() throws Exception {
    var f = fixture(1);
    verified(f, true);
    var job =
        create(
            f,
            notification(
                request(f, Audience.SELECTED, Timing.NOW, List.of()),
                com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Policy
                    .EMAIL_REQUIRED));
    processor.processDue(clock.instant());
    confirm(detail(job.summary().id()), false, 200);
    processor.processDue(clock.instant());
    assertThat(detail(job.summary().id()).summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.WAITING, 1L);
    var notice = notice(job.summary().id());
    var claim = noticeDelivery.claimNotice(notice.getId());
    assertThat(claim).isNotNull();
    assertThat(noticeDelivery.claimNotice(notice.getId())).isNull();
    noticeDelivery.completeNotice(
        claim, com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.FAILED);
    clock.set(clock.instant().plusSeconds(31));
    processor.processDue(clock.instant());
    assertThat(detail(job.summary().id()).summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.CONFLICT, 1L);
    assertThat(
            subscriptions
                .findCurrentByAccountId(f.accounts.getFirst())
                .orElseThrow()
                .getPlan()
                .getId())
        .isEqualTo(f.source);
    mockMvc
        .perform(
            post(BASE + "/{id}/notices/retry", job.summary().id())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Retry(
                            List.of(notice.getId()), "Transport recovered"))))
        .andExpect(status().isOk());
    claim = noticeDelivery.claimNotice(notice.getId());
    noticeDelivery.completeNotice(
        claim, com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.SENT);
    processor.processDue(clock.instant());
    assertThat(detail(job.summary().id()).summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.APPLIED, 1L);
    assertThat(notices.findById(notice.getId()).orElseThrow().getDelivery().getAttempts())
        .isEqualTo(2);
  }

  @Test
  void optionalEmailDoesNotBlockAndCancelledFutureNoticeIsNotDispatched() throws Exception {
    var f = fixture(1);
    verified(f, false);
    var optional =
        create(
            f,
            notification(
                request(f, Audience.SELECTED, Timing.NOW, List.of()),
                com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Policy.EMAIL));
    processor.processDue(clock.instant());
    confirm(detail(optional.summary().id()), false, 200);
    processor.processDue(clock.instant());
    assertThat(detail(optional.summary().id()).summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.APPLIED, 1L);
    var optionalNotice = notice(optional.summary().id());
    assertThat(noticeDelivery.claimNotice(optionalNotice.getId())).isNull();
    assertThat(notices.findById(optionalNotice.getId()).orElseThrow().getDelivery().getDelivery())
        .isEqualTo(com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.SUPPRESSED);
    var future = fixture(1);
    verified(future, true);
    var scheduled =
        create(
            future,
            notification(
                request(future, Audience.SELECTED, Timing.AT_DATE, List.of()),
                com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Policy.EMAIL));
    processor.processDue(clock.instant());
    confirm(detail(scheduled.summary().id()), false, 200);
    processor.processDue(clock.instant());
    var scheduledNotice = notice(scheduled.summary().id());
    mockMvc
        .perform(
            post(BASE + "/{id}/cancel", scheduled.summary().id())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Withdraw change\"}"))
        .andExpect(status().isOk());
    assertThat(noticeDelivery.claimNotice(scheduledNotice.getId())).isNull();
    assertThat(notices.findById(scheduledNotice.getId()).orElseThrow().getDelivery().getDelivery())
        .isEqualTo(com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.CANCELLED);
  }

  @Test
  void unverifiedRequiredEmailAndRemovingSubscriptionPortalAreReviewConflicts() throws Exception {
    var f = fixture(1);
    verified(f, false);
    tx.executeWithoutResult(
        ignored ->
            features.deleteAll(
                features.findAllByPlanId(f.target).stream()
                    .filter(
                        row ->
                            row.getFeature()
                                .getCode()
                                .equals(
                                    com.hiveapp.platform.registry.definition
                                        .ClientSubscriptionFeature.CODE))
                    .toList()));
    var job =
        create(
            f,
            notification(
                request(f, Audience.SELECTED, Timing.NOW, List.of()),
                com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Policy
                    .EMAIL_REQUIRED));
    processor.processDue(clock.instant());
    var item =
        items
            .findAllByJobId(
                job.summary().id(), org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent()
            .getFirst();
    assertThat(item.getContentAssessment().conflicts())
        .extracting(com.hiveapp.platform.client.plan.dto.SubscriptionChangeConflict::code)
        .contains("NOTICE_RECIPIENT_UNVERIFIED", "SUBSCRIPTION_PORTAL_REQUIRED");
  }

  @Test
  void requiredDispatchDoesNotSurviveRecipientLosingVerification() throws Exception {
    var f = fixture(1);
    verified(f, true);
    var job =
        create(
            f,
            notification(
                request(f, Audience.SELECTED, Timing.NOW, List.of()),
                com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Policy
                    .EMAIL_REQUIRED));
    processor.processDue(clock.instant());
    confirm(detail(job.summary().id()), false, 200);
    processor.processDue(clock.instant());
    var claim = noticeDelivery.claimNotice(notice(job.summary().id()).getId());
    noticeDelivery.completeNotice(
        claim, com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.SENT);
    verified(f, false);
    clock.set(clock.instant().plusSeconds(31));
    processor.processDue(clock.instant());
    assertThat(detail(job.summary().id()).summary().counts())
        .containsEntry(SubscriptionChangeJobItemStatus.CONFLICT, 1L);
    assertThat(
            subscriptions
                .findCurrentByAccountId(f.accounts.getFirst())
                .orElseThrow()
                .getPlan()
                .getId())
        .isEqualTo(f.source);
    mockMvc
        .perform(
            get(BASE + "/{id}/results", job.summary().id()).header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].notice.emailDelivery").value("SENT"))
        .andExpect(jsonPath("content[0].outcomeCode").value("NOTICE_RECIPIENT_UNVERIFIED"));
  }

  private PlanContentNotice notice(UUID jobId) {
    return notices
        .findByCommandId(
            items
                .findAllByJobId(jobId, org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent()
                .getFirst()
                .getId())
        .orElseThrow();
  }

  @Test
  void familySubscriberPresetsKeepCoexistingVersionsAndOtherFamiliesSeparate() throws Exception {
    var f = fixture(2);
    fixture(1);
    var selected = new Fixture(f.source, f.target, List.of(f.accounts.getFirst()));
    var job = create(selected, request(selected, Audience.SELECTED, Timing.NOW, List.of()));
    processor.processDue(clock.instant());
    confirm(detail(job.summary().id()), false, 200);
    processor.processDue(clock.instant());
    assertThat(subscriberView(f.source, "ALL").path("totalElements").asLong()).isEqualTo(2);
    var old = subscriberView(f.source, "CURRENT_VERSION");
    assertThat(old.path("content").get(0).path("productVersionNumber").asLong()).isEqualTo(1);
    assertThat(old.path("totalElements").asLong()).isEqualTo(1);
    var newer = subscriberView(f.source, "OTHER_VERSIONS");
    assertThat(newer.path("content").get(0).path("productVersionNumber").asLong()).isEqualTo(2);
    assertThat(newer.path("content").get(0).path("retainedTotal").asText()).isEqualTo("100.0000");
    assertThat(newer.path("totalElements").asLong()).isEqualTo(1);
    assertThat(subscriberView(f.source, "PENDING").path("totalElements").asLong()).isZero();
    mockMvc
        .perform(
            get("/api/admin/plans/{id}/family-subscribers", f.source)
                .header("Authorization", bearer(registerClientAndGetToken())))
        .andExpect(status().isForbidden());
  }

  @Test
  void pendingAndNeedsReviewPresetsTrackOnlyUnfinishedOrUnresolvedInstructions() throws Exception {
    var f = fixture(1);
    var job = create(f, request(f, Audience.SELECTED, Timing.AT_RENEWAL, List.of()));
    processor.processDue(clock.instant());
    assertThat(subscriberView(f.source, "PENDING").path("totalElements").asLong()).isZero();
    confirm(detail(job.summary().id()), false, 200);
    processor.processDue(clock.instant());
    assertThat(subscriberView(f.source, "PENDING").path("totalElements").asLong()).isEqualTo(1);
    mockMvc
        .perform(
            post(BASE + "/{id}/cancel", job.summary().id())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Change timing\"}"))
        .andExpect(status().isOk());
    assertThat(subscriberView(f.source, "PENDING").path("totalElements").asLong()).isZero();
    var next = create(f, request(f, Audience.SELECTED, Timing.NOW, List.of()));
    processor.processDue(clock.instant());
    confirm(detail(next.summary().id()), false, 200);
    transitions.claimOrResume(next.summary().id(), clock.instant());
    var item =
        items
            .findAllByJobId(
                next.summary().id(), org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent()
            .getFirst();
    worker.failed(next.summary().id(), item.getId(), new RuntimeException("Technical failure"));
    worker.finishExecution(next.summary().id());
    assertThat(subscriberView(f.source, "NEEDS_REVIEW").path("totalElements").asLong())
        .isEqualTo(1);
    mockMvc
        .perform(
            post(BASE + "/{id}/retry", next.summary().id())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Recovered\"}"))
        .andExpect(status().isOk());
    processor.processDue(clock.instant());
    assertThat(subscriberView(f.source, "NEEDS_REVIEW").path("totalElements").asLong()).isZero();
  }

  @Test
  void familySubscriberSearchEscapesWildcardsAndBoundsPages() throws Exception {
    var f = fixture(2);
    tx.executeWithoutResult(
        ignored -> {
          var account = accounts.findById(f.accounts.getFirst()).orElseThrow();
          account.setName("50% local");
          accounts.saveAndFlush(account);
        });
    mockMvc
        .perform(
            get("/api/admin/plans/{id}/family-subscribers", f.source)
                .param("search", "%")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(1));
    mockMvc
        .perform(
            get("/api/admin/plans/{id}/family-subscribers", f.source)
                .param("size", "101")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isBadRequest());
  }

  private com.fasterxml.jackson.databind.JsonNode subscriberView(UUID planId, String view)
      throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(
                get("/api/admin/plans/{id}/family-subscribers", planId)
                    .param("view", view)
                    .header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void verified(Fixture f, boolean value) {
    tx.executeWithoutResult(
        ignored -> {
          var owner = accounts.findById(f.accounts.getFirst()).orElseThrow().getOwner();
          owner.setEmailVerified(value);
          users.saveAndFlush(owner);
        });
  }

  private Request notification(
      Request r, com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Policy policy) {
    return new Request(
        r.sourcePlanId(),
        r.scope(),
        r.audience(),
        r.accountIds(),
        r.excludedAccountIds(),
        r.statuses(),
        r.search(),
        r.currency(),
        r.billingCycle(),
        r.application(),
        policy);
  }

  private List<UUID> bulkAccounts(UUID planId, int count) {
    return tx.execute(
        ignored -> {
          var source = plans.findById(planId).orElseThrow();
          Instant start = clock.instant().minusSeconds(86400),
              end = clock.instant().plusSeconds(2592000);
          var snapshot = snapshots.fromPlan(source).withEffectivePeriod(start, end);
          var session = entityManager.unwrap(org.hibernate.Session.class);
          session.setJdbcBatchSize(50);
          List<UUID> result = new ArrayList<>();
          for (int i = 0; i < count; i++) {
            String code = UUID.randomUUID().toString();
            var owner =
                users.save(
                    com.hiveapp.identity.domain.entity.User.builder()
                        .email("load-" + code + "@example.com")
                        .username(code)
                        .firstName("Load")
                        .lastName("Owner")
                        .build());
            var account =
                accounts.save(
                    com.hiveapp.platform.client.account.domain.entity.Account.builder()
                        .owner(owner)
                        .name("Load " + code)
                        .slug(code)
                        .build());
            var subscription = new Subscription();
            subscription.setAccount(account);
            subscription.setPlan(source);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodStart(start);
            subscription.setCurrentPeriodEnd(end);
            subscription.setEntitlementSnapshot(snapshot);
            subscription.setCurrentMoney(source.money());
            subscriptions.save(subscription);
            var period = new SubscriptionPeriod();
            period.setSubscription(subscription);
            period.setStartsAt(start);
            period.setEndsAt(end);
            period.setEntitlementSnapshot(snapshot);
            periods.save(period);
            result.add(account.getId());
            if (i % 250 == 249) {
              entityManager.flush();
              entityManager.clear();
            }
          }
          entityManager.flush();
          return result;
        });
  }

  private Request request(Fixture f, Audience audience, Timing timing, List<UUID> excluded) {
    return new Request(
        f.source,
        Scope.FAMILY,
        audience,
        audience == Audience.SELECTED ? f.accounts : List.of(),
        excluded,
        Set.of(SubscriptionStatus.ACTIVE),
        null,
        null,
        null,
        new com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Request(
            timing,
            timing == Timing.AT_DATE ? clock.instant().plusSeconds(3600) : null,
            "Reviewed content only"));
  }

  private Detail create(Fixture f, Request request) throws Exception {
    return objectMapper.readValue(
        mockMvc
            .perform(
                post(BASE)
                    .param("targetPlanId", f.target.toString())
                    .header("Authorization", bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        Detail.class);
  }

  private Detail detail(UUID id) throws Exception {
    return objectMapper.readValue(
        mockMvc
            .perform(get(BASE + "/" + id).header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        Detail.class);
  }

  private void confirm(Detail detail, boolean partial, int status) throws Exception {
    mockMvc
        .perform(
            post(BASE + "/" + detail.summary().id() + "/confirm")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new Confirm(detail.previewToken(), partial))))
        .andExpect(status().is(status));
  }

  private Fixture fixture(int count) throws Exception {
    var ids =
        tx.execute(
            ignored -> {
              Plan source = plan(null);
              Plan free = plans.findByCode("FREE").orElseThrow();
              copyFeatures(free, source);
              Plan target = plan(source);
              copyFeatures(source, target);
              var price =
                  ProductPrice.draft(
                      source, source.money(), BillingCycle.MONTHLY, Instant.EPOCH, null);
              price.activate();
              prices.saveAndFlush(price);
              return List.of(source.getId(), target.getId());
            });
    List<UUID> accounts = new ArrayList<>();
    for (int i = 0; i < count; i++) accounts.add(addAccount(ids.getFirst()));
    return new Fixture(ids.get(0), ids.get(1), accounts);
  }

  private UUID addAccount(UUID planId) throws Exception {
    String email = "rollout-" + UUID.randomUUID() + "@example.com";
    String token =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    new RegisterRequest(
                                        email, CLIENT_PASSWORD, "Rollout", "Owner", null))))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("accessToken")
            .asText();
    UUID accountId =
        tx.execute(
            ignored -> {
              UUID account =
                  accounts
                      .findByOwner_Id(users.findByEmail(email).orElseThrow().getId())
                      .orElseThrow()
                      .getId();
              var current = subscriptions.findCurrentByAccountId(account).orElseThrow();
              var source = plans.findById(planId).orElseThrow();
              current.setPlan(source);
              current.setStatus(SubscriptionStatus.ACTIVE);
              current.setEntitlementSnapshot(
                  snapshots
                      .fromPlan(source)
                      .withEffectivePeriod(
                          current.getCurrentPeriodStart(), current.getCurrentPeriodEnd()));
              current.setCurrentMoney(source.money());
              subscriptions.saveAndFlush(current);
              var period =
                  periods
                      .findBySubscriptionIdAndStatus(current.getId(), SubscriptionPeriodStatus.OPEN)
                      .orElseThrow();
              period.setEntitlementSnapshot(current.getEntitlementSnapshot());
              periods.saveAndFlush(period);
              return account;
            });
    clientTokens.put(accountId, token);
    return accountId;
  }

  private Plan plan(Plan source) {
    var plan = new Plan();
    plan.setCode("ROLLOUT_" + UUID.randomUUID().toString().replace("-", ""));
    plan.setName("Rollout test");
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

  private record Fixture(UUID source, UUID target, List<UUID> accounts) {}
}
