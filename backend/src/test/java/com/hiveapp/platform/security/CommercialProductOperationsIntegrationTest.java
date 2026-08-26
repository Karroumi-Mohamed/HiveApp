package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.AssignAddOnFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.DeletePlanRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceVersionRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.QuotaLimitRequest;
import com.hiveapp.platform.client.plan.dto.UpdateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import com.hiveapp.shared.money.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommercialProductOperationsIntegrationTest extends PlatformShellIntegrationTestSupport {

    private final String marker = "OPS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private final List<UUID> quotaPackageIds = new ArrayList<>();
    private final List<UUID> addOnIds = new ArrayList<>();
    private final List<UUID> planIds = new ArrayList<>();

    @Autowired
    private QuotaPackageRepository quotaPackageRepository;

    @Autowired
    private AddOnRepository addOnRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @AfterEach
    void removeFixtures() {
        quotaPackageIds.forEach(id -> {
            var prices = productPriceRepository.findAllByQuotaPackageId(id);
            if (!prices.isEmpty()) {
                productPriceRepository.deleteAllInBatch(prices);
                productPriceRepository.flush();
            }
            quotaPackageRepository.findById(id).ifPresent(quotaPackageRepository::delete);
        });
        quotaPackageRepository.flush();
        addOnIds.forEach(id -> productPriceRepository.deleteAllInBatch(
                productPriceRepository.findAllByAddOnId(id)));
        productPriceRepository.flush();
        addOnIds.stream()
                .map(addOnRepository::findById)
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt(
                        item -> item.getSourceAddOn() == null ? 1 : 0))
                .forEach(item -> {
                    addOnRepository.delete(item);
                    addOnRepository.flush();
                });
        planIds.forEach(id -> {
            productPriceRepository.deleteAllInBatch(productPriceRepository.findAllByPlanId(id));
            planFeatureRepository.deleteAllInBatch(planFeatureRepository.findAllByPlanId(id));
        });
        productPriceRepository.flush();
        planFeatureRepository.flush();
        planIds.stream()
                .map(planRepository::findById)
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt(
                        item -> item.getSourcePlan() == null ? 1 : 0))
                .forEach(item -> {
                    planRepository.delete(item);
                    planRepository.flush();
                });
    }

    @Test
    void operationalPagesEnforceBoundsWhitelistsAndCombinedFilters() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode targeted = createQuotaPackage(
                token, marker + " Targeted", ProductSalesVisibility.PUBLIC, Set.of("FLEX"));
        createQuotaPackage(
                token, marker + " Open", ProductSalesVisibility.DIRECT_ONLY, Set.of());

        mockMvc.perform(get("/api/admin/quota-packages")
                        .header("Authorization", bearer(token))
                        .param("search", marker)
                        .param("status", "DRAFT")
                        .param("salesVisibility", "PUBLIC")
                        .param("featureCode", "platform.staff")
                        .param("resource", "members")
                        .param("targetPlanCode", "FLEX")
                        .param("sort", "name")
                        .param("direction", "asc")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(targeted.get("id").asText()))
                .andExpect(jsonPath("$.content[0].targetingMode").value("TARGETED"))
                .andExpect(jsonPath("$.content[0].targetPlanCount").value(1))
                .andExpect(jsonPath("$.content[0].applicablePriceCount").value(0))
                .andExpect(jsonPath("$.content[0].availableActions").isArray())
                .andExpect(jsonPath("$.content[0].blockers").isArray());

        mockMvc.perform(get("/api/admin/plans")
                        .header("Authorization", bearer(token))
                        .param("search", "FLEX")
                        .param("status", "ACTIVE")
                        .param("salesVisibility", "PUBLIC")
                        .param("extensionPolicy", "OPEN_COMPATIBLE")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("FLEX"))
                .andExpect(jsonPath("$.content[0].currentSubscriberCount").isNumber())
                .andExpect(jsonPath("$.content[0].affectedSubscriptionCount").isNumber())
                .andExpect(jsonPath("$.content[0].applicablePriceCount").isNumber());

        mockMvc.perform(get("/api/admin/quota-packages/{id}/operations",
                        targeted.get("id").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targeted.get("id").asText()))
                .andExpect(jsonPath("$.availableActions").isArray())
                .andExpect(jsonPath("$.blockers").isArray());
        UUID flexId = planRepository.findByCode("FLEX").orElseThrow().getId();
        JsonNode flexOperations = responseJson(mockMvc.perform(
                        get("/api/admin/plans/{id}/operations", flexId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        assertThat(flexOperations.get("affectedSubscriptionCount").asLong())
                .isGreaterThanOrEqualTo(flexOperations.get("currentSubscriberCount").asLong());

        assertInvalidPage(token, "/api/admin/plans", "page", "-1");
        assertInvalidPage(token, "/api/admin/add-ons", "size", "101");
        assertInvalidPage(token, "/api/admin/quota-packages", "sort", "price");
        assertInvalidPage(token, "/api/admin/plans", "direction", "sideways");
    }

    @Test
    void deterministicTieBreakAndSelectedChoiceHydrationAreBounded() throws Exception {
        String token = loginAdminAndGetToken();
        String sharedName = marker + " Same name";
        for (int index = 0; index < 3; index++) {
            JsonNode created = createAddOn(token, sharedName);
            addOnIds.add(UUID.fromString(created.get("id").asText()));
        }

        JsonNode page = responseJson(mockMvc.perform(get("/api/admin/add-ons")
                        .header("Authorization", bearer(token))
                        .param("search", sharedName)
                        .param("status", "DRAFT")
                        .param("targetPlanCode", "FLEX")
                        .param("sort", "name")
                        .param("direction", "desc")
                        .param("size", "10"))
                .andExpect(status().isOk()));
        List<UUID> returned = new ArrayList<>();
        page.get("content").forEach(row -> returned.add(UUID.fromString(row.get("id").asText())));
        assertThat(returned).hasSize(3);
        assertThat(returned).isSortedAccordingTo(Comparator.comparing(UUID::toString));
        assertThat(page.get("content").get(0).get("targetingMode").asText()).isEqualTo("TARGETED");
        assertThat(page.get("content").get(0).get("blockedPlanCount").asInt()).isZero();
        assertThat(page.get("content").get(0).get("availableActions").toString())
                .contains("MANAGE_PRICES")
                .doesNotContain("ACTIVATE");

        mockMvc.perform(get("/api/admin/add-ons/chooser")
                        .header("Authorization", bearer(token))
                        .param("search", sharedName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        var selected = get("/api/admin/add-ons/chooser/selected")
                .header("Authorization", bearer(token));
        addOnIds.forEach(id -> selected.param("ids", id.toString()));
        mockMvc.perform(selected)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[*].choiceState",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("NO_LONGER_ACTIVE"))));

        String retainedCode = addOnRepository.findById(addOnIds.getFirst()).orElseThrow().getCode();
        mockMvc.perform(get("/api/admin/add-ons/chooser/selected-codes")
                        .header("Authorization", bearer(token))
                        .param("codes", retainedCode.toLowerCase(java.util.Locale.ROOT))
                        .param("codes", "MISSING_RETAINED_ADD_ON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value(retainedCode))
                .andExpect(jsonPath("$[0].choiceState").value("NO_LONGER_ACTIVE"))
                .andExpect(jsonPath("$[1].code").value("MISSING_RETAINED_ADD_ON"))
                .andExpect(jsonPath("$[1].choiceState").value("MISSING"));
        mockMvc.perform(get("/api/admin/add-ons/chooser/selected-codes")
                        .header("Authorization", bearer(token))
                        .param("codes", retainedCode)
                        .param("codes", retainedCode.toLowerCase(java.util.Locale.ROOT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        var tooMany = get("/api/admin/add-ons/chooser/selected")
                .header("Authorization", bearer(token));
        for (int index = 0; index < 101; index++) {
            tooMany.param("ids", UUID.randomUUID().toString());
        }
        mockMvc.perform(tooMany)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        var tooManyCodes = get("/api/admin/add-ons/chooser/selected-codes")
                .header("Authorization", bearer(token));
        for (int index = 0; index < 101; index++) {
            tooManyCodes.param("codes", "RETAINED_ADD_ON_" + index);
        }
        mockMvc.perform(tooManyCodes)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void productMutationsReturnFreshVersionsAndRejectSequentialStaleWrites() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode plan = responseJson(mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                marker + " Versioned plan", null, BigDecimal.ZERO,
                                "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated()));
        UUID planId = UUID.fromString(plan.get("id").asText());
        planIds.add(planId);
        long initialPlanVersion = plan.get("version").asLong();
        JsonNode draftOperations = responseJson(mockMvc.perform(
                        get("/api/admin/plans/{id}/operations", planId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        assertThat(draftOperations.get("availableActions").toString())
                .contains("MANAGE_PRICES")
                .doesNotContain("ACTIVATE");

        JsonNode firstPlanUpdate = responseJson(mockMvc.perform(put("/api/admin/plans/{id}", planId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlanRequest(
                                marker + " Plan v2", null, BigDecimal.ONE, "USD",
                                BillingCycle.MONTHLY, initialPlanVersion))))
                .andExpect(status().isOk()));
        assertThat(firstPlanUpdate.get("version").asLong()).isGreaterThan(initialPlanVersion);
        long freshPlanVersion = firstPlanUpdate.get("version").asLong();

        JsonNode secondPlanUpdate = responseJson(mockMvc.perform(put("/api/admin/plans/{id}", planId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlanRequest(
                                marker + " Plan v3", null, BigDecimal.TWO, "USD",
                                BillingCycle.MONTHLY, freshPlanVersion))))
                .andExpect(status().isOk()));
        assertThat(secondPlanUpdate.get("version").asLong()).isGreaterThan(freshPlanVersion);

        mockMvc.perform(put("/api/admin/plans/{id}", planId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlanRequest(
                                marker + " stale", null, BigDecimal.TEN, "USD",
                                BillingCycle.MONTHLY, initialPlanVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        mockMvc.perform(patch("/api/admin/plans/{id}/status", planId)
                        .header("Authorization", bearer(token))
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(initialPlanVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));

        JsonNode addOn = createAddOn(token, marker + " Versioned AddOn");
        UUID addOnId = UUID.fromString(addOn.get("id").asText());
        addOnIds.add(addOnId);
        long initialAddOnVersion = addOn.get("version").asLong();
        JsonNode updatedAddOn = responseJson(mockMvc.perform(put("/api/admin/add-ons/{id}", addOnId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAddOnRequest(
                                marker + " AddOn v2", null, new BigDecimal("4.00"), "USD",
                                BillingCycle.MONTHLY, Set.of("FLEX"), Set.of(), Set.of(), Set.of(),
                                initialAddOnVersion))))
                .andExpect(status().isOk()));
        long freshAddOnVersion = updatedAddOn.get("version").asLong();
        assertThat(freshAddOnVersion).isGreaterThan(initialAddOnVersion);

        mockMvc.perform(put("/api/admin/add-ons/{id}", addOnId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAddOnRequest(
                                marker + " stale AddOn", null, new BigDecimal("5.00"), "USD",
                                BillingCycle.MONTHLY, Set.of("FLEX"), Set.of(), Set.of(), Set.of(),
                                initialAddOnVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        mockMvc.perform(patch("/api/admin/add-ons/{id}/status", addOnId)
                        .header("Authorization", bearer(token))
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(initialAddOnVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        mockMvc.perform(delete("/api/admin/add-ons/{id}", addOnId)
                        .header("Authorization", bearer(token))
                        .param("expectedVersion", String.valueOf(initialAddOnVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        mockMvc.perform(delete("/api/admin/add-ons/{id}", addOnId)
                        .header("Authorization", bearer(token))
                        .param("expectedVersion", String.valueOf(freshAddOnVersion)))
                .andExpect(status().isNoContent());
        addOnIds.remove(addOnId);
    }

    @Test
    void planCompositionAdvancesOwnerVersionAndInvalidatesStaleLifecycleIntent() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode plan = responseJson(mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                marker + " Composition plan", null, BigDecimal.ZERO,
                                "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated()));
        UUID planId = UUID.fromString(plan.get("id").asText());
        planIds.add(planId);
        long beforeComposition = plan.get("version").asLong();

        mockMvc.perform(post("/api/admin/plans/{id}/features", planId)
                        .param("expectedVersion", String.valueOf(beforeComposition))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignPlanFeatureRequest(
                                "platform.workspace", PlanFeatureMode.INCLUDED, List.of()))))
                .andExpect(status().isCreated());

        long afterComposition = planRepository.findById(planId).orElseThrow().getVersion();
        assertThat(afterComposition).isGreaterThan(beforeComposition);
        mockMvc.perform(patch("/api/admin/plans/{id}/status", planId)
                        .header("Authorization", bearer(token))
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(beforeComposition)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        mockMvc.perform(patch("/api/admin/plans/{id}/status", planId)
                        .header("Authorization", bearer(token))
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(afterComposition)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        activateInitialPrice(token, productPriceRepository.findAllByPlanId(planId).getFirst());
        mockMvc.perform(patch("/api/admin/plans/{id}/status", planId)
                        .header("Authorization", bearer(token))
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(afterComposition)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void publishedPriceHistoryMakesEveryDraftArchivableButNotDisposable() throws Exception {
        String token = loginAdminAndGetToken();

        JsonNode plan = responseJson(mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                marker + " Published-price Plan", null, BigDecimal.ONE,
                                "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated()));
        UUID planId = UUID.fromString(plan.get("id").asText());
        planIds.add(planId);
        activateInitialPrice(token, productPriceRepository.findAllByPlanId(planId).getFirst());
        JsonNode planOperations = operations(token, "/api/admin/plans/{id}/operations", planId);
        assertPublishedDraftActions(planOperations);
        JsonNode deletionPreview = responseJson(mockMvc.perform(
                        get("/api/admin/plans/{id}/deletion-preview", planId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(false))
                .andExpect(jsonPath("$.blockers",
                        org.hamcrest.Matchers.hasItem("PUBLISHED_PRICE_HISTORY"))));
        mockMvc.perform(delete("/api/admin/plans/{id}", planId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeletePlanRequest(
                                plan.get("name").asText(), deletionPreview.get("expectedVersion").asLong(),
                                deletionPreview.get("previewToken").asText()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_BLOCKED"))
                .andExpect(jsonPath("$.details",
                        org.hamcrest.Matchers.hasItem("PUBLISHED_PRICE_HISTORY")));
        assertReasonedArchive(token, "/api/admin/plans/{id}/status", planId,
                plan.get("version").asLong());
        assertThat(productPriceRepository.findAllByPlanId(planId)).singleElement()
                .satisfies(price -> assertThat(price.getStatus().name()).isEqualTo("ACTIVE"));
        assertThat(productPriceRepository.findAllApplicable(
                com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN,
                planId, java.time.Instant.now())).isEmpty();
        assertThat(operations(token, "/api/admin/plans/{id}/operations", planId)
                .get("applicablePriceCount").asLong()).isZero();
        var archivedOwnerPrice = productPriceRepository.findAllByPlanId(planId).getFirst();
        assertThat(productPriceRepository.findAllApplicable(java.time.Instant.now()))
                .extracting(com.hiveapp.platform.client.plan.domain.entity.ProductPrice::getId)
                .doesNotContain(archivedOwnerPrice.getId());
        assertThat(productPriceRepository.findAllApplicableForOwners(
                Set.of(planId), Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()),
                java.time.Instant.now()))
                .extracting(com.hiveapp.platform.client.plan.domain.entity.ProductPrice::getId)
                .doesNotContain(archivedOwnerPrice.getId());
        assertArchivedOwnerPriceCannotReactivate(token, archivedOwnerPrice);

        JsonNode addOn = createAddOn(token, marker + " Published-price AddOn");
        UUID addOnId = UUID.fromString(addOn.get("id").asText());
        addOnIds.add(addOnId);
        activateInitialPrice(token, productPriceRepository.findAllByAddOnId(addOnId).getFirst());
        JsonNode referencingAddOn = responseJson(mockMvc.perform(post("/api/admin/add-ons")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAddOnRequest(
                                marker + " Referencing AddOn", null, new BigDecimal("3.0000"),
                                "USD", BillingCycle.MONTHLY, Set.of("FLEX"), Set.of(),
                                Set.of(addOn.get("code").asText()), Set.of(),
                                ProductSalesVisibility.PUBLIC))))
                .andExpect(status().isCreated()));
        addOnIds.add(UUID.fromString(referencingAddOn.get("id").asText()));
        createQuotaPackage(token, marker + " Referencing package",
                ProductSalesVisibility.PUBLIC, Set.of("FLEX"),
                Set.of(addOn.get("code").asText()));
        JsonNode referencedAddOnOperations = operations(
                token, "/api/admin/add-ons/{id}/operations", addOnId);
        assertPublishedDraftActions(referencedAddOnOperations);
        assertThat(referencedAddOnOperations.get("referencedByAddOnCount").asLong()).isEqualTo(1);
        assertThat(referencedAddOnOperations.get("referencedByQuotaPackageCount").asLong())
                .isEqualTo(1);
        assertThat(referencedAddOnOperations.get("blockers").toString())
                .contains("REFERENCED_BY_ADD_ON", "REFERENCED_BY_QUOTA_PACKAGE");
        mockMvc.perform(delete("/api/admin/add-ons/{id}", addOnId)
                        .header("Authorization", bearer(token))
                        .param("expectedVersion", addOn.get("version").asText()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_BLOCKED"))
                .andExpect(jsonPath("$.details",
                        org.hamcrest.Matchers.contains(
                                "PUBLISHED_PRICE_HISTORY",
                                "REFERENCED_BY_ADD_ON",
                                "REFERENCED_BY_QUOTA_PACKAGE")));
        assertReasonedArchive(token, "/api/admin/add-ons/{id}/status", addOnId,
                addOn.get("version").asLong());
        assertThat(productPriceRepository.findAllApplicable(
                com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON,
                addOnId, java.time.Instant.now())).isEmpty();
        var archivedAddOnPrice = productPriceRepository.findAllByAddOnId(addOnId).getFirst();
        assertThat(productPriceRepository.findAllApplicable(java.time.Instant.now()))
                .extracting(com.hiveapp.platform.client.plan.domain.entity.ProductPrice::getId)
                .doesNotContain(archivedAddOnPrice.getId());
        assertThat(productPriceRepository.findAllApplicableForOwners(
                Set.of(UUID.randomUUID()), Set.of(addOnId), Set.of(UUID.randomUUID()),
                java.time.Instant.now()))
                .extracting(com.hiveapp.platform.client.plan.domain.entity.ProductPrice::getId)
                .doesNotContain(archivedAddOnPrice.getId());
        assertArchivedOwnerPriceCannotReactivate(token, archivedAddOnPrice);

        JsonNode quota = createQuotaPackage(
                token, marker + " Published-price package", ProductSalesVisibility.PUBLIC,
                Set.of("FLEX"));
        UUID quotaId = UUID.fromString(quota.get("id").asText());
        activateInitialPrice(token, productPriceRepository.findAllByQuotaPackageId(quotaId).getFirst());
        assertPublishedDraftActions(operations(
                token, "/api/admin/quota-packages/{id}/operations", quotaId));
        mockMvc.perform(delete("/api/admin/quota-packages/{id}", quotaId)
                        .header("Authorization", bearer(token))
                        .param("expectedVersion", quota.get("version").asText()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_BLOCKED"))
                .andExpect(jsonPath("$.details",
                        org.hamcrest.Matchers.hasItem("PUBLISHED_PRICE_HISTORY")));
        mockMvc.perform(post("/api/admin/quota-packages/{id}/lifecycle", quotaId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "action", "ARCHIVE",
                                "expectedVersion", quota.get("version").asLong(),
                                "reason", ""))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/admin/quota-packages/{id}/lifecycle", quotaId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "action", "ARCHIVE",
                                "expectedVersion", quota.get("version").asLong() + 1,
                                "reason", "Stale archival intent"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        mockMvc.perform(post("/api/admin/quota-packages/{id}/lifecycle", quotaId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "action", "ARCHIVE",
                                "expectedVersion", quota.get("version").asLong(),
                                "reason", "Abandon package draft with published price history"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        assertThat(productPriceRepository.findAllApplicable(
                com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE,
                quotaId, java.time.Instant.now())).isEmpty();
        var archivedQuotaPrice = productPriceRepository.findAllByQuotaPackageId(quotaId).getFirst();
        assertThat(productPriceRepository.findAllApplicable(java.time.Instant.now()))
                .extracting(com.hiveapp.platform.client.plan.domain.entity.ProductPrice::getId)
                .doesNotContain(archivedQuotaPrice.getId());
        assertThat(productPriceRepository.findAllApplicableForOwners(
                Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()), Set.of(quotaId),
                java.time.Instant.now()))
                .extracting(com.hiveapp.platform.client.plan.domain.entity.ProductPrice::getId)
                .doesNotContain(archivedQuotaPrice.getId());
        assertArchivedOwnerPriceCannotReactivate(token, archivedQuotaPrice);
    }

    @Test
    void planDuplicateAndProductRevisionsCopyMonthlyAndYearlySchedulesAsNewOwnerDrafts()
            throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode plan = responseJson(mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                marker + " Price-copy Plan", null, new BigDecimal("11.00"),
                                "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated()));
        UUID planId = UUID.fromString(plan.get("id").asText());
        planIds.add(planId);
        mockMvc.perform(post("/api/admin/plans/{id}/features", planId)
                        .param("expectedVersion", plan.get("version").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignPlanFeatureRequest(
                                "platform.company", PlanFeatureMode.INCLUDED,
                                List.of(new QuotaLimitRequest(
                                        "companies",
                                        com.hiveapp.shared.quota.QuotaLimitMode.UNLIMITED,
                                        null))))))
                .andExpect(status().isCreated());
        activateInitialPrice(token, productPriceRepository.findAllByPlanId(planId).getFirst());
        var annualPlanPrice = com.hiveapp.platform.client.plan.domain.entity.ProductPrice.draft(
                planRepository.findById(planId).orElseThrow(),
                Money.of(new BigDecimal("99.00"), "USD"),
                BillingCycle.YEARLY, java.time.Instant.now().minusSeconds(30), null);
        annualPlanPrice.activate();
        productPriceRepository.saveAndFlush(annualPlanPrice);
        long planVersion = planRepository.findById(planId).orElseThrow().getVersion();
        JsonNode activePlan = responseJson(mockMvc.perform(patch("/api/admin/plans/{id}/status", planId)
                        .header("Authorization", bearer(token))
                        .param("status", "ACTIVE")
                        .param("expectedVersion", Long.toString(planVersion)))
                .andExpect(status().isOk()));
        List<com.hiveapp.platform.client.plan.domain.entity.ProductPrice> sourcePlanPrices =
                List.copyOf(productPriceRepository.findAllByPlanId(planId));

        PlanBranchRequest planBranch = new PlanBranchRequest(
                marker + " Price-copy Plan successor", null, new BigDecimal("12.00"),
                "USD", BillingCycle.MONTHLY);
        JsonNode duplicate = responseJson(mockMvc.perform(post("/api/admin/plans/{id}/duplicate", planId)
                        .header("Authorization", bearer(token))
                        .param("expectedVersion", activePlan.get("version").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planBranch)))
                .andExpect(status().isCreated()));
        UUID duplicateId = UUID.fromString(duplicate.get("id").asText());
        planIds.add(duplicateId);
        assertCopiedStartingPoint(
                sourcePlanPrices, productPriceRepository.findAllByPlanId(duplicateId), duplicateId);

        JsonNode revision = responseJson(mockMvc.perform(post("/api/admin/plans/{id}/revisions", planId)
                        .header("Authorization", bearer(token))
                        .param("expectedVersion", activePlan.get("version").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planBranch)))
                .andExpect(status().isCreated()));
        UUID planRevisionId = UUID.fromString(revision.get("id").asText());
        planIds.add(planRevisionId);
        assertCopiedStartingPoint(
                sourcePlanPrices, productPriceRepository.findAllByPlanId(planRevisionId), planRevisionId);

        JsonNode addOn = createAddOn(token, marker + " Price-copy AddOn");
        UUID addOnId = UUID.fromString(addOn.get("id").asText());
        addOnIds.add(addOnId);
        mockMvc.perform(post("/api/admin/add-ons/{id}/features", addOnId)
                        .param("expectedVersion", addOn.get("version").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignAddOnFeatureRequest(
                                "platform.organization", List.of()))))
                .andExpect(status().isCreated());
        long unpublishedAddOnVersion = addOnRepository.findById(addOnId)
                .orElseThrow().getRowVersion();
        mockMvc.perform(patch("/api/admin/add-ons/{id}/status", addOnId)
                        .header("Authorization", bearer(token))
                        .param("status", "ACTIVE")
                        .param("expectedVersion", Long.toString(unpublishedAddOnVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        activateInitialPrice(token, productPriceRepository.findAllByAddOnId(addOnId).getFirst());
        var annualAddOnPrice = com.hiveapp.platform.client.plan.domain.entity.ProductPrice.draft(
                addOnRepository.findById(addOnId).orElseThrow(),
                Money.of(new BigDecimal("29.00"), "USD"),
                BillingCycle.YEARLY, java.time.Instant.now().minusSeconds(30), null);
        annualAddOnPrice.activate();
        productPriceRepository.saveAndFlush(annualAddOnPrice);
        long addOnVersion = addOnRepository.findById(addOnId).orElseThrow().getRowVersion();
        JsonNode activeAddOn = responseJson(mockMvc.perform(
                        patch("/api/admin/add-ons/{id}/status", addOnId)
                                .header("Authorization", bearer(token))
                                .param("status", "ACTIVE")
                                .param("expectedVersion", Long.toString(addOnVersion)))
                .andExpect(status().isOk()));
        List<com.hiveapp.platform.client.plan.domain.entity.ProductPrice> sourceAddOnPrices =
                List.copyOf(productPriceRepository.findAllByAddOnId(addOnId));
        JsonNode addOnRevision = responseJson(mockMvc.perform(
                        post("/api/admin/add-ons/{id}/revisions", addOnId)
                                .header("Authorization", bearer(token))
                                .param("expectedVersion", activeAddOn.get("version").asText()))
                .andExpect(status().isCreated()));
        UUID addOnRevisionId = UUID.fromString(addOnRevision.get("id").asText());
        addOnIds.add(addOnRevisionId);
        assertCopiedStartingPoint(
                sourceAddOnPrices,
                productPriceRepository.findAllByAddOnId(addOnRevisionId), addOnRevisionId);
    }

    private void assertCopiedStartingPoint(
            List<com.hiveapp.platform.client.plan.domain.entity.ProductPrice> source,
            List<com.hiveapp.platform.client.plan.domain.entity.ProductPrice> copied,
            UUID targetOwnerId
    ) {
        assertThat(source).hasSize(2)
                .allMatch(price -> price.getStatus().name().equals("ACTIVE"));
        assertThat(copied).hasSize(2)
                .allMatch(price -> price.getStatus().name().equals("DRAFT"))
                .allMatch(price -> price.ownerId().equals(targetOwnerId))
                .allMatch(price -> price.getSourcePrice() == null);
        assertThat(copied).extracting(price -> price.getBillingCycle())
                .containsExactlyInAnyOrder(BillingCycle.MONTHLY, BillingCycle.YEARLY);
        assertThat(copied).extracting(price -> price.getLineageId())
                .doesNotContainAnyElementsOf(source.stream()
                        .map(price -> price.getLineageId()).toList());
        assertThat(source).allMatch(price -> price.getStatus().name().equals("ACTIVE"));
    }

    @Test
    void inactivePlanAndAddOnRevisionsRemainMaintainableWithoutActiveSourcePrices()
            throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode plan = responseJson(mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                marker + " No-price Plan", null, BigDecimal.ONE,
                                "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated()));
        UUID planId = UUID.fromString(plan.get("id").asText());
        planIds.add(planId);
        var planPrice = productPriceRepository.findAllByPlanId(planId).getFirst();
        activateInitialPrice(token, planPrice);
        var inactivePlan = planRepository.findById(planId).orElseThrow();
        inactivePlan.setStatus(com.hiveapp.platform.client.plan.domain.constant.PlanStatus.INACTIVE);
        planRepository.saveAndFlush(inactivePlan);
        JsonNode pausedPlanPrice = pausePrice(
                token, productPriceRepository.findById(planPrice.getId()).orElseThrow(),
                "Pause price while Plan is inactive");
        JsonNode reactivatedPlanPrice = reactivatePrice(
                token, planPrice.getId(), pausedPlanPrice.get("version").asLong(),
                "Resume price while Plan is inactive");
        pausePrice(token, planPrice.getId(), reactivatedPlanPrice.get("version").asLong(),
                "Leave source price paused for maintenance revision");
        long inactivePlanVersion = operations(
                token, "/api/admin/plans/{id}/operations", planId).get("version").asLong();
        JsonNode planRevision = responseJson(mockMvc.perform(
                        post("/api/admin/plans/{id}/revisions", planId)
                                .header("Authorization", bearer(token))
                                .param("expectedVersion", Long.toString(inactivePlanVersion))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new PlanBranchRequest(
                                        marker + " No-price Plan successor", null, BigDecimal.ONE,
                                        "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated()));
        UUID planRevisionId = UUID.fromString(planRevision.get("id").asText());
        planIds.add(planRevisionId);
        assertThat(productPriceRepository.findAllByPlanId(planRevisionId)).isEmpty();
        assertNoPriceStartingPoint(
                operations(token, "/api/admin/plans/{id}/operations", planRevisionId));

        JsonNode addOn = createAddOn(token, marker + " No-price AddOn");
        UUID addOnId = UUID.fromString(addOn.get("id").asText());
        addOnIds.add(addOnId);
        var addOnPrice = productPriceRepository.findAllByAddOnId(addOnId).getFirst();
        activateInitialPrice(token, addOnPrice);
        var inactiveAddOn = addOnRepository.findById(addOnId).orElseThrow();
        inactiveAddOn.setStatus(
                com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.INACTIVE);
        addOnRepository.saveAndFlush(inactiveAddOn);
        JsonNode pausedAddOnPrice = pausePrice(
                token, productPriceRepository.findById(addOnPrice.getId()).orElseThrow(),
                "Pause price while AddOn is inactive");
        JsonNode reactivatedAddOnPrice = reactivatePrice(
                token, addOnPrice.getId(), pausedAddOnPrice.get("version").asLong(),
                "Resume price while AddOn is inactive");
        pausePrice(token, addOnPrice.getId(), reactivatedAddOnPrice.get("version").asLong(),
                "Leave source price paused for maintenance revision");
        long inactiveAddOnVersion = operations(
                token, "/api/admin/add-ons/{id}/operations", addOnId).get("version").asLong();
        JsonNode addOnRevision = responseJson(mockMvc.perform(
                        post("/api/admin/add-ons/{id}/revisions", addOnId)
                                .header("Authorization", bearer(token))
                                .param("expectedVersion", Long.toString(inactiveAddOnVersion)))
                .andExpect(status().isCreated()));
        UUID addOnRevisionId = UUID.fromString(addOnRevision.get("id").asText());
        addOnIds.add(addOnRevisionId);
        assertThat(productPriceRepository.findAllByAddOnId(addOnRevisionId)).isEmpty();
        assertNoPriceStartingPoint(
                operations(token, "/api/admin/add-ons/{id}/operations", addOnRevisionId));
    }

    private void assertNoPriceStartingPoint(JsonNode operations) {
        assertThat(operations.get("blockers").toString()).contains("NO_PRICE_STARTING_POINT");
        assertThat(operations.get("availableActions").toString())
                .contains("MANAGE_PRICES")
                .doesNotContain("ACTIVATE");
    }

    private void activateInitialPrice(
            String token,
            com.hiveapp.platform.client.plan.domain.entity.ProductPrice price
    ) throws Exception {
        mockMvc.perform(post("/api/admin/product-prices/{id}/activate", price.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductPriceVersionRequest(
                                price.getVersion(), "Publish reviewed initial price"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private JsonNode pausePrice(
            String token,
            com.hiveapp.platform.client.plan.domain.entity.ProductPrice price,
            String reason
    ) throws Exception {
        return pausePrice(token, price.getId(), price.getVersion(), reason);
    }

    private JsonNode pausePrice(String token, UUID id, long version, String reason) throws Exception {
        return responseJson(mockMvc.perform(post("/api/admin/product-prices/{id}/pause", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductPriceVersionRequest(
                                version, reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE")));
    }

    private JsonNode reactivatePrice(String token, UUID id, long version, String reason)
            throws Exception {
        return responseJson(mockMvc.perform(post("/api/admin/product-prices/{id}/reactivate", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductPriceVersionRequest(
                                version, reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE")));
    }

    private void assertArchivedOwnerPriceCannotReactivate(
            String token,
            com.hiveapp.platform.client.plan.domain.entity.ProductPrice price
    ) throws Exception {
        JsonNode paused = pausePrice(token, price, "Pause archived-owner price");
        mockMvc.perform(post("/api/admin/product-prices/{id}/reactivate", price.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductPriceVersionRequest(
                                paused.get("version").asLong(),
                                "Must not reactivate archived-owner price"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("OWNER_NOT_ACTIVE")));
    }

    private JsonNode operations(String token, String path, UUID id) throws Exception {
        return responseJson(mockMvc.perform(get(path, id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
    }

    private void assertPublishedDraftActions(JsonNode operations) {
        assertThat(operations.get("publishedPriceCount").asLong()).isEqualTo(1);
        assertThat(operations.get("blockers").toString()).contains("PUBLISHED_PRICE_HISTORY");
        assertThat(operations.get("availableActions").toString())
                .contains("ARCHIVE")
                .doesNotContain("DELETE_DRAFT");
    }

    private void assertReasonedArchive(
            String token,
            String path,
            UUID id,
            long version
    ) throws Exception {
        mockMvc.perform(patch(path, id)
                        .header("Authorization", bearer(token))
                        .param("status", "ARCHIVED")
                        .param("expectedVersion", Long.toString(version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(patch(path, id)
                        .header("Authorization", bearer(token))
                        .param("status", "ARCHIVED")
                        .param("expectedVersion", Long.toString(version + 1))
                        .param("reason", "Stale archival intent"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        mockMvc.perform(patch(path, id)
                        .header("Authorization", bearer(token))
                        .param("status", "ARCHIVED")
                        .param("expectedVersion", Long.toString(version))
                        .param("reason", "Abandon draft with published price history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    private JsonNode createQuotaPackage(
            String token,
            String name,
            ProductSalesVisibility visibility,
            Set<String> plans
    ) throws Exception {
        return createQuotaPackage(token, name, visibility, plans, Set.of());
    }

    private JsonNode createQuotaPackage(
            String token,
            String name,
            ProductSalesVisibility visibility,
            Set<String> plans,
            Set<String> addOns
    ) throws Exception {
        CreateQuotaPackageRequest request = new CreateQuotaPackageRequest(
                name, null, "platform.staff", "members", 2,
                new BigDecimal("7.2500"), "USD", BillingCycle.MONTHLY,
                true, 5, plans, addOns, visibility);
        JsonNode created = responseJson(mockMvc.perform(post("/api/admin/quota-packages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
        quotaPackageIds.add(UUID.fromString(created.get("id").asText()));
        return created;
    }

    private JsonNode createAddOn(String token, String name) throws Exception {
        CreateAddOnRequest request = new CreateAddOnRequest(
                name, null, new BigDecimal("3.0000"), "USD", BillingCycle.MONTHLY,
                Set.of("FLEX"), Set.of(), Set.of(), Set.of(), ProductSalesVisibility.PUBLIC);
        return responseJson(mockMvc.perform(post("/api/admin/add-ons")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
    }

    private void assertInvalidPage(String token, String path, String parameter, String value)
            throws Exception {
        mockMvc.perform(get(path)
                        .header("Authorization", bearer(token))
                        .param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
