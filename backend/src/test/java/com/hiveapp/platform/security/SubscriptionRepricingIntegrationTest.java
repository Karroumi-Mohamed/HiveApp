package com.hiveapp.platform.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.dto.RepricingModels.*;
import com.hiveapp.platform.client.plan.service.*;
import com.hiveapp.shared.money.Money;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class SubscriptionRepricingIntegrationTest extends PlatformShellIntegrationTestSupport {
  @Autowired UserRepository users;
  @Autowired AccountRepository accounts;
  @Autowired SubscriptionRepository subscriptions;
  @Autowired PlanRepository plans;
  @Autowired ProductPriceRepository prices;
  @Autowired SubscriptionRepricingItemRepository items;
  @Autowired SubscriptionRepricingService service;
  @Autowired SubscriptionRepricingProcessor processor;
  @Autowired SubscriptionRenewalChangeProcessor renewals;
  @Autowired SubscriptionChangeOperationRepository operations;
  @Autowired BillingInvoiceRepository invoices;
  @Autowired PlatformTransactionManager transactions;
  @Autowired SubscriptionBillingRenewalService billingRenewals;
  @Autowired SubscriptionCheckoutService checkoutService;
  @Autowired RepricingNoticeDelivery noticeDelivery;
  @Autowired RepricingNoticeDispatcher noticeDispatcher;
  @Autowired SubscriptionRepricingExecutor executor;
  @Autowired BillingOutboxTransactionService billingTransactions;
  @Autowired BillingOutboxCommandRepository billingCommands;
  @Autowired BillingPaymentAttemptRepository payments;
  @Autowired BillingRecoveryService billingRecovery;

  @Autowired
  com.hiveapp.platform.client.plan.infrastructure.AcceptedPriceProjectionBackfill priceProjection;

  @Test
  void failedPaymentCanRecoverWithoutRepeatingTheRepricingInstruction() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    var review =
        preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    confirm(admin, review);
    UUID job = UUID.fromString(review.path("summary").path("id").asText());
    processor.processDue(f.renewal);
    UUID checkoutId =
        new TransactionTemplate(transactions)
            .execute(
                tx ->
                    items
                        .findAllByJobIdOrderById(job)
                        .getFirst()
                        .getOperation()
                        .getCheckout()
                        .getId());
    UUID invoiceId = invoices.findByCheckoutId(checkoutId).orElseThrow().getId();
    var payment =
        payments
            .findFirstByInvoiceIdAndKindOrderByCreatedAtDesc(invoiceId, BillingPaymentKind.PROVIDER)
            .orElseThrow();
    var command =
        billingCommands
            .findByAggregateIdAndOperation(payment.getId(), BillingOutboxOperation.CHARGE)
            .orElseThrow();
    assertThat(billingTransactions.claim(command.getId())).isPresent();
    billingTransactions.complete(
        command.getId(),
        new BillingProviderCommandExecutor.Result(
            new com.hiveapp.shared.payment.PaymentResult(
                "decline-" + command.getId(),
                com.hiveapp.shared.payment.PaymentStatus.FAILED,
                "Declined"),
            true));
    assertThat(
            service
                .results(job, null, org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent()
                .getFirst()
                .blocker())
        .isEqualTo("PAYMENT_FAILED");
    assertThat(subscriptions.findCurrentByAccountId(f.accountId).orElseThrow().getCurrentPrice())
        .isEqualByComparingTo("100");
    assertThat(billingRecovery.preview(invoiceId).retryAllowed()).isTrue();
    UUID actorId = users.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
    var retry =
        billingRecovery.retryCharge(
            invoiceId,
            actorId,
            "repricing-recovery-" + invoiceId,
            "Retry declined renewal",
            "confirmed-decline",
            false);
    var retryCommand =
        billingCommands
            .findByAggregateIdAndOperation(retry.getId(), BillingOutboxOperation.CHARGE)
            .orElseThrow();
    assertThat(billingTransactions.claim(retryCommand.getId())).isPresent();
    billingTransactions.complete(
        retryCommand.getId(),
        new BillingProviderCommandExecutor.Result(
            new com.hiveapp.shared.payment.PaymentResult(
                "paid-" + retryCommand.getId(),
                com.hiveapp.shared.payment.PaymentStatus.SUCCESS,
                null),
            true));
    renewals.processDue(f.renewal);
    processor.processDue(f.renewal);
    assertThat(invoices.findById(invoiceId).orElseThrow().getStatus())
        .isEqualTo(BillingInvoiceStatus.SETTLED);
    assertThat(items.findAllByJobIdOrderById(job).getFirst().getStatus()).isEqualTo(State.APPLIED);
    var current = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
    assertThat(current.getCurrentPrice()).isEqualByComparingTo("200");
    assertThat(current.getEntitlementSnapshot().features()).isEqualTo(f.snapshot.features());
    assertThat(current.getEntitlementSnapshot().planPriceEntryId()).isEqualTo(f.targetId);
    assertThat(payments.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId)).hasSize(2);
  }

  @Test
  void segmentAudienceIntersectsExactTariffAndFreezesItsActivation() throws Exception {
    Fixture f = fixture("100", "200"), other = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    var create =
        new CommercialSegmentRequests.Create(
            "Repricing QA segment",
            null,
            CommercialSegmentKind.EXPLICIT_ACCOUNTS,
            "Freeze a reviewed audience",
            new CommercialSegmentRequests.Definition(Set.of(f.accountId, other.accountId), null));
    var segment =
        objectMapper.readTree(
            mockMvc
                .perform(
                    post("/api/admin/segments")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String segmentId = segment.at("/summary/id").asText();
    var review =
        objectMapper.readTree(
            mockMvc
                .perform(
                    get("/api/admin/segments/{id}/preview", segmentId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    mockMvc
        .perform(
            post("/api/admin/segments/{id}/activate", segmentId)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CommercialSegmentRequests.Activation(
                            review.path("criteriaVersion").asLong(),
                            "Activate test audience",
                            review.path("previewToken").asText()))))
        .andExpect(status().isOk());
    var p =
        preview(
            admin,
            new Request(
                f.sourceId,
                f.targetId,
                Audience.SEGMENT,
                List.of(),
                List.of(),
                null,
                null,
                UUID.fromString(segmentId),
                null,
                false,
                "Use reviewed segment"));
    assertThat(p.path("summary").path("targetCount").asInt()).isEqualTo(1);
    UUID job = UUID.fromString(p.path("summary").path("id").asText());
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var item = items.findAllByJobIdOrderById(job).getFirst();
              assertThat(item.getJob().getSegmentActivationId()).isNotNull();
              assertThat(item.getAccount().getId()).isEqualTo(f.accountId);
            });
    var filtered =
        preview(
            admin,
            new Request(
                f.sourceId,
                f.targetId,
                Audience.FILTERED,
                List.of(),
                List.of(),
                null,
                SubscriptionStatus.ACTIVE,
                null,
                null,
                false,
                "Filter by active subscription"));
    assertThat(filtered.path("summary").path("targetCount").asInt()).isEqualTo(1);
  }

  @Test
  void revertedCommercialEditsAndTamperedTokensStillInvalidateReview() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    var p = preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    UUID job = UUID.fromString(p.path("summary").path("id").asText());
    mockMvc
        .perform(
            post("/api/admin/subscription-repricing/{id}/confirm", job)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new Confirm(p.path("previewToken").asText() + "tampered"))))
        .andExpect(status().isConflict());
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var current = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
              current.setCurrentMoney(Money.of(new BigDecimal("150"), "USD"));
              subscriptions.saveAndFlush(current);
              current.setCurrentMoney(Money.of(new BigDecimal("100"), "USD"));
              subscriptions.saveAndFlush(current);
            });
    mockMvc
        .perform(
            post("/api/admin/subscription-repricing/{id}/confirm", job)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new Confirm(p.path("previewToken").asText()))))
        .andExpect(status().isConflict());
    assertThat(items.findAllByJobIdOrderById(job).getFirst().getNoticeCreatedAt()).isNull();
  }

  @Test
  void ordinaryRenewalRetainsAcceptedPriceAndFutureRepricingSurvivesIt() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    var p =
        preview(
            admin,
            request(
                f, Audience.SELECTED, List.of(f.accountId), List.of(), f.renewal.plusSeconds(1)));
    confirm(admin, p);
    var terms = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow().termsIdentity();
    UUID checkoutId =
        new TransactionTemplate(transactions)
            .execute(
                tx -> {
                  var renewal =
                      billingRenewals.ensureCharge(
                          subscriptions.findCurrentByAccountId(f.accountId).orElseThrow());
                  assertThat(renewal.getTargetSnapshot().basePrice()).isEqualByComparingTo("100");
                  return renewal.getCheckout().getId();
                });
    // Explicit settlement, not the repricing confirmation, permits paid renewal.
    checkoutService.confirmManual(
        checkoutId,
        users.findByEmail(ADMIN_EMAIL).orElseThrow().getId(),
        "renewal-" + checkoutId,
        "Test bank transfer received");
    renewals.processDue(f.renewal);
    var renewed = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
    assertThat(renewed.termsIdentity()).isEqualTo(terms);
    assertThat(renewed.getCurrentPrice()).isEqualByComparingTo("100");
    var item =
        items
            .findAllByJobIdOrderById(UUID.fromString(p.path("summary").path("id").asText()))
            .getFirst();
    processor.processDue(renewed.getCurrentPeriodEnd());
    assertThat(items.findById(item.getId()).orElseThrow().getStatus())
        .isEqualTo(State.AWAITING_PAYMENT);
  }

  @Test
  void freeToPaidNeverRenewsForFreeWhileNewChargeIsOutstanding() throws Exception {
    Fixture f = fixture("0", "200");
    var p =
        preview(
            loginAdminAndGetToken(),
            request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    confirm(loginAdminAndGetToken(), p);
    processor.processDue(f.renewal);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var current = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
              assertThat(billingRenewals.hasOutstandingPaidRenewal(current)).isTrue();
              assertThat(current.getCurrentPrice()).isEqualByComparingTo("0");
            });
  }

  @Test
  void concurrentProcessingCreatesOneOperationAndOneInvoice() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    var p = preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    confirm(admin, p);
    UUID itemId =
        items
            .findAllByJobIdOrderById(UUID.fromString(p.path("summary").path("id").asText()))
            .getFirst()
            .getId();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> executor.execute(itemId, f.renewal));
      var second = pool.submit(() -> executor.execute(itemId, f.renewal));
      first.get(15, java.util.concurrent.TimeUnit.SECONDS);
      second.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var item = items.findById(itemId).orElseThrow();
              assertThat(item.getOperation()).isNotNull();
              assertThat(
                      operations
                          .findTopByAccountIdAndStatusIn(
                              f.accountId, List.of(SubscriptionChangeStatus.AWAITING_CONFIRMATION))
                          .orElseThrow()
                          .getId())
                  .isEqualTo(item.getOperation().getId());
              assertThat(invoices.findByCheckoutId(item.getOperation().getCheckout().getId()))
                  .isPresent();
            });
  }

  @Test
  void staleExecutionCannotOverwriteChangedTermsAfterSettlement() throws Exception {
    Fixture f = fixture("100", "0");
    String admin = loginAdminAndGetToken();
    var p = preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    confirm(admin, p);
    processor.processDue(f.renewal);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var current = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
              current.setCancelAtPeriodEnd(true);
              subscriptions.saveAndFlush(current);
            });
    renewals.processDue(f.renewal);
    assertThat(
            items
                .findAllByJobIdOrderById(UUID.fromString(p.path("summary").path("id").asText()))
                .getFirst()
                .getStatus())
        .isEqualTo(State.CONFLICT);
    assertThat(subscriptions.findCurrentByAccountId(f.accountId).orElseThrow().getCurrentPrice())
        .isEqualByComparingTo("100");
  }

  @Test
  void technicalRetryPreservesOriginalReviewAndCancellationCannotBeRetried() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    var p = preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    confirm(admin, p);
    UUID job = UUID.fromString(p.path("summary").path("id").asText());
    var item = items.findAllByJobIdOrderById(job).getFirst();
    executor.recordFailure(item.getId());
    service.retryExecution(job, item.getId(), "Retry original terms after database recovery");
    assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(State.PENDING);
    service.cancel(
        job, users.findByEmail(ADMIN_EMAIL).orElseThrow().getId(), "Withdraw", item.getId());
    assertThatThrownBy(() -> service.retryExecution(job, item.getId(), "Cannot revive"))
        .isInstanceOf(com.hiveapp.shared.exception.InvalidStateException.class);
  }

  @Test
  void deliveryIsDurableRetryableAndUnverifiedEmailsAreSuppressed() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var owner = accounts.findById(f.accountId).orElseThrow().getOwner();
              owner.setEmailVerified(false);
              users.saveAndFlush(owner);
            });
    Request r =
        new Request(
            f.sourceId,
            f.targetId,
            Audience.SELECTED,
            List.of(f.accountId),
            List.of(),
            null,
            null,
            null,
            null,
            true,
            "Notify price change");
    var p = preview(admin, r);
    confirm(admin, p);
    UUID job = UUID.fromString(p.path("summary").path("id").asText());
    var item = items.findAllByJobIdOrderById(job).getFirst();
    assertThat(noticeDelivery.claim(item.getId())).isNull();
    assertThat(items.findById(item.getId()).orElseThrow().getDelivery())
        .isEqualTo(Delivery.SUPPRESSED);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var owner = accounts.findById(f.accountId).orElseThrow().getOwner();
              owner.setEmailVerified(true);
              users.saveAndFlush(owner);
              var retry = items.findById(item.getId()).orElseThrow();
              retry.setDelivery(Delivery.FAILED);
              retry.setEmailAttempts(3);
              items.saveAndFlush(retry);
            });
    service.retryNotice(job, "Explicit mail retry");
    var claim = noticeDelivery.claim(item.getId());
    assertThat(claim).isNotNull();
    assertThat(noticeDelivery.claim(item.getId())).isNull();
    noticeDelivery.complete(claim, Delivery.SENT);
    assertThat(items.findById(item.getId()).orElseThrow().getDelivery()).isEqualTo(Delivery.SENT);
    assertThat(items.findById(item.getId()).orElseThrow().getEmailAttempts()).isEqualTo(4);
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(tx -> noticeDispatcher.dispatch()))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  @Test
  void oldProjectionBackfillUsesOnlyAcceptedTariffIds() throws Exception {
    Fixture f = fixture("100", "200");
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var current = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
              assertThat(current.getCurrentHoldings())
                  .extracting(SubscriptionCurrentHolding::getPriceEntryId)
                  .contains(f.sourceId);
            });
    priceProjection.backfill();
    var p =
        preview(
            loginAdminAndGetToken(),
            request(f, Audience.TARIFF_HOLDERS, List.of(), List.of(), null));
    assertThat(p.path("summary").path("targetCount").asInt()).isEqualTo(1);
  }

  @Test
  void previewAndConfirmPreserveCurrentTermsAndExposePrivateNotice() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    JsonNode preview =
        preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    assertThat(preview.path("summary").path("readyCount").asInt()).isEqualTo(1);
    assertThat(preview.path("sample").get(0).has("accountId")).isFalse();
    UUID job = UUID.fromString(preview.path("summary").path("id").asText());
    confirm(admin, preview);
    assertThat(subscriptions.findCurrentByAccountId(f.accountId).orElseThrow().getCurrentPrice())
        .isEqualByComparingTo("100");
    mockMvc
        .perform(
            get("/api/v1/subscriptions/notices").header("Authorization", bearer(f.clientToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].change.newTotal").value("200.0000"));
    var item = items.findAllByJobIdOrderById(job).getFirst();
    mockMvc
        .perform(
            post("/api/v1/subscriptions/notices/{id}/read", item.getId())
                .header("Authorization", bearer(f.clientToken)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            get("/api/v1/subscriptions/notices").header("Authorization", bearer(f.clientToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].read").value(true));
    String outsider = registerClientAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/subscriptions/notices/{id}/read", item.getId())
                .header("Authorization", bearer(outsider)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/admin/subscription-repricing/{id}", job)
                .header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.request.accountIds").isEmpty());
  }

  @Test
  void positivePriceCreatesOneInvoiceOnlyAtTheRenewalAndNeedsSettlement() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    JsonNode p =
        preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    confirm(admin, p);
    UUID job = UUID.fromString(p.path("summary").path("id").asText());
    processor.processDue(f.renewal.minusSeconds(1));
    assertThat(items.findAllByJobIdOrderById(job).getFirst().getStatus()).isEqualTo(State.PENDING);
    processor.processDue(f.renewal);
    processor.processDue(f.renewal);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var item = items.findAllByJobIdOrderById(job).getFirst();
              assertThat(item.getStatus()).isEqualTo(State.AWAITING_PAYMENT);
              assertThat(item.getOperation().getCheckout().getAmount()).isEqualByComparingTo("200");
              assertThat(invoices.findByCheckoutId(item.getOperation().getCheckout().getId()))
                  .isPresent();
              assertThat(
                      subscriptions
                          .findCurrentByAccountId(f.accountId)
                          .orElseThrow()
                          .getCurrentPrice())
                  .isEqualByComparingTo("100");
            });
  }

  @Test
  void zeroPriceUsesNoPaymentPathAndPreservesEntitlements() throws Exception {
    Fixture f = fixture("100", "0");
    String admin = loginAdminAndGetToken();
    JsonNode p =
        preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    confirm(admin, p);
    processor.processDue(f.renewal);
    renewals.processDue(f.renewal);
    var current = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
    assertThat(current.getCurrentPrice()).isEqualByComparingTo("0");
    assertThat(current.getEntitlementSnapshot().features()).isEqualTo(f.snapshot.features());
    assertThat(current.getEntitlementSnapshot().planPriceEntryId()).isEqualTo(f.targetId);
    assertThat(
            items
                .findAllByJobIdOrderById(UUID.fromString(p.path("summary").path("id").asText()))
                .getFirst()
                .getStatus())
        .isEqualTo(State.APPLIED);
  }

  @Test
  void cancellationAndCustomerChangesNeverApplyThePrice() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    JsonNode p =
        preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    confirm(admin, p);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var s = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
              s.setCommercialTermsId(UUID.randomUUID());
              subscriptions.saveAndFlush(s);
            });
    processor.processDue(f.renewal);
    var item =
        items
            .findAllByJobIdOrderById(UUID.fromString(p.path("summary").path("id").asText()))
            .getFirst();
    assertThat(item.getBlocker()).isEqualTo("SUBSCRIPTION_CHANGED");
    assertThat(item.getStatus()).isEqualTo(State.CONFLICT);
    JsonNode next =
        preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    confirm(admin, next);
    mockMvc
        .perform(
            post(
                    "/api/admin/subscription-repricing/{id}/cancel",
                    next.path("summary").path("id").asText())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Keep their existing contract\"}"))
        .andExpect(status().isOk());
    processor.processDue(f.renewal);
    assertThat(subscriptions.findCurrentByAccountId(f.accountId).orElseThrow().getCurrentPrice())
        .isEqualByComparingTo("100");
  }

  @Test
  void staleConfirmationAndClientAudienceAreRejected() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    Request request = request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null);
    mockMvc
        .perform(
            post("/api/admin/subscription-repricing/preview")
                .header("Authorization", bearer(f.clientToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
    JsonNode p = preview(admin, request);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var s = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
              s.setCancelAtPeriodEnd(true);
              subscriptions.saveAndFlush(s);
            });
    mockMvc
        .perform(
            post(
                    "/api/admin/subscription-repricing/{id}/confirm",
                    p.path("summary").path("id").asText())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new Confirm(p.path("previewToken").asText()))))
        .andExpect(status().isConflict());
  }

  @Test
  void exactTariffAudienceHonorsExclusionsAndTermsAreProtected() throws Exception {
    Fixture f = fixture("100", "200");
    String admin = loginAdminAndGetToken();
    JsonNode p = preview(admin, request(f, Audience.TARIFF_HOLDERS, List.of(), List.of(), null));
    assertThat(p.path("summary").path("targetCount").asInt()).isEqualTo(1);
    mockMvc
        .perform(
            post("/api/admin/subscription-repricing/preview")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        request(
                            f, Audience.TARIFF_HOLDERS, List.of(), List.of(f.accountId), null))))
        .andExpect(status().isBadRequest());
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var s = subscriptions.findCurrentByAccountId(f.accountId).orElseThrow();
              s.setCurrentMoney(Money.of(new BigDecimal("75"), "USD"));
              subscriptions.saveAndFlush(s);
            });
    p = preview(admin, request(f, Audience.SELECTED, List.of(f.accountId), List.of(), null));
    assertThat(p.path("sample").get(0).path("blocker").asText()).isEqualTo("PROTECTED_TERMS");
    assertThat(p.path("summary").path("readyCount").asInt()).isZero();
  }

  private Request request(
      Fixture f, Audience audience, List<UUID> ids, List<UUID> excluded, Instant threshold) {
    return new Request(
        f.sourceId,
        f.targetId,
        audience,
        ids,
        excluded,
        null,
        null,
        null,
        threshold,
        false,
        "Reviewed price update");
  }

  private JsonNode preview(String admin, Request request) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(
                post("/api/admin/subscription-repricing/preview")
                    .header("Authorization", bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void confirm(String admin, JsonNode preview) throws Exception {
    mockMvc
        .perform(
            post(
                    "/api/admin/subscription-repricing/{id}/confirm",
                    preview.path("summary").path("id").asText())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new Confirm(preview.path("previewToken").asText()))))
        .andExpect(status().isOk());
  }

  private Fixture fixture(String oldAmount, String newAmount) throws Exception {
    String email = "repricing-" + UUID.randomUUID() + "@example.com";
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RegisterRequest(email, CLIENT_PASSWORD, "Price", "Owner", null))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String client = objectMapper.readTree(body).path("accessToken").asText();
    UUID account =
        accounts
            .findByOwner_Id(users.findByEmail(email).orElseThrow().getId())
            .orElseThrow()
            .getId();
    return new TransactionTemplate(transactions)
        .execute(
            tx -> {
              Plan plan = new Plan();
              plan.setCode(
                  "REPRICE_"
                      + UUID.randomUUID().toString().replace('-', '_').toUpperCase(Locale.ROOT));
              plan.setName("Price-only test");
              plan.setMoney(Money.of(new BigDecimal(oldAmount), "USD"));
              plan.setBillingCycle(BillingCycle.MONTHLY);
              plan.setStatus(PlanStatus.ACTIVE);
              plans.saveAndFlush(plan);
              ProductPrice oldPrice =
                  ProductPrice.draft(plan, plan.money(), BillingCycle.MONTHLY, Instant.EPOCH, null);
              oldPrice.activate();
              prices.saveAndFlush(oldPrice);
              ProductPrice newPrice =
                  ProductPrice.draft(
                      plan,
                      Money.of(new BigDecimal(newAmount), "USD"),
                      BillingCycle.MONTHLY,
                      Instant.EPOCH,
                      null);
              newPrice.activate();
              prices.saveAndFlush(newPrice);
              Subscription s = subscriptions.findCurrentByAccountId(account).orElseThrow();
              var original = s.getEntitlementSnapshot();
              var snapshot =
                  new SubscriptionEntitlementSnapshot(
                      4,
                      plan.getCode(),
                      plan.getName(),
                      plan.getVersion(),
                      oldPrice.getAmount(),
                      "USD",
                      BillingCycle.MONTHLY,
                      s.getCurrentPeriodStart(),
                      s.getCurrentPeriodEnd(),
                      original.features(),
                      List.of(),
                      List.of(),
                      oldPrice.getId(),
                      null,
                      null);
              s.setPlan(plan);
              s.setEntitlementSnapshot(snapshot);
              s.setCurrentMoney(plan.money());
              subscriptions.saveAndFlush(s);
              return new Fixture(
                  account,
                  client,
                  oldPrice.getId(),
                  newPrice.getId(),
                  s.getCurrentPeriodEnd(),
                  snapshot);
            });
  }

  private record Fixture(
      UUID accountId,
      String clientToken,
      UUID sourceId,
      UUID targetId,
      Instant renewal,
      SubscriptionEntitlementSnapshot snapshot) {}
}
