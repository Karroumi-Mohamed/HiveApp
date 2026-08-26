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
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementPreviewRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceVersionRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.platform.client.plan.dto.UpdateProductPriceRequest;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
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
    @Autowired private AuditLogRepository auditLogRepository;

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
        assertThat(new BigDecimal(annual.get("amount").asText())).isEqualByComparingTo("120.00");

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

        var planPrice = ensureActivePrice(adminToken, ProductPriceOwnerType.PLAN, plan.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, starts);
        var addOnPrice = ensureActivePrice(adminToken, ProductPriceOwnerType.ADD_ON, addOn.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, starts);
        var packagePrice = ensureActivePrice(adminToken, ProductPriceOwnerType.QUOTA_PACKAGE,
                quotaPackage.getId(), BigDecimal.ZERO, BillingCycle.YEARLY, starts);

        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                plan.getCode(), Set.of(addOn.getCode()),
                List.of(new QuotaPackageSelection(quotaPackage.getCode(), 1)),
                SubscriptionChangeTiming.IMMEDIATE,
                new ProductPriceSelectionRequest(
                        planPrice.getId(), "USD", BillingCycle.YEARLY));

        mockMvc.perform(post("/api/v1/subscriptions/apply")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operation.status").value("APPLIED"))
                .andExpect(jsonPath("$.preview.previewPrice").value("0.00"));

        var subscription = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        var snapshot = subscription.getEntitlementSnapshot();
        assertThat(snapshot.schemaVersion()).isEqualTo(2);
        assertThat(snapshot.billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(snapshot.planPriceEntryId()).isEqualTo(planPrice.getId());
        assertThat(snapshot.addOns()).singleElement()
                .extracting(item -> item.priceEntryId())
                .isEqualTo(addOnPrice.getId());
        assertThat(snapshot.quotaPackages()).singleElement()
                .extracting(item -> item.priceEntryId())
                .isEqualTo(packagePrice.getId());

        pause(adminToken, planPrice.getId(), planPrice.getVersion());
        pause(adminToken, addOnPrice.getId(), addOnPrice.getVersion());
        pause(adminToken, packagePrice.getId(), packagePrice.getVersion());

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
        JsonNode catalogJson = objectMapper.readTree(catalog);
        assertThat(catalogJson.path("currentSubscription").path("planPriceEntryId").asText())
                .isEqualTo(planPrice.getId().toString());
        String selectableProducts = catalogJson.path("plans").toString();
        assertThat(selectableProducts).doesNotContain(planPrice.getId().toString());
        assertThat(selectableProducts).doesNotContain(addOnPrice.getId().toString());
        assertThat(selectableProducts).doesNotContain(packagePrice.getId().toString());
    }

    @Test
    void samePlanMayChangeToAnotherExactBillingCyclePrice() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var free = planRepository.findByCode("FLEX").orElseThrow();
        var addOn = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        var quotaPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
        var annualPrice = ensureActivePrice(
                adminToken,
                ProductPriceOwnerType.PLAN,
                free.getId(),
                BigDecimal.ZERO,
                BillingCycle.YEARLY,
                Instant.now().minusSeconds(30));
        var annualAddOnPrice = ensureActivePrice(
                adminToken, ProductPriceOwnerType.ADD_ON, addOn.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, Instant.now().minusSeconds(30));
        var annualPackagePrice = ensureActivePrice(
                adminToken, ProductPriceOwnerType.QUOTA_PACKAGE, quotaPackage.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, Instant.now().minusSeconds(30));

        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        var monthlyPlanPrice = productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, free.getId(), "USD", BillingCycle.MONTHLY, Instant.now())
                .getFirst();
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}", accountId)
                        .param("planCode", free.getCode())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductPriceSelectionRequest(
                                monthlyPlanPrice.getId(), "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated());
        var before = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        assertThat(before.getEntitlementSnapshot().billingCycle()).isEqualTo(BillingCycle.MONTHLY);
        mockMvc.perform(patch("/api/admin/subscriptions/account/{accountId}/overrides", accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSubscriptionOverridesRequest(
                                Set.of(addOn.getCode()),
                                List.of(new QuotaPackageSelection(quotaPackage.getCode(), 1))))))
                .andExpect(status().isOk());
        var monthlySnapshot = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                .getEntitlementSnapshot();

        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                free.getCode(), Set.of(addOn.getCode()),
                List.of(new QuotaPackageSelection(quotaPackage.getCode(), 1)),
                SubscriptionChangeTiming.IMMEDIATE,
                new ProductPriceSelectionRequest(
                        annualPrice.getId(), "USD", BillingCycle.YEARLY));

        mockMvc.perform(post("/api/v1/subscriptions/apply")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operation.status").value("APPLIED"));

        var changed = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        assertThat(changed.getId()).isNotEqualTo(before.getId());
        assertThat(changed.getEntitlementSnapshot().billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(changed.getEntitlementSnapshot().planPriceEntryId())
                .isEqualTo(annualPrice.getId());
        assertThat(changed.getEntitlementSnapshot().addOns()).singleElement()
                .satisfies(item -> {
                    assertThat(item.billingCycle()).isEqualTo(BillingCycle.YEARLY);
                    assertThat(item.priceEntryId()).isEqualTo(annualAddOnPrice.getId());
                    assertThat(item.priceEntryId()).isNotEqualTo(
                            monthlySnapshot.addOns().getFirst().priceEntryId());
                });
        assertThat(changed.getEntitlementSnapshot().quotaPackages()).singleElement()
                .satisfies(item -> {
                    assertThat(item.billingCycle()).isEqualTo(BillingCycle.YEARLY);
                    assertThat(item.priceEntryId()).isEqualTo(annualPackagePrice.getId());
                    assertThat(item.priceEntryId()).isNotEqualTo(
                            monthlySnapshot.quotaPackages().getFirst().priceEntryId());
                });

        mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSubscription.planPriceEntryId")
                        .value(annualPrice.getId().toString()))
                .andExpect(jsonPath("$.currentSubscription.billingCycle").value("YEARLY"));
    }

    @Test
    void adminCreateAndTrialSnapshotExactAnnualPricesAndRejectWrongOwnerIds() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var pro = planRepository.findByCode("PRO").orElseThrow();
        var enterprise = planRepository.findByCode("ENTERPRISE").orElseThrow();
        Instant starts = Instant.now().minusSeconds(30);
        JsonNode proAnnual = activateNewPrice(adminToken, ProductPriceOwnerType.PLAN, pro.getId(),
                new BigDecimal("240.00"), "EUR", BillingCycle.YEARLY, starts);
        JsonNode enterpriseAnnual = activateNewPrice(
                adminToken, ProductPriceOwnerType.PLAN, enterprise.getId(),
                new BigDecimal("480.00"), BillingCycle.YEARLY, starts);
        ProductPriceSelectionRequest proSelection = new ProductPriceSelectionRequest(
                UUID.fromString(proAnnual.get("id").asText()), "EUR", BillingCycle.YEARLY);

        String subscriberToken = registerClientAndGetToken();
        UUID subscriberAccountId = currentAccountId(subscriberToken);
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}", subscriberAccountId)
                        .param("planCode", pro.getCode())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proSelection)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentPrice").value("240.00"));
        var annualSubscription = subscriptionRepository.findActiveByAccountId(subscriberAccountId).orElseThrow();
        assertThat(annualSubscription.getEntitlementSnapshot().planPriceEntryId())
                .isEqualTo(proSelection.priceEntryId());
        assertThat(annualSubscription.getEntitlementSnapshot().billingCycle())
                .isEqualTo(BillingCycle.YEARLY);
        assertThat(annualSubscription.getCurrentPriceCurrencyCode()).isEqualTo("EUR");

        String trialToken = registerClientAndGetToken();
        UUID trialAccountId = currentAccountId(trialToken);
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}/trial", trialAccountId)
                        .param("planCode", pro.getCode())
                        .param("trialDays", "14")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proSelection)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TRIALING"))
                .andExpect(jsonPath("$.currentPrice").value("0.00"));
        var annualTrial = subscriptionRepository.findByAccountIdAndStatus(
                trialAccountId,
                com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus.TRIALING).orElseThrow();
        assertThat(annualTrial.getEntitlementSnapshot().planPriceEntryId())
                .isEqualTo(proSelection.priceEntryId());
        assertThat(annualTrial.getEntitlementSnapshot().billingCycle())
                .isEqualTo(BillingCycle.YEARLY);
        assertThat(annualTrial.getCurrentPriceCurrencyCode()).isEqualTo("EUR");

        String protectedToken = registerClientAndGetToken();
        UUID protectedAccountId = currentAccountId(protectedToken);
        var protectedBefore = subscriptionRepository.findActiveByAccountId(protectedAccountId).orElseThrow();
        ProductPriceSelectionRequest wrongOwner = new ProductPriceSelectionRequest(
                UUID.fromString(enterpriseAnnual.get("id").asText()), "USD", BillingCycle.YEARLY);
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}", protectedAccountId)
                        .param("planCode", pro.getCode())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongOwner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        var protectedAfter = subscriptionRepository.findActiveByAccountId(protectedAccountId).orElseThrow();
        assertThat(protectedAfter.getId()).isEqualTo(protectedBefore.getId());
        assertThat(protectedAfter.getEntitlementSnapshot()).isEqualTo(protectedBefore.getEntitlementSnapshot());
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
    void atomicallySchedulesIndefinitePriceReplacementAndRetriesIdempotently() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var free = planRepository.findByCode("FREE").orElseThrow();
        var current = productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, free.getId(), "USD", BillingCycle.MONTHLY, Instant.now())
                .getFirst();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        var subscriptionBefore = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        var snapshotBefore = subscriptionBefore.getEntitlementSnapshot();
        assertThat(snapshotBefore.planPriceEntryId()).isEqualTo(current.getId());

        JsonNode successorDraft = revise(adminToken, current.getId(), current.getVersion());
        UUID successorId = UUID.fromString(successorDraft.get("id").asText());
        Instant cutoff = Instant.now().plusSeconds(7200);
        String updatedBody = mockMvc.perform(put("/api/admin/product-prices/{id}", successorId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductPriceRequest(
                                new BigDecimal("1.00"), "USD", BillingCycle.MONTHLY,
                                cutoff, null, successorDraft.get("version").asLong()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode successor = objectMapper.readTree(updatedBody);
        ProductPriceReplacementRequest request = new ProductPriceReplacementRequest(
                current.getId(), current.getVersion(), successor.get("version").asLong(),
                "Schedule the next monthly price");

        mockMvc.perform(post("/api/admin/product-prices/{id}/replacement-preview", successorId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductPriceReplacementPreviewRequest(
                                        current.getId(), current.getVersion(),
                                        successor.get("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedulable").value(true))
                .andExpect(jsonPath("$.cutoff").value(cutoff.toString()))
                .andExpect(jsonPath("$.blockers").isEmpty());

        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<HttpResult> left = CompletableFuture.supplyAsync(
                () -> scheduleReplacementAfter(start, adminToken, successorId, request));
        CompletableFuture<HttpResult> right = CompletableFuture.supplyAsync(
                () -> scheduleReplacementAfter(start, adminToken, successorId, request));
        start.countDown();
        List<HttpResult> results = List.of(left.join(), right.join());
        assertThat(results).extracting(HttpResult::status).containsOnly(200);
        assertThat(results.stream()
                .map(HttpResult::body)
                .map(body -> {
                    try {
                        return objectMapper.readTree(body).get("existingResult").asBoolean();
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }).toList()).containsExactlyInAnyOrder(false, true);

        var bounded = productPriceRepository.findById(current.getId()).orElseThrow();
        var scheduled = productPriceRepository.findById(successorId).orElseThrow();
        assertThat(bounded.getEffectiveUntil()).isEqualTo(cutoff);
        assertThat(scheduled.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, free.getId(), "USD", BillingCycle.MONTHLY,
                cutoff.minusNanos(1))).singleElement()
                .extracting(price -> price.getId()).isEqualTo(current.getId());
        assertThat(productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, free.getId(), "USD", BillingCycle.MONTHLY,
                cutoff)).singleElement()
                .extracting(price -> price.getId()).isEqualTo(successorId);

        mockMvc.perform(post("/api/admin/product-prices/{id}/schedule-replacement", successorId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingResult").value(true));

        mockMvc.perform(get("/api/admin/product-prices/{id}/history", current.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action")
                        .value("platform.price_books.schedule_replacement"))
                .andExpect(jsonPath("$.content[0].reason").value("Schedule the next monthly price"));

        mockMvc.perform(get("/api/admin/product-prices/{id}/history", successorId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action")
                        .value("platform.price_books.schedule_replacement"))
                .andExpect(jsonPath("$.content[0].reason").value("Schedule the next monthly price"));
        var successorHistory = auditLogRepository
                .findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc(
                        "PRODUCT_PRICE_ADMIN", successorId.toString()).stream()
                .filter(log -> "platform.price_books.schedule_replacement".equals(log.getAction()))
                .toList();
        assertThat(successorHistory).hasSize(3);
        assertThat(successorHistory).anySatisfy(log -> assertThat(log.getResultData())
                .contains("\"existingResult\":false"));
        assertThat(successorHistory).anySatisfy(log -> assertThat(log.getResultData())
                .contains("\"existingResult\":true"));

        var subscriptionAfter = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        assertThat(subscriptionAfter.getId()).isEqualTo(subscriptionBefore.getId());
        assertThat(subscriptionAfter.getEntitlementSnapshot()).isEqualTo(snapshotBefore);
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

    private HttpResult scheduleReplacementAfter(
            CountDownLatch start,
            String token,
            UUID successorId,
            ProductPriceReplacementRequest request
    ) {
        try {
            start.await();
            var response = mockMvc.perform(post(
                                    "/api/admin/product-prices/{id}/schedule-replacement", successorId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
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
        return activateNewPrice(token, type, ownerId, amount, "USD", cycle, starts);
    }

    private com.hiveapp.platform.client.plan.domain.entity.ProductPrice ensureActivePrice(
            String token,
            ProductPriceOwnerType type,
            UUID ownerId,
            BigDecimal amount,
            BillingCycle cycle,
            Instant starts
    ) throws Exception {
        var existing = productPriceRepository.findApplicable(
                type, ownerId, "USD", cycle, Instant.now());
        if (!existing.isEmpty()) return existing.getFirst();
        JsonNode activated = activateNewPrice(token, type, ownerId, amount, cycle, starts);
        return productPriceRepository.findById(
                UUID.fromString(activated.get("id").asText())).orElseThrow();
    }

    private JsonNode activateNewPrice(String token, ProductPriceOwnerType type, UUID ownerId,
                                      BigDecimal amount, String currencyCode,
                                      BillingCycle cycle, Instant starts) throws Exception {
        JsonNode draft = createPrice(token, type, ownerId, amount, currencyCode, cycle, starts, null);
        return activate(token, UUID.fromString(draft.get("id").asText()), draft.get("version").asLong());
    }

    private JsonNode createPrice(String token, ProductPriceOwnerType type, UUID ownerId,
                                 BigDecimal amount, BillingCycle cycle, Instant starts,
                                 Instant until) throws Exception {
        return createPrice(token, type, ownerId, amount, "USD", cycle, starts, until);
    }

    private JsonNode createPrice(String token, ProductPriceOwnerType type, UUID ownerId,
                                 BigDecimal amount, String currencyCode, BillingCycle cycle,
                                 Instant starts, Instant until) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices")
                        .header("Authorization", bearer(token))
                        .param("ownerType", type.name())
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateProductPriceRequest(amount, currencyCode, cycle, starts, until))))
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
