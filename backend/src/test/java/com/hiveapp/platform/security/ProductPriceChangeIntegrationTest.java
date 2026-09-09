package com.hiveapp.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.shared.money.Money;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

class ProductPriceChangeIntegrationTest extends PlatformShellIntegrationTestSupport {
    @Autowired PlanRepository plans;
    @Autowired ProductPriceRepository prices;
    @Autowired ProductPriceChangeReceiptRepository receipts;
    @Autowired CommercialOfferRepository offers;
    @Autowired CommercialOfferLineageRepository lineages;
    @Autowired CommercialCampaignRepository campaigns;
    @Autowired com.hiveapp.platform.admin.domain.repository.AdminUserRepository admins;
    @Autowired TransactionTemplate transactions;
    @Autowired com.hiveapp.shared.audit.AuditTrail audit;

    @Autowired
    com.hiveapp.platform.client.plan.service.impl.ProductPriceAdminServiceImpl priceService;

    String token;
    ProductPrice current;
    final List<UUID> offerIds = new ArrayList<>();
    final List<UUID> lineageIds = new ArrayList<>();
    final List<UUID> campaignIds = new ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void removeOwnFixtures() {
        if (current == null) return;
        transactions.executeWithoutResult(
                status -> {
                    offers.deleteAllByIdInBatch(offerIds);
                    lineages.deleteAllByIdInBatch(lineageIds);
                    campaigns.deleteAllByIdInBatch(campaignIds);
                    receipts.deleteAllInBatch(
                            receipts.findAll().stream()
                                    .filter(
                                            receipt ->
                                                    receipt.getCurrentPriceId()
                                                            .equals(current.getId()))
                                    .toList());
                    prices.findAllByPlanId(current.ownerId()).stream()
                            .sorted(Comparator.comparingInt(ProductPrice::getRevisionNumber).reversed())
                            .forEach(price -> { prices.delete(price); prices.flush(); });
                    plans.deleteById(current.ownerId());
                });
    }

    @BeforeEach
    void fixture() throws Exception {
        token = loginAdminAndGetToken();
        current =
                transactions.execute(
                        status -> {
                            Plan plan = new Plan();
                            plan.setCode(
                                    "CHANGE_"
                                            + UUID.randomUUID()
                                                    .toString()
                                                    .toUpperCase(Locale.ROOT));
                            plan.setName("Price change QA");
                            plan.setMoney(Money.of(new BigDecimal("100"), "MAD"));
                            plan.setBillingCycle(BillingCycle.MONTHLY);
                            plan.setStatus(PlanStatus.ACTIVE);
                            plans.saveAndFlush(plan);
                            var price =
                                    ProductPrice.draft(
                                            plan, plan.money(), BillingCycle.MONTHLY, now(), null);
                            price.markCompatibilityDefault();
                            price.activate();
                            return prices.saveAndFlush(price);
                        });
    }

    @Test
    void immediateHandoffUsesOneServerBoundaryAndExactMoney() throws Exception {
        var preview = preview(current, change(current, ProductPriceChangeRequest.Timing.NOW, null));
        var result = confirm(current, preview, UUID.randomUUID(), "change", 200);
        Instant cutoff = Instant.parse(result.get("cutoff").asText());
        UUID nextId = UUID.fromString(result.get("successorPrice").get("id").asText());
        assertThat(result.get("successorPrice").get("amount").asText()).isEqualTo("200.12");
        assertBoundary(current, nextId, cutoff);
        assertThat(prices.findById(nextId).orElseThrow().getEffectiveUntil()).isNull();
        mockMvc.perform(
                        get("/api/admin/product-prices/{id}/history", current.getId())
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("platform.price_books.change"))
                .andExpect(jsonPath("$.content[0].reason").value("Reviewed price change"));
    }

    @Test
    void scheduledChangeCanBeCancelledWithoutGapAndReceiptRetryIsStable() throws Exception {
        Instant cutoff = now().plusSeconds(3600);
        var scheduled =
                confirm(
                        current,
                        preview(
                                current,
                                change(
                                        current,
                                        ProductPriceChangeRequest.Timing.SCHEDULED,
                                        cutoff)),
                        UUID.randomUUID(),
                        "change",
                        200);
        UUID nextId = UUID.fromString(scheduled.get("successorPrice").get("id").asText());
        assertBoundary(current, nextId, cutoff);
        current = prices.findById(current.getId()).orElseThrow();
        var cancel =
                new ProductPriceChangeRequest(
                        ProductPriceChangeRequest.Operation.CANCEL,
                        current.getVersion(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Cancel future price");
        var review = preview(current, cancel);
        assertThat(review.get("change").get("scheduledPriceId").asText())
                .isEqualTo(nextId.toString());
        UUID key = UUID.randomUUID();
        confirm(current, review, key, "cancel-change", 200);
        assertThat(
                        confirm(current, review, key, "cancel-change", 200)
                                .get("existingResult")
                                .asBoolean())
                .isTrue();
        assertThat(prices.findById(current.getId()).orElseThrow().getEffectiveUntil()).isNull();
        assertThat(prices.findById(nextId).orElseThrow().getStatus())
                .isEqualTo(ProductPriceStatus.ARCHIVED);
        assertThat(
                        prices.findApplicable(
                                ProductPriceOwnerType.PLAN,
                                current.ownerId(),
                                "MAD",
                                BillingCycle.MONTHLY,
                                cutoff.plusSeconds(9999)))
                .extracting(ProductPrice::getId)
                .containsExactly(current.getId());
    }

    @Test
    void reschedulingChangesBothBoundariesAndKeepsCancelledVersionForHistory() throws Exception {
        var first =
                confirm(
                        current,
                        preview(
                                current,
                                change(
                                        current,
                                        ProductPriceChangeRequest.Timing.SCHEDULED,
                                        now().plusSeconds(3600))),
                        UUID.randomUUID(),
                        "change",
                        200);
        current = prices.findById(current.getId()).orElseThrow();
        Instant moved = now().plusSeconds(7200);
        var request =
                new ProductPriceChangeRequest(
                        ProductPriceChangeRequest.Operation.RESCHEDULE,
                        current.getVersion(),
                        null,
                        null,
                        ProductPriceChangeRequest.Timing.SCHEDULED,
                        new BigDecimal("250"),
                        moved,
                        "Move future tariff");
        var second =
                confirm(
                        current,
                        preview(current, request),
                        UUID.randomUUID(),
                        "reschedule-change",
                        200);
        UUID replacementId = UUID.fromString(second.get("successorPrice").get("id").asText());
        assertBoundary(current, replacementId, moved);
        assertThat(
                        prices.findById(
                                        UUID.fromString(
                                                first.get("successorPrice").get("id").asText()))
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(ProductPriceStatus.ARCHIVED);
        assertThat(second.get("successorPrice").get("revisionNumber").asInt()).isEqualTo(3);
    }

    @Test
    void changedIntentAndStaleReviewCannotWriteAnything() throws Exception {
        var review = preview(current, change(current, ProductPriceChangeRequest.Timing.NOW, null));
        var tampered = review.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) tampered.get("change"))
                .put("amount", "1");
        var error = confirm(current, tampered, UUID.randomUUID(), "change", 409);
        assertThat(error.get("code").asText()).isEqualTo("STALE_PRICE_CHANGE_PREVIEW");
        assertThat(prices.findById(current.getId()).orElseThrow().getEffectiveUntil()).isNull();
        confirm(current, review, UUID.randomUUID(), "change", 200);
        assertThat(confirm(current, review, UUID.randomUUID(), "change", 409).get("code").asText())
                .isEqualTo("STALE_PRICE_CHANGE_PREVIEW");
    }

    @Test
    void concurrentConfirmationsWithDifferentKeysHaveOneWinner() throws Exception {
        var review =
                preview(
                        current,
                        change(
                                current,
                                ProductPriceChangeRequest.Timing.SCHEDULED,
                                now().plusSeconds(3600)));
        CountDownLatch start = new CountDownLatch(1);
        var left =
                CompletableFuture.supplyAsync(
                        () -> concurrentConfirm(start, review, UUID.randomUUID()));
        var right =
                CompletableFuture.supplyAsync(
                        () -> concurrentConfirm(start, review, UUID.randomUUID()));
        start.countDown();
        assertThat(List.of(left.join(), right.join())).containsExactlyInAnyOrder(200, 409);
        assertThat(prices.findBySourcePrice_IdAndStatus(current.getId(), ProductPriceStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    void responseLossRetriesCreateOnlyOneSuccessorAndDifferentIntentCannotReuseKey()
            throws Exception {
        var review = preview(current, change(current, ProductPriceChangeRequest.Timing.NOW, null));
        UUID key = UUID.randomUUID();
        var first = confirm(current, review, key, "change", 200);
        var retry = confirm(current, review, key, "change", 200);
        assertThat(retry.get("successorPrice").get("id"))
                .isEqualTo(first.get("successorPrice").get("id"));
        assertThat(retry.get("existingResult").asBoolean()).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) review.get("change"))
                .put("reason", "Different intent");
        confirm(current, review, key, "change", 409);
        assertThat(prices.findBySourcePrice_IdAndStatus(current.getId(), ProductPriceStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    void otherCyclesAndCurrenciesAreUntouched() throws Exception {
        List<UUID> ids =
                transactions.execute(
                        status -> {
                            var owner = plans.findById(current.ownerId()).orElseThrow();
                            var annual =
                                    ProductPrice.draft(
                                            owner,
                                            Money.of(new BigDecimal("1000"), "MAD"),
                                            BillingCycle.YEARLY,
                                            now().minusSeconds(60),
                                            null);
                            var euro =
                                    ProductPrice.draft(
                                            owner,
                                            Money.of(new BigDecimal("10"), "EUR"),
                                            BillingCycle.MONTHLY,
                                            now().minusSeconds(60),
                                            null);
                            annual.activate();
                            euro.activate();
                            prices.saveAllAndFlush(List.of(annual, euro));
                            return List.of(annual.getId(), euro.getId());
                        });
        confirm(
                current,
                preview(current, change(current, ProductPriceChangeRequest.Timing.NOW, null)),
                UUID.randomUUID(),
                "change",
                200);
        assertThat(prices.findAllById(ids))
                .allSatisfy(
                        price -> {
                            assertThat(price.getVersion()).isZero();
                            assertThat(price.getEffectiveUntil()).isNull();
                        });
    }

    @Test
    void legacyPauseAndUnsignedReplacementCannotBypassContinuousFlow() throws Exception {
        mockMvc.perform(
                        post("/api/admin/product-prices/{id}/pause", current.getId())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0,\"reason\":\"Attempt gap\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(
                        post("/api/admin/product-prices/{id}/schedule-replacement", current.getId())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ProductPriceReplacementRequest(
                                                        current.getId(), 0, 0, "Unsigned"))))
                .andExpect(status().isConflict());
        assertThat(prices.findById(current.getId()).orElseThrow().getEffectiveUntil()).isNull();
    }

    @Test
    void finiteDraftCannotBePublishedButDraftCreationDoesNotAffectCurrentSales() throws Exception {
        var draft =
                transactions.execute(
                        status ->
                                prices.saveAndFlush(
                                        ProductPrice.draft(
                                                plans.findById(current.ownerId()).orElseThrow(),
                                                Money.of(new BigDecimal("900"), "MAD"),
                                                BillingCycle.YEARLY,
                                                now(),
                                                now().plusSeconds(3600))));
        var preview =
                objectMapper.readTree(
                        mockMvc.perform(
                                        get(
                                                        "/api/admin/product-prices/{id}/activation-preview",
                                                        draft.getId())
                                                .header("Authorization", bearer(token)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(preview.get("blockers").toString()).contains("CONTINUOUS_PRICE_REQUIRED");
        mockMvc.perform(
                        post("/api/admin/product-prices/{id}/activate", draft.getId())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ProductPriceActivationRequest(
                                                        draft.getVersion(),
                                                        "No standalone end date",
                                                        preview.get("previewToken").asText()))))
                .andExpect(status().isConflict());
        assertThat(prices.findById(current.getId()).orElseThrow().isApplicableAt(now())).isTrue();
    }

    @Test
    void publishedOfferBlocksPriceChangeUntilItsAcceptedSalesWindowEnds() throws Exception {
        Instant offerEnd = now().plusSeconds(1800);
        publishOffer(current, offerEnd);
        var request = change(current, ProductPriceChangeRequest.Timing.NOW, null);
        var blocked = review(current, request);
        assertThat(blocked.get("allowed").asBoolean()).isFalse();
        assertThat(blocked.get("blockingOfferCount").asLong()).isEqualTo(1);
        assertThat(blocked.get("blockers").toString()).contains("PUBLISHED_OFFERS_DEPEND_ON_PRICE");
        confirm(current, blocked, UUID.randomUUID(), "change", 409);
        assertThat(prices.findById(current.getId()).orElseThrow().getEffectiveUntil()).isNull();
        confirm(
                current,
                preview(
                        current,
                        change(current, ProductPriceChangeRequest.Timing.SCHEDULED, offerEnd)),
                UUID.randomUUID(),
                "change",
                200);
    }

    @Test
    void futureOfferCannotBeSilentlyRetargetedByCancellationOrRescheduling() throws Exception {
        var result =
                confirm(
                        current,
                        preview(
                                current,
                                change(
                                        current,
                                        ProductPriceChangeRequest.Timing.SCHEDULED,
                                        now().plusSeconds(3600))),
                        UUID.randomUUID(),
                        "change",
                        200);
        var future =
                prices.findById(UUID.fromString(result.get("successorPrice").get("id").asText()))
                        .orElseThrow();
        current = prices.findById(current.getId()).orElseThrow();
        publishOffer(future, now().plusSeconds(7200));
        for (var operation :
                List.of(
                        ProductPriceChangeRequest.Operation.CANCEL,
                        ProductPriceChangeRequest.Operation.RESCHEDULE)) {
            boolean cancel = operation == ProductPriceChangeRequest.Operation.CANCEL;
            var request =
                    new ProductPriceChangeRequest(
                            operation,
                            current.getVersion(),
                            null,
                            null,
                            cancel ? null : ProductPriceChangeRequest.Timing.SCHEDULED,
                            cancel ? null : BigDecimal.TEN,
                            cancel ? null : now().plusSeconds(5400),
                            "Review offer dependency");
            var blocked = review(current, request);
            assertThat(blocked.get("blockingOfferCount").asLong()).isEqualTo(1);
            confirm(
                    current,
                    blocked,
                    UUID.randomUUID(),
                    cancel ? "cancel-change" : "reschedule-change",
                    409);
        }
        assertBoundary(current, future.getId(), future.getEffectiveFrom());
    }

    @Test
    void newlyPublishedOfferInvalidatesEarlierCleanReview() throws Exception {
        var clean = preview(current, change(current, ProductPriceChangeRequest.Timing.NOW, null));
        publishOffer(current, now().plusSeconds(3600));
        assertThat(confirm(current, clean, UUID.randomUUID(), "change", 409).get("code").asText())
                .isEqualTo("STALE_PRICE_CHANGE_PREVIEW");
        assertThat(prices.findById(current.getId()).orElseThrow().getEffectiveUntil()).isNull();
    }

    @Test
    void elapsedHandoffCannotBeCancelledAndPastScheduledDatesAreRejected() throws Exception {
        var blocked =
                review(
                        current,
                        change(
                                current,
                                ProductPriceChangeRequest.Timing.SCHEDULED,
                                now().minusSeconds(1)));
        assertThat(blocked.get("blockers").toString()).contains("CHANGE_DATE_PASSED");
        confirm(current, blocked, UUID.randomUUID(), "change", 409);
        Instant cutoff = now().plusSeconds(3600);
        var result =
                confirm(
                        current,
                        preview(
                                current,
                                change(
                                        current,
                                        ProductPriceChangeRequest.Timing.SCHEDULED,
                                        cutoff)),
                        UUID.randomUUID(),
                        "change",
                        200);
        var future =
                prices.findById(UUID.fromString(result.get("successorPrice").get("id").asText()))
                        .orElseThrow();
        var previous = prices.findById(current.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> future.cancelBeforeStart(cutoff))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> previous.replaceFutureBoundary(cutoff, null, cutoff))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void auditFailureRollsBackBothWindowsAndReceiptBeforeSafeRetry() throws Exception {
        var review = preview(current, change(current, ProductPriceChangeRequest.Timing.NOW, null));
        UUID key = UUID.randomUUID();
        // Fault injection stays within the existing application context. Permissionizer's
        // global bean resolver cannot safely alternate between independently closed contexts.
        com.hiveapp.shared.audit.AuditTrail target =
                org.mockito.Mockito.spy(
                        (com.hiveapp.shared.audit.AuditTrail)
                                org.springframework.test.util.AopTestUtils.getUltimateTargetObject(
                                        audit));
        Object serviceTarget =
                org.springframework.test.util.AopTestUtils.getUltimateTargetObject(priceService);
        org.mockito.Mockito.doThrow(new IllegalStateException("Simulated audit failure"))
                .when(target)
                .recordSuccess(
                        org.mockito.ArgumentMatchers.eq("platform.price_books.change"),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        org.springframework.test.util.ReflectionTestUtils.setField(
                serviceTarget, "auditTrail", target);
        try {
            confirm(current, review, key, "change", 500);
            assertThat(prices.findById(current.getId()).orElseThrow().getEffectiveUntil()).isNull();
            assertThat(
                            prices.findBySourcePrice_IdAndStatus(
                                    current.getId(), ProductPriceStatus.ACTIVE))
                    .isEmpty();
            assertThat(receipts.findById(key)).isEmpty();
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    serviceTarget, "auditTrail", audit);
        }
        confirm(current, review, key, "change", 200);
    }

    private void publishOffer(ProductPrice price, Instant endsAt) {
        transactions.executeWithoutResult(
                status -> {
                    var owner = admins.findByUser_Email(ADMIN_EMAIL).orElseThrow();
                    String suffix = UUID.randomUUID().toString().replace("-", "");
                    var campaign =
                            CommercialCampaign.draft(
                                    "PRICE_" + suffix,
                                    "Price dependency",
                                    null,
                                    now().minusSeconds(60),
                                    endsAt.plusSeconds(60),
                                    CommercialCampaignSource.MARKETING,
                                    "QA",
                                    owner);
                    campaign.configurePublicAudience();
                    campaigns.saveAndFlush(campaign);
                    var lineage =
                            lineages.saveAndFlush(
                                    CommercialOfferLineage.create(
                                            "OFFER_" + suffix,
                                            campaign,
                                            CommercialOfferDiscovery.CATALOG,
                                            CommercialOfferAcceptance.CLIENT_OR_OPERATOR,
                                            null,
                                            null,
                                            null,
                                            owner));
                    var offer =
                            CommercialOffer.draft(
                                    lineage,
                                    "Pinned price offer",
                                    null,
                                    price.getEffectiveFrom().isAfter(now())
                                            ? price.getEffectiveFrom()
                                            : now().minusSeconds(1),
                                    endsAt,
                                    new CommercialOfferSelection(
                                            price.ownerId(),
                                            price.getId(),
                                            List.of(),
                                            List.of(),
                                            SubscriptionChangeTiming.IMMEDIATE),
                                    new CommercialOfferEffectSnapshot(
                                            CommercialOfferDiscountType.FIXED,
                                            BigDecimal.ONE,
                                            null,
                                            null,
                                            List.of()));
                    offer.publish(now());
                    offers.saveAndFlush(offer);
                    offerIds.add(offer.getId());
                    lineageIds.add(lineage.getId());
                    campaignIds.add(campaign.getId());
                });
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private ProductPriceChangeRequest change(
            ProductPrice price, ProductPriceChangeRequest.Timing timing, Instant cutoff) {
        return new ProductPriceChangeRequest(
                ProductPriceChangeRequest.Operation.CHANGE,
                price.getVersion(),
                null,
                null,
                timing,
                new BigDecimal("200.12"),
                cutoff,
                "Reviewed price change");
    }

    private JsonNode preview(ProductPrice price, ProductPriceChangeRequest request)
            throws Exception {
        var json = review(price, request);
        assertThat(json.get("allowed").asBoolean()).as(json.toString()).isTrue();
        return json;
    }

    private JsonNode review(ProductPrice price, ProductPriceChangeRequest request)
            throws Exception {
        var json =
                objectMapper.readTree(
                        mockMvc.perform(
                                        post(
                                                        "/api/admin/product-prices/{id}/change-preview",
                                                        price.getId())
                                                .header("Authorization", bearer(token))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        return json;
    }

    private JsonNode confirm(
            ProductPrice price, JsonNode preview, UUID key, String endpoint, int expected)
            throws Exception {
        var confirmation =
                Map.of(
                        "change",
                        preview.get("change"),
                        "previewToken",
                        preview.get("previewToken").asText(),
                        "idempotencyKey",
                        key);
        return objectMapper.readTree(
                mockMvc.perform(
                                post("/api/admin/product-prices/{id}/" + endpoint, price.getId())
                                        .header("Authorization", bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(confirmation)))
                        .andExpect(status().is(expected))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    @Test
    void cancellationRacingReschedulingHasOneWinnerAndContinuousCoverage() throws Exception {
        confirm(
                current,
                preview(
                        current,
                        change(
                                current,
                                ProductPriceChangeRequest.Timing.SCHEDULED,
                                now().plusSeconds(3600))),
                UUID.randomUUID(),
                "change",
                200);
        current = prices.findById(current.getId()).orElseThrow();
        var cancel =
                preview(
                        current,
                        new ProductPriceChangeRequest(
                                ProductPriceChangeRequest.Operation.CANCEL,
                                current.getVersion(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                "Cancel before start"));
        var reschedule =
                preview(
                        current,
                        new ProductPriceChangeRequest(
                                ProductPriceChangeRequest.Operation.RESCHEDULE,
                                current.getVersion(),
                                null,
                                null,
                                ProductPriceChangeRequest.Timing.SCHEDULED,
                                BigDecimal.TEN,
                                now().plusSeconds(7200),
                                "Move before start"));
        CountDownLatch start = new CountDownLatch(1);
        var left =
                CompletableFuture.supplyAsync(
                        () -> concurrentConfirm(start, cancel, UUID.randomUUID(), "cancel-change"));
        var right =
                CompletableFuture.supplyAsync(
                        () ->
                                concurrentConfirm(
                                        start, reschedule, UUID.randomUUID(), "reschedule-change"));
        start.countDown();
        assertThat(List.of(left.join(), right.join())).containsExactlyInAnyOrder(200, 409);
        var saved = prices.findById(current.getId()).orElseThrow();
        var children =
                prices.findBySourcePrice_IdAndStatus(current.getId(), ProductPriceStatus.ACTIVE);
        if (saved.getEffectiveUntil() == null) assertThat(children).isEmpty();
        else {
            assertThat(children).hasSize(1);
            assertBoundary(saved, children.getFirst().getId(), saved.getEffectiveUntil());
        }
        assertThat(
                        prices.findApplicable(
                                ProductPriceOwnerType.PLAN,
                                current.ownerId(),
                                "MAD",
                                BillingCycle.MONTHLY,
                                now().plusSeconds(8000)))
                .hasSize(1);
    }

    private int concurrentConfirm(CountDownLatch start, JsonNode review, UUID key) {
        return concurrentConfirm(start, review, key, "change");
    }

    private int concurrentConfirm(
            CountDownLatch start, JsonNode review, UUID key, String endpoint) {
        try {
            start.await();
            return mockMvc.perform(
                            post("/api/admin/product-prices/{id}/" + endpoint, current.getId())
                                    .header("Authorization", bearer(token))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(
                                                    Map.of(
                                                            "change",
                                                            review.get("change"),
                                                            "previewToken",
                                                            review.get("previewToken").asText(),
                                                            "idempotencyKey",
                                                            key))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void assertBoundary(ProductPrice previous, UUID next, Instant cutoff) {
        assertThat(prices.findById(previous.getId()).orElseThrow().getEffectiveUntil())
                .isEqualTo(cutoff);
        assertThat(prices.findById(next).orElseThrow().getEffectiveFrom()).isEqualTo(cutoff);
        assertThat(
                        prices.findApplicable(
                                ProductPriceOwnerType.PLAN,
                                previous.ownerId(),
                                "MAD",
                                BillingCycle.MONTHLY,
                                cutoff.minusNanos(1)))
                .extracting(ProductPrice::getId)
                .containsExactly(previous.getId());
        assertThat(
                        prices.findApplicable(
                                ProductPriceOwnerType.PLAN,
                                previous.ownerId(),
                                "MAD",
                                BillingCycle.MONTHLY,
                                cutoff))
                .extracting(ProductPrice::getId)
                .containsExactly(next);
    }
}
