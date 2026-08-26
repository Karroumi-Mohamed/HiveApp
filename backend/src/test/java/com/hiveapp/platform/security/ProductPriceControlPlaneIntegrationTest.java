package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.infrastructure.ProductPriceBackfill;
import com.hiveapp.platform.client.plan.dto.CreateProductPriceRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceVersionRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductPriceControlPlaneIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private PlanRepository planRepository;
    @Autowired private AddOnRepository addOnRepository;
    @Autowired private QuotaPackageRepository quotaPackageRepository;
    @Autowired private ProductPriceRepository productPriceRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private ProductPriceBackfill productPriceBackfill;

    @Test
    void compatibilityBackfillIsIdempotentForEverySeededProductTuple() {
        long before = productPriceRepository.count();
        productPriceBackfill.backfill();
        assertThat(productPriceRepository.count()).isEqualTo(before);

        var free = planRepository.findByCode("FREE").orElseThrow();
        var addOn = addOnRepository.findByCode("ORGANIZATION_TOOLS").orElseThrow();
        var quotaPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
        assertThat(productPriceRepository.countCompatibilityPrice(
                ProductPriceOwnerType.PLAN, free.getId(), "USD", BillingCycle.MONTHLY)).isEqualTo(1);
        assertThat(productPriceRepository.countCompatibilityPrice(
                ProductPriceOwnerType.ADD_ON, addOn.getId(), "USD", BillingCycle.MONTHLY)).isEqualTo(1);
        assertThat(productPriceRepository.countCompatibilityPrice(
                ProductPriceOwnerType.QUOTA_PACKAGE, quotaPackage.getId(), "USD", BillingCycle.MONTHLY)).isEqualTo(1);
    }

    @Test
    void supportsIndependentAnnualPriceLifecycleAndStableConflictCodes() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createActivePlan(adminToken);
        Instant starts = Instant.now().minusSeconds(30);

        mockMvc.perform(post("/api/admin/product-prices")
                        .header("Authorization", bearer(adminToken))
                        .param("ownerId", planId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductPriceRequest(
                                BigDecimal.ZERO, "USD", BillingCycle.YEARLY, starts, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        JsonNode annualDraft = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("120.00"), BillingCycle.YEARLY, starts, null);
        UUID annualId = UUID.fromString(annualDraft.get("id").asText());
        String productName = planRepository.findById(planId).orElseThrow().getName();
        assertThat(annualDraft.get("productName").asText()).isEqualTo(productName);

        String searchResponse = mockMvc.perform(get("/api/admin/product-prices")
                        .header("Authorization", bearer(adminToken))
                        .param("search", productName))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(searchResponse).get("content").findValuesAsText("id"))
                .contains(annualId.toString());

        mockMvc.perform(get("/api/admin/product-prices/{id}/activation-preview", annualId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activatable").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());

        mockMvc.perform(post("/api/admin/product-prices/{id}/activate", annualId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + annualDraft.get("version").asLong() + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        JsonNode annual = activate(adminToken, annualId, annualDraft.get("version").asLong());
        assertThat(annual.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(annual.get("amount").decimalValue()).isEqualByComparingTo("120.00");

        mockMvc.perform(get("/api/admin/product-prices/{id}/history", annualId)
                        .header("Authorization", bearer(adminToken))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content[0].actorUserId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].actorEmail").isNotEmpty())
                .andExpect(jsonPath("$.content[0].action").value("platform.price_books.activate"))
                .andExpect(jsonPath("$.content[0].outcome").value("SUCCEEDED"))
                .andExpect(jsonPath("$.content[0].reason").value("Activate price"))
                .andExpect(jsonPath("$.content[0].occurredAt").isNotEmpty());

        JsonNode overlap = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("99.00"), BillingCycle.YEARLY, starts.plusSeconds(1), null);
        mockMvc.perform(put("/api/admin/product-prices/{id}", overlap.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":99,"currencyCode":"USD","billingCycle":"YEARLY",
                                 "effectiveFrom":"2026-01-01T00:00:00Z","version":99}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        mockMvc.perform(post("/api/admin/product-prices/{id}/activate", overlap.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceVersionRequest(
                                        overlap.get("version").asLong(), "Publish overlapping annual price"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_ENTRY_OVERLAP"));
        mockMvc.perform(delete("/api/admin/product-prices/{id}", overlap.get("id").asText())
                        .param("version", String.valueOf(overlap.get("version").asLong()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/admin/product-prices/{id}", annualId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":121,"currencyCode":"USD","billingCycle":"YEARLY",
                                 "effectiveFrom":"2026-01-01T00:00:00Z","version":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        JsonNode paused = pause(adminToken, annualId, annual.get("version").asLong());
        JsonNode revision = revise(adminToken, annualId, paused.get("version").asLong());
        assertThat(revision.get("status").asText()).isEqualTo("DRAFT");
        assertThat(revision.get("sourcePriceId").asText()).isEqualTo(annualId.toString());
        assertThat(revision.get("revisionNumber").asInt()).isEqualTo(2);

        mockMvc.perform(get("/api/admin/product-prices/{id}", annualId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableActions", hasItem("REACTIVATE")))
                .andExpect(jsonPath("$.availableActions", not(hasItem("REVISE"))))
                .andExpect(jsonPath("$.blockers", hasItem("SUCCESSOR_ALREADY_EXISTS")));

        JsonNode reactivated = lifecycle(adminToken, annualId, paused.get("version").asLong(), "reactivate");
        JsonNode pausedAgain = pause(adminToken, annualId, reactivated.get("version").asLong());
        JsonNode archived = lifecycle(adminToken, annualId, pausedAgain.get("version").asLong(), "archive");
        assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
        mockMvc.perform(post("/api/admin/product-prices/{id}/reactivate", annualId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceVersionRequest(
                                        archived.get("version").asLong(), "Attempt archived reactivation"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mockMvc.perform(delete("/api/admin/product-prices/{id}", revision.get("id").asText())
                        .param("version", String.valueOf(revision.get("version").asLong()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        var monthly = productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, planId, "USD", BillingCycle.MONTHLY, Instant.now());
        assertThat(monthly).singleElement().satisfies(price -> {
            assertThat(price.isCompatibilityDefault()).isTrue();
            assertThat(price.getAmount()).isZero();
        });
    }

    @Test
    void exactPlanSelectionResolvesMatchingItemsAndWritesImmutableV2Snapshot() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var plan = planRepository.findByCode("FLEX").orElseThrow();
        var addOn = addOnRepository.findByCode("ORGANIZATION_TOOLS").orElseThrow();
        var quotaPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
        Instant starts = Instant.now().minusSeconds(30);

        JsonNode planPrice = activateNewPrice(adminToken, ProductPriceOwnerType.PLAN, plan.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, starts);
        JsonNode addOnPrice = activateNewPrice(adminToken, ProductPriceOwnerType.ADD_ON, addOn.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, starts);
        JsonNode packagePrice = activateNewPrice(adminToken, ProductPriceOwnerType.QUOTA_PACKAGE,
                quotaPackage.getId(), BigDecimal.ZERO, BillingCycle.YEARLY, starts);

        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                plan.getCode(), Set.of(addOn.getCode()),
                List.of(new QuotaPackageSelection(quotaPackage.getCode(), 1)),
                SubscriptionChangeTiming.IMMEDIATE,
                new ProductPriceSelectionRequest(
                        UUID.fromString(planPrice.get("id").asText()), "USD", BillingCycle.YEARLY));

        mockMvc.perform(post("/api/v1/subscriptions/apply")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operation.status").value("APPLIED"))
                .andExpect(jsonPath("$.preview.previewPrice").value(0));

        var subscription = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        var snapshot = subscription.getEntitlementSnapshot();
        assertThat(snapshot.schemaVersion()).isEqualTo(2);
        assertThat(snapshot.billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(snapshot.planPriceEntryId()).isEqualTo(UUID.fromString(planPrice.get("id").asText()));
        assertThat(snapshot.addOns()).singleElement()
                .extracting(item -> item.priceEntryId())
                .isEqualTo(UUID.fromString(addOnPrice.get("id").asText()));
        assertThat(snapshot.quotaPackages()).singleElement()
                .extracting(item -> item.priceEntryId())
                .isEqualTo(UUID.fromString(packagePrice.get("id").asText()));

        pause(adminToken, UUID.fromString(planPrice.get("id").asText()), planPrice.get("version").asLong());
        pause(adminToken, UUID.fromString(addOnPrice.get("id").asText()), addOnPrice.get("version").asLong());
        pause(adminToken, UUID.fromString(packagePrice.get("id").asText()), packagePrice.get("version").asLong());

        mockMvc.perform(patch("/api/admin/subscriptions/account/{accountId}/overrides", accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSubscriptionOverridesRequest(
                                Set.of(addOn.getCode()),
                                List.of(new QuotaPackageSelection(quotaPackage.getCode(), 1))))))
                .andExpect(status().isOk());

        var unchanged = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                .getEntitlementSnapshot();
        assertThat(unchanged).isEqualTo(snapshot);
        String catalog = mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(catalog).doesNotContain(planPrice.get("id").asText());
        assertThat(catalog).doesNotContain(addOnPrice.get("id").asText());
        assertThat(catalog).doesNotContain(packagePrice.get("id").asText());
    }

    @Test
    void concurrentOverlappingActivationsPublishExactlyOneEntry() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createActivePlan(adminToken);
        Instant starts = Instant.now().minusSeconds(30);
        JsonNode first = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("10.00"), BillingCycle.YEARLY, starts, null);
        JsonNode second = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("11.00"), BillingCycle.YEARLY, starts, null);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<HttpResult> left = CompletableFuture.supplyAsync(
                () -> activateAfter(start, adminToken, first));
        CompletableFuture<HttpResult> right = CompletableFuture.supplyAsync(
                () -> activateAfter(start, adminToken, second));
        start.countDown();

        List<HttpResult> results = List.of(left.join(), right.join());
        assertThat(results).extracting(HttpResult::status).containsExactlyInAnyOrder(200, 409);
        assertThat(results.stream().filter(result -> result.status() == 409).findFirst().orElseThrow().body())
                .contains("PRICE_ENTRY_OVERLAP");
        assertThat(productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, planId, "USD", BillingCycle.YEARLY, Instant.now()))
                .hasSize(1);
    }

    @Test
    void halfOpenWindowsAllowAdjacentSchedulingAndRejectExpiredActivation() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createActivePlan(adminToken);
        Instant now = Instant.now();
        Instant boundary = now.plusSeconds(3600);

        JsonNode current = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("20.00"), BillingCycle.YEARLY, now.minusSeconds(60), boundary);
        activate(adminToken, UUID.fromString(current.get("id").asText()), current.get("version").asLong());
        JsonNode successor = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("25.00"), BillingCycle.YEARLY, boundary, boundary.plusSeconds(3600));
        activate(adminToken, UUID.fromString(successor.get("id").asText()),
                successor.get("version").asLong());

        JsonNode expired = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("15.00"), BillingCycle.YEARLY,
                now.minusSeconds(7200), now.minusSeconds(3600));
        mockMvc.perform(get("/api/admin/product-prices/{id}/activation-preview", expired.get("id").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activatable").value(false))
                .andExpect(jsonPath("$.blockers[0]").value("EFFECTIVE_WINDOW_EXPIRED"));
        mockMvc.perform(post("/api/admin/product-prices/{id}/activate", expired.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceVersionRequest(
                                        expired.get("version").asLong(), "Attempt expired activation"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void priceIdentityIsBoundToTheExactProductRevision() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID sourceId = createActivePlan(adminToken);
        var source = planRepository.findById(sourceId).orElseThrow();
        UUID sourcePriceId = productPriceRepository.findApplicable(
                        ProductPriceOwnerType.PLAN, sourceId, "USD", BillingCycle.MONTHLY, Instant.now())
                .getFirst().getId();

        PlanBranchRequest branch = new PlanBranchRequest(
                "Exact revision " + UUID.randomUUID(), null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY);
        String revisionResponse = mockMvc.perform(post("/api/admin/plans/{id}/revisions", sourceId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branch)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode revisionJson = objectMapper.readTree(revisionResponse);
        UUID revisionId = UUID.fromString(revisionJson.get("id").asText());
        mockMvc.perform(patch("/api/admin/plans/{id}/status", revisionId)
                        .param("status", "ACTIVE")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        var revision = planRepository.findById(revisionId).orElseThrow();
        assertThat(revision.getLineageId()).isEqualTo(source.getLineageId());
        assertThat(productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, revisionId, "USD", BillingCycle.MONTHLY, Instant.now()))
                .singleElement().satisfies(price -> assertThat(price.getId()).isNotEqualTo(sourcePriceId));

        String clientToken = registerClientAndGetToken();
        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                revision.getCode(), Set.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE,
                new ProductPriceSelectionRequest(sourcePriceId, "USD", BillingCycle.MONTHLY));
        mockMvc.perform(post("/api/v1/subscriptions/preview")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private HttpResult activateAfter(CountDownLatch start, String token, JsonNode draft) {
        try {
            start.await();
            var response = mockMvc.perform(post("/api/admin/product-prices/{id}/activate", draft.get("id").asText())
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ProductPriceVersionRequest(
                                            draft.get("version").asLong(), "Concurrent price activation"))))
                    .andReturn().getResponse();
            return new HttpResult(response.getStatus(), response.getContentAsString());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private UUID createActivePlan(String token) throws Exception {
        var source = planRepository.findByCode("FREE").orElseThrow();
        PlanBranchRequest request = new PlanBranchRequest(
                "Price fixture " + UUID.randomUUID(), null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY);
        String response = mockMvc.perform(post("/api/admin/plans/{id}/duplicate", source.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        mockMvc.perform(patch("/api/admin/plans/{id}/status", id)
                        .param("status", "ACTIVE")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        return id;
    }

    private JsonNode activateNewPrice(String token, ProductPriceOwnerType type, UUID ownerId,
                                      BigDecimal amount, BillingCycle cycle, Instant starts) throws Exception {
        JsonNode draft = createPrice(token, type, ownerId, amount, cycle, starts, null);
        return activate(token, UUID.fromString(draft.get("id").asText()), draft.get("version").asLong());
    }

    private JsonNode createPrice(String token, ProductPriceOwnerType type, UUID ownerId,
                                 BigDecimal amount, BillingCycle cycle, Instant starts,
                                 Instant until) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices")
                        .header("Authorization", bearer(token))
                        .param("ownerType", type.name())
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateProductPriceRequest(amount, "USD", cycle, starts, until))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode activate(String token, UUID priceId, long version) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices/{id}/activate", priceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceVersionRequest(version, "Activate price"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode pause(String token, UUID priceId, long version) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices/{id}/pause", priceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceVersionRequest(version, "Pause price"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode revise(String token, UUID priceId, long version) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices/{id}/revisions", priceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceVersionRequest(version, "Create successor price revision"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode lifecycle(String token, UUID priceId, long version, String action) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices/{id}/{action}", priceId, action)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceVersionRequest(version, "Price lifecycle " + action))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID currentAccountId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private record HttpResult(int status, String body) {}
}
