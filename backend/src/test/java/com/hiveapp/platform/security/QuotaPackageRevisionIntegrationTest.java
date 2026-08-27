package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.ProductPriceVersionRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceActivationRequest;
import com.hiveapp.platform.client.plan.dto.UpdateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotaPackageRevisionIntegrationTest extends PlatformShellIntegrationTestSupport {

    private final List<UUID> packageIds = new ArrayList<>();

    @Autowired
    private QuotaPackageRepository quotaPackageRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @AfterEach
    void removePackages() {
        List<QuotaPackage> packages = packageIds.stream()
                .distinct()
                .map(quotaPackageRepository::findById)
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt(QuotaPackage::getRevisionNumber).reversed())
                .toList();
        for (QuotaPackage item : packages) {
            List<ProductPrice> prices = productPriceRepository.findAllByQuotaPackageId(item.getId());
            if (!prices.isEmpty()) {
                productPriceRepository.deleteAllInBatch(prices);
                productPriceRepository.flush();
            }
            quotaPackageRepository.delete(item);
            quotaPackageRepository.flush();
        }
    }

    @Test
    void successorCopiesReviewedPricesAndPublishesWithoutChangingSourceOrSnapshot() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode sourceCreated = createPackage(token, Set.of("FLEX"), Set.of());
        UUID sourceId = id(sourceCreated);
        JsonNode sourceActivated = activate(token, sourceId);
        QuotaPackage sourceBefore = quotaPackageRepository.findDetailedById(sourceId).orElseThrow();
        ProductPrice sourcePriceBefore = onlyPrice(sourceId);

        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        mockMvc.perform(post("/api/admin/subscriptions/account/{id}", accountId)
                        .param("planCode", "FLEX")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/admin/subscriptions/account/{id}/overrides", accountId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSubscriptionOverridesRequest(
                                Set.of(), List.of(new QuotaPackageSelection(
                                        sourceBefore.getCode(), 1))))))
                .andExpect(status().isOk());
        var snapshotBefore = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                .getEntitlementSnapshot();
        assertThat(snapshotBefore.quotaPackages()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(sourceBefore.getCode());
            assertThat(item.priceEntryId()).isEqualTo(sourcePriceBefore.getId());
        });

        JsonNode revision = responseJson(mockMvc.perform(
                        post("/api/admin/quota-packages/{id}/revisions", sourceId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "expectedVersion", sourceActivated.get("version").asLong(),
                                        "reason", "Increase member capacity after review"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.successor.status").value("DRAFT"))
                .andExpect(jsonPath("$.successor.revisionNumber").value(2))
                .andExpect(jsonPath("$.successor.sourceQuotaPackageId").value(sourceId.toString()))
                .andExpect(jsonPath("$.successor.creationReason").value("REVISED"))
                .andExpect(jsonPath("$.copiedPriceDrafts.length()").value(1))
                .andExpect(jsonPath("$.copiedPriceDrafts[0].amount").value("8.12")));
        JsonNode successor = revision.get("successor");
        UUID successorId = id(successor);
        packageIds.add(successorId);

        QuotaPackage copied = quotaPackageRepository.findDetailedById(successorId).orElseThrow();
        ProductPrice copiedPrice = onlyPrice(successorId);
        assertThat(copied.getLineageId()).isEqualTo(sourceBefore.getLineageId());
        assertThat(copied.getCode()).isNotEqualTo(sourceBefore.getCode());
        assertThat(copied.getFeature().getCode()).isEqualTo(sourceBefore.getFeature().getCode());
        assertThat(copied.getResource()).isEqualTo(sourceBefore.getResource());
        assertThat(copied.getAllowedPlanCodes()).isEqualTo(sourceBefore.getAllowedPlanCodes());
        assertThat(copied.getSalesVisibility()).isEqualTo(sourceBefore.getSalesVisibility());
        assertThat(copiedPrice.getOwnerType()).isEqualTo(ProductPriceOwnerType.QUOTA_PACKAGE);
        assertThat(copiedPrice.ownerId()).isEqualTo(successorId);
        assertThat(copiedPrice.getStatus()).isEqualTo(ProductPriceStatus.DRAFT);
        assertThat(copiedPrice.money()).isEqualTo(sourcePriceBefore.money());
        assertThat(copiedPrice.getLineageId()).isNotEqualTo(sourcePriceBefore.getLineageId());
        assertThat(copiedPrice.getSourcePrice()).isNull();
        assertThat(copiedPrice.isCompatibilityDefault()).isEqualTo(sourcePriceBefore.isCompatibilityDefault());
        assertThat(quotaPackageRepository.findById(sourceId).orElseThrow().getStatus())
                .isEqualTo(QuotaPackageStatus.ACTIVE);
        assertThat(productPriceRepository.findById(sourcePriceBefore.getId()).orElseThrow().getVersion())
                .isEqualTo(sourcePriceBefore.getVersion());

        long editableVersion = successor.get("version").asLong();
        UpdateQuotaPackageRequest update = updateRequest(copied, 3, editableVersion);
        JsonNode updated = responseJson(mockMvc.perform(put("/api/admin/quota-packages/{id}", successorId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk()));
        mockMvc.perform(put("/api/admin/quota-packages/{id}", successorId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));

        mockMvc.perform(get("/api/admin/quota-packages/{id}/comparison", successorId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directSuccessor").value(true))
                .andExpect(jsonPath("$.changedFields",
                        org.hamcrest.Matchers.hasItem("CAPACITY_PER_UNIT")))
                .andExpect(jsonPath("$.changedFields",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("PRICE_BOOK"))));

        JsonNode preview = activationPreview(token, successorId);
        assertThat(preview.get("activatable").asBoolean()).isTrue();
        assertThat(preview.get("catalogRevision").isIntegralNumber()).isTrue();
        assertThat(preview.get("evaluatedAt").asText()).isNotBlank();
        assertThat(preview.get("expiresAt").asText()).isNotBlank();
        assertThat(preview.get("reviewedPrices").get(0).get("amount").asText())
                .isEqualTo("8.1200");
        assertThat(preview.get("packagesToDeactivate").get(0).asText())
                .isEqualTo(sourceId.toString());
        String lifecycleBody = objectMapper.writeValueAsString(Map.of(
                "action", "ACTIVATE",
                "expectedVersion", updated.get("version").asLong(),
                "reason", "Publish reviewed successor",
                "activationPreviewToken", preview.get("previewToken").asText()));
        mockMvc.perform(post("/api/admin/quota-packages/{id}/lifecycle", successorId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecycleBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(quotaPackageRepository.findById(sourceId).orElseThrow().getStatus())
                .isEqualTo(QuotaPackageStatus.INACTIVE);
        assertThat(quotaPackageRepository.findById(successorId).orElseThrow().getStatus())
                .isEqualTo(QuotaPackageStatus.ACTIVE);
        assertThat(onlyPrice(successorId).getStatus()).isEqualTo(ProductPriceStatus.ACTIVE);
        mockMvc.perform(get("/api/admin/product-prices/{id}/history", copiedPrice.getId())
                        .header("Authorization", bearer(token))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", org.hamcrest.Matchers.hasItems(
                        "platform.price_books.create", "platform.price_books.activate")))
                .andExpect(jsonPath("$.content[?(@.action == 'platform.price_books.activate')].reason",
                        org.hamcrest.Matchers.hasItem("Publish reviewed successor")))
                .andExpect(jsonPath("$.content[*].actorEmail",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo(ADMIN_EMAIL))));
        ProductPrice unchangedSourcePrice = productPriceRepository.findById(sourcePriceBefore.getId()).orElseThrow();
        assertThat(unchangedSourcePrice.getStatus()).isEqualTo(ProductPriceStatus.ACTIVE);
        assertThat(unchangedSourcePrice.money()).isEqualTo(sourcePriceBefore.money());
        assertThat(subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                .getEntitlementSnapshot()).isEqualTo(snapshotBefore);

        mockMvc.perform(post("/api/admin/quota-packages/{id}/lifecycle", successorId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecycleBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));

        mockMvc.perform(get("/api/admin/quota-packages/{id}/history", successorId)
                        .header("Authorization", bearer(token))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.content[*].actorEmail",
                        org.hamcrest.Matchers.hasItem(ADMIN_EMAIL)))
                .andExpect(jsonPath("$.content[*].reason",
                        org.hamcrest.Matchers.hasItem("Publish reviewed successor")))
                .andExpect(jsonPath("$.content[?(@.lifecycleAction == 'ACTIVATE')].revisionNumber",
                        org.hamcrest.Matchers.hasItem(2)))
                .andExpect(jsonPath("$.content[?(@.lifecycleAction == 'ACTIVATE')].resultingStatus",
                        org.hamcrest.Matchers.hasItem("ACTIVE")))
                .andExpect(jsonPath("$.content[?(@.successorId == '" + successorId
                                + "')].successorRevisionNumber",
                        org.hamcrest.Matchers.hasItem(2)))
                .andExpect(jsonPath("$.content[?(@.successorId == '" + successorId
                                + "')].resultingStatus",
                        org.hamcrest.Matchers.hasItem("DRAFT")));
    }

    @Test
    void staleDeleteAndLifecycleBlockersAreStable() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode draft = createPackage(token, Set.of("FLEX"), Set.of());
        UUID id = id(draft);
        QuotaPackage entity = quotaPackageRepository.findDetailedById(id).orElseThrow();
        long staleVersion = draft.get("version").asLong();

        JsonNode updated = responseJson(mockMvc.perform(put("/api/admin/quota-packages/{id}", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest(entity, 2, staleVersion))))
                .andExpect(status().isOk()));
        mockMvc.perform(delete("/api/admin/quota-packages/{id}", id)
                        .header("Authorization", bearer(token))
                        .param("expectedVersion", Long.toString(staleVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));

        ProductPrice price = onlyPrice(id);
        price.editDraft(price.money(), price.getBillingCycle(), Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        productPriceRepository.saveAndFlush(price);
        mockMvc.perform(get("/api/admin/quota-packages/{id}/activation-preview", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activatable").value(false))
                .andExpect(jsonPath("$.blockers",
                        org.hamcrest.Matchers.hasItem("EXPIRED_PRICE_WINDOW")));

        ProductPrice futurePrice = onlyPrice(id);
        Instant tomorrow = Instant.now().plusSeconds(86_400);
        futurePrice.editDraft(futurePrice.money(), futurePrice.getBillingCycle(), tomorrow, null);
        productPriceRepository.saveAndFlush(futurePrice);
        mockMvc.perform(get("/api/admin/quota-packages/{id}/activation-preview", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activatable").value(false))
                .andExpect(jsonPath("$.blockers",
                        org.hamcrest.Matchers.hasItem("NO_APPLICABLE_PRICE")));

        mockMvc.perform(delete("/api/admin/quota-packages/{id}", id)
                        .header("Authorization", bearer(token))
                        .param("expectedVersion", updated.get("version").asText()))
                .andExpect(status().isNoContent());
        packageIds.remove(id);
    }

    @Test
    void concurrentRevisionCreatesExactlyOneDraftSuccessor() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode source = createPackage(token, Set.of("FLEX"), Set.of());
        UUID sourceId = id(source);
        JsonNode active = activate(token, sourceId);
        String body = objectMapper.writeValueAsString(Map.of(
                "expectedVersion", active.get("version").asLong(),
                "reason", "Concurrent successor race"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MvcResult> command = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/admin/quota-packages/{id}/revisions", sourceId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
            };
            Future<MvcResult> first = executor.submit(command);
            Future<MvcResult> second = executor.submit(command);
            ready.await();
            start.countDown();
            List<MvcResult> results = List.of(first.get(), second.get());

            assertThat(results).extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 409);
            MvcResult conflict = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst().orElseThrow();
            assertThat(objectMapper.readTree(conflict.getResponse().getContentAsString())
                    .get("code").asText()).isEqualTo("DRAFT_SUCCESSOR_EXISTS");
        }

        List<QuotaPackage> lineage = quotaPackageRepository
                .findAllByLineageIdOrderByRevisionNumberDesc(
                        quotaPackageRepository.findById(sourceId).orElseThrow().getLineageId());
        lineage.stream().map(QuotaPackage::getId).forEach(id -> {
            if (!packageIds.contains(id)) packageIds.add(id);
        });
        assertThat(lineage).filteredOn(item -> item.getStatus() == QuotaPackageStatus.DRAFT)
                .singleElement();
        assertThat(lineage).extracting(QuotaPackage::getRevisionNumber).containsExactly(2, 1);
    }

    @Test
    void activationPreviewPinsTargetCompositionFactsThatRemainCompatible() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode draft = createPackage(token, Set.of("FLEX"), Set.of());
        UUID packageId = id(draft);
        JsonNode preview = activationPreview(token, packageId);
        assertThat(preview.get("activatable").asBoolean()).isTrue();

        UUID flexId = planRepository.findByCode("FLEX").orElseThrow().getId();
        var staff = planFeatureRepository
                .findByPlanIdAndFeature_Code(flexId, "platform.staff")
                .orElseThrow();
        List<QuotaLimitEntry> original = List.copyOf(staff.getQuotaConfigs());
        List<QuotaLimitEntry> changed = original.stream()
                .map(entry -> entry.resource().equals("members")
                        ? new QuotaLimitEntry(entry.resource(), QuotaLimitMode.FINITE,
                                Math.addExact(entry.limit(), 1L))
                        : entry)
                .toList();
        assertThat(changed).isNotEqualTo(original);
        try {
            staff.setQuotaConfigs(new ArrayList<>(changed));
            planFeatureRepository.saveAndFlush(staff);

            mockMvc.perform(post("/api/admin/quota-packages/{id}/lifecycle", packageId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "action", "ACTIVATE",
                                    "expectedVersion", preview.get("expectedVersion").asLong(),
                                    "reason", "Reject stale composition review",
                                    "activationPreviewToken", preview.get("previewToken").asText()))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("STALE_ACTIVATION_PREVIEW"));

            assertThat(quotaPackageRepository.findById(packageId).orElseThrow().getStatus())
                    .isEqualTo(QuotaPackageStatus.DRAFT);
            assertThat(onlyPrice(packageId).getStatus()).isEqualTo(ProductPriceStatus.DRAFT);
        } finally {
            var restored = planFeatureRepository.findById(staff.getId()).orElseThrow();
            restored.setQuotaConfigs(new ArrayList<>(original));
            planFeatureRepository.saveAndFlush(restored);
        }
    }

    @Test
    void inactiveSourceWithoutSaleRelevantPricesCanStillBeRevisedForMaintenance() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode source = createPackage(token, Set.of("FLEX"), Set.of());
        UUID sourceId = id(source);
        JsonNode active = activate(token, sourceId);
        JsonNode inactive = responseJson(mockMvc.perform(
                        post("/api/admin/quota-packages/{id}/lifecycle", sourceId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "action", "DEACTIVATE",
                                        "expectedVersion", active.get("version").asLong(),
                                        "reason", "Pause package while maintaining its definition"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE")));

        ProductPrice sourcePrice = onlyPrice(sourceId);
        JsonNode paused = responseJson(mockMvc.perform(
                        post("/api/admin/product-prices/{id}/pause", sourcePrice.getId())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ProductPriceVersionRequest(
                                        sourcePrice.getVersion(), "Pause source price"))))
                .andExpect(status().isOk()));
        JsonNode reactivated = responseJson(mockMvc.perform(
                        post("/api/admin/product-prices/{id}/reactivate", sourcePrice.getId())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ProductPriceActivationRequest(
                                        paused.get("version").asLong(),
                                        "Inactive owners may publish reviewed prices",
                                        fetchProductPriceActivationToken(token, sourcePrice.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE")));
        mockMvc.perform(post("/api/admin/product-prices/{id}/pause", sourcePrice.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductPriceVersionRequest(
                                reactivated.get("version").asLong(),
                                "Leave no sale-relevant source schedule"))))
                .andExpect(status().isOk());

        JsonNode revision = responseJson(mockMvc.perform(
                        post("/api/admin/quota-packages/{id}/revisions", sourceId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "expectedVersion", inactive.get("version").asLong(),
                                        "reason", "Maintain inactive package without carrying stale prices"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.copiedPriceDrafts.length()").value(0))
                .andExpect(jsonPath("$.warnings",
                        org.hamcrest.Matchers.hasItem("NO_PRICE_STARTING_POINT"))));
        UUID successorId = id(revision.get("successor"));
        packageIds.add(successorId);
        assertThat(productPriceRepository.findAllByQuotaPackageId(successorId)).isEmpty();
        mockMvc.perform(get("/api/admin/quota-packages/{id}/operations", successorId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers",
                        org.hamcrest.Matchers.hasItem("NO_PRICE_STARTING_POINT")))
                .andExpect(jsonPath("$.availableActions",
                        org.hamcrest.Matchers.hasItems("PREVIEW_ACTIVATION", "ACTIVATE")))
                .andExpect(jsonPath("$.availableActions",
                        org.hamcrest.Matchers.hasItem("MANAGE_PRICES")));
    }

    private JsonNode createPackage(
            String token, Set<String> allowedPlans, Set<String> allowedAddOns) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        CreateQuotaPackageRequest request = new CreateQuotaPackageRequest(
                "Revision fixture " + suffix, "Immutable source fixture",
                "platform.staff", "members", 2, new BigDecimal("8.12"), "USD",
                BillingCycle.MONTHLY, true, 4, allowedPlans, allowedAddOns,
                ProductSalesVisibility.PUBLIC);
        JsonNode created = responseJson(mockMvc.perform(post("/api/admin/quota-packages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineageId").isNotEmpty())
                .andExpect(jsonPath("$.revisionNumber").value(1)));
        packageIds.add(id(created));
        return created;
    }

    private JsonNode activate(String token, UUID id) throws Exception {
        JsonNode preview = activationPreview(token, id);
        return responseJson(mockMvc.perform(post("/api/admin/quota-packages/{id}/lifecycle", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "action", "ACTIVATE",
                                "expectedVersion", preview.get("expectedVersion").asLong(),
                                "reason", "Publish source fixture",
                                "activationPreviewToken", preview.get("previewToken").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE")));
    }

    private JsonNode activationPreview(String token, UUID id) throws Exception {
        return responseJson(mockMvc.perform(
                        get("/api/admin/quota-packages/{id}/activation-preview", id)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
    }

    private UpdateQuotaPackageRequest updateRequest(
            QuotaPackage item, long capacity, long expectedVersion) {
        return new UpdateQuotaPackageRequest(
                item.getName(), item.getDescription(), item.getFeature().getCode(), item.getResource(),
                capacity, item.getPrice(), item.getCurrencyCode(), item.getBillingCycle(),
                item.isRepeatable(), item.getMaximumQuantity(), item.getAllowedPlanCodes(),
                item.getAllowedAddOnCodes(), expectedVersion);
    }

    private UUID currentAccountId(String token) throws Exception {
        JsonNode account = responseJson(mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        return UUID.fromString(account.get("id").asText());
    }

    private ProductPrice onlyPrice(UUID packageId) {
        assertThat(productPriceRepository.findAllByQuotaPackageId(packageId)).hasSize(1);
        return productPriceRepository.findAllByQuotaPackageId(packageId).getFirst();
    }

    private UUID id(JsonNode value) {
        return UUID.fromString(value.get("id").asText());
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
