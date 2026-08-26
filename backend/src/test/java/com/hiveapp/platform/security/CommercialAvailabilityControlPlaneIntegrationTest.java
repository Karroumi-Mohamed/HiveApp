package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommercialAvailabilityControlPlaneIntegrationTest
        extends PlatformShellIntegrationTestSupport {

    @Autowired private PlanRepository planRepository;
    @Autowired private AddOnRepository addOnRepository;
    @Autowired private QuotaPackageRepository quotaPackageRepository;
    @Autowired private ProductPriceRepository productPriceRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void previewApplyIsReasonedVersionedConcurrentAndAudited() throws Exception {
        String token = loginAdminAndGetToken();
        var flex = planRepository.findByCode("FLEX").orElseThrow();
        resetVisibility();

        try {
            mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                            .param("search", "custom")
                            .param("type", "ADD_ON")
                            .param("available", "true")
                            .param("currencyCode", "USD")
                            .param("billingCycle", "MONTHLY")
                            .param("page", "0")
                            .param("size", "1")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].code").value("CUSTOM_ROLES"))
                    .andExpect(jsonPath("$.content[0].operatorSelectable").value(true))
                    .andExpect(jsonPath("$.content[0].applicablePriceCount").value(1));

            JsonNode noChange = planPreview(
                    token, flex.getId(), PlanExtensionPolicy.OPEN_COMPATIBLE,
                    ProductSalesVisibility.PUBLIC);
            assertThat(noChange.get("applicable").asBoolean()).isFalse();
            assertThat(noChange.get("blockers").toString()).contains("NO_CHANGE");
            assertThat(noChange.get("availableActions").isEmpty()).isTrue();

            JsonNode preview = planPreview(
                    token, flex.getId(), PlanExtensionPolicy.CLOSED,
                    ProductSalesVisibility.PUBLIC);
            assertThat(preview.get("applicable").asBoolean()).isTrue();
            assertThat(preview.get("changedCount").asInt()).isPositive();
            assertThat(preview.get("availableActions").toString())
                    .contains("APPLY_PLAN_AVAILABILITY");
            assertThat(preview.get("affectedSubscriptionCount").asLong()).isNotNegative();

            String request = objectMapper.writeValueAsString(java.util.Map.of(
                    "expectedVersion", preview.get("expectedVersion").asLong(),
                    "extensionPolicy", "CLOSED",
                    "salesVisibility", "PUBLIC",
                    "reason", "Close self-service extensions for a controlled rollout",
                    "previewToken", preview.get("previewToken").asText()));
            CountDownLatch start = new CountDownLatch(1);
            CompletableFuture<HttpResult> first = CompletableFuture.supplyAsync(
                    () -> patchPlanAfter(start, token, flex.getId(), request));
            CompletableFuture<HttpResult> second = CompletableFuture.supplyAsync(
                    () -> patchPlanAfter(start, token, flex.getId(), request));
            start.countDown();
            List<HttpResult> results = List.of(first.join(), second.join());
            assertThat(results).extracting(HttpResult::status)
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(results.stream().filter(result -> result.status() == 409)
                    .map(HttpResult::body).findFirst().orElseThrow())
                    .contains("STALE_RESOURCE_VERSION");

            mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                            .param("search", "custom")
                            .param("available", "false")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].operatorSelectable").value(false))
                    .andExpect(jsonPath("$.content[0].issues[0].reason")
                            .value("PLAN_EXTENSIONS_CLOSED"))
                    .andExpect(jsonPath("$.content[0].issues[0].source")
                            .value("PLAN_POLICY"));

            String history = mockMvc.perform(
                            get("/api/admin/commercial-products/{id}/availability-history", flex.getId())
                                    .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode historyDocument = objectMapper.readTree(history);
            JsonNode historyEntries = historyDocument.get("content");
            assertThat(historyDocument.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
            assertThat(historyEntries).filteredOn(entry ->
                            "SUCCEEDED".equals(entry.get("outcome").asText())
                                    && "Close self-service extensions for a controlled rollout"
                                    .equals(entry.get("reason").asText()))
                    .hasSize(1);
            assertThat(historyEntries).filteredOn(entry ->
                            "SUCCEEDED".equals(entry.get("outcome").asText())
                                    && "Close self-service extensions for a controlled rollout"
                                    .equals(entry.get("reason").asText()))
                    .singleElement().satisfies(entry -> {
                assertThat(entry.get("action").asText())
                        .isEqualTo("platform.commercial_availability.plan_availability_changed");
                assertThat(entry.get("reason").asText())
                        .isEqualTo("Close self-service extensions for a controlled rollout");
                assertThat(entry.get("actorEmail").asText()).isEqualTo("test-admin@hiveapp.local");
                assertThat(entry.get("productType").asText()).isEqualTo("PLAN");
                assertThat(entry.get("productCode").asText()).isEqualTo("FLEX");
                assertThat(entry.get("previousExtensionPolicy").asText())
                        .isEqualTo("OPEN_COMPATIBLE");
                assertThat(entry.get("resultingExtensionPolicy").asText()).isEqualTo("CLOSED");
                assertThat(entry.get("previousSalesVisibility").asText()).isEqualTo("PUBLIC");
                assertThat(entry.get("resultingSalesVisibility").asText()).isEqualTo("PUBLIC");
            });
            assertThat(historyEntries).filteredOn(entry ->
                            "FAILED".equals(entry.get("outcome").asText())
                                    && "Close self-service extensions for a controlled rollout"
                                    .equals(entry.get("reason").asText()))
                    .singleElement().satisfies(entry -> {
                assertThat(entry.get("outcome").asText()).isEqualTo("FAILED");
                assertThat(entry.get("action").asText())
                        .isEqualTo("platform.commercial_availability.update_plan_policy");
                assertThat(entry.get("productType").asText()).isEqualTo("PLAN");
                assertThat(entry.get("productCode").asText()).isEqualTo("FLEX");
                assertThat(entry.get("resultingExtensionPolicy").asText()).isEqualTo("CLOSED");
            });

            String firstPage = mockMvc.perform(
                            get("/api/admin/commercial-products/{id}/availability-history", flex.getId())
                                    .param("page", "0").param("size", "1")
                                    .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String secondPage = mockMvc.perform(
                            get("/api/admin/commercial-products/{id}/availability-history", flex.getId())
                                    .param("page", "1").param("size", "1")
                                    .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String firstPageId = objectMapper.readTree(firstPage)
                    .path("content").get(0).path("id").asText();
            String secondPageId = objectMapper.readTree(secondPage)
                    .path("content").get(0).path("id").asText();
            assertThat(firstPageId).isEqualTo(historyEntries.get(0).path("id").asText());
            assertThat(secondPageId).isEqualTo(historyEntries.get(1).path("id").asText());
            assertThat(firstPageId).isNotEqualTo(secondPageId);
            mockMvc.perform(get(
                                    "/api/admin/commercial-products/{id}/availability-history",
                                    flex.getId())
                            .param("size", "101")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            mockMvc.perform(get(
                                    "/api/admin/commercial-products/{id}/availability-history",
                                    flex.getId())
                            .param("page", "-1")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        } finally {
            resetVisibility();
        }
    }

    @Test
    void directOnlyNeverLeaksButAuthorizedAssignmentAndCurrentHoldingRemainUsable() throws Exception {
        String adminToken = loginAdminAndGetToken();
        resetVisibility();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        var initialSubscription = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        var initialSnapshot = initialSubscription.getEntitlementSnapshot();
        var flex = planRepository.findByCode("FLEX").orElseThrow();
        var customRoles = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        var organizationTools = addOnRepository.findByCode("ORGANIZATION_TOOLS").orElseThrow();
        var members = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
        var originalCustomRolesStatus = customRoles.getStatus();
        var originalMembersStatus = members.getStatus();
        Set<String> originalOrganizationDependencies = Set.copyOf(
                organizationTools.getDependencyCodes());
        Set<UUID> pausedPriceIds = new java.util.LinkedHashSet<>();

        try {
            changeAddOnVisibility(
                    adminToken, customRoles.getId(), ProductSalesVisibility.DIRECT_ONLY,
                    "Make this AddOn operator-assigned only");
            changeQuotaVisibility(
                    adminToken, members.getId(), ProductSalesVisibility.DIRECT_ONLY,
                    "Make this capacity package operator-assigned only");

            JsonNode catalog = clientCatalog(clientToken);
            JsonNode flexCatalog = findByCode(catalog.get("plans"), "FLEX");
            assertThat(flexCatalog).isNotNull();
            assertThat(flexCatalog.get("addOns").toString()).doesNotContain("CUSTOM_ROLES");
            assertThat(flexCatalog.get("quotaPackages").toString()).doesNotContain("MEMBERS_5");
            // Privacy is a serialized-contract invariant: hidden codes must not leak through
            // dependency, exclusion, targeting, reach, or reason fields anywhere in the payload.
            assertThat(catalog.toString()).doesNotContain("CUSTOM_ROLES", "MEMBERS_5");

            ErrorIdentity hiddenAddOn = clientPreviewError(clientToken, new SubscriptionChangeRequest(
                    "FLEX", Set.of("CUSTOM_ROLES"), List.of()));
            ErrorIdentity unknownAddOn = clientPreviewError(clientToken, new SubscriptionChangeRequest(
                    "FLEX", Set.of("DOES_NOT_EXIST"), List.of()));
            assertThat(hiddenAddOn).isEqualTo(unknownAddOn);
            assertThat(clientApplyError(clientToken, new SubscriptionChangeRequest(
                    "FLEX", Set.of("CUSTOM_ROLES"), List.of())))
                    .isEqualTo(clientApplyError(clientToken, new SubscriptionChangeRequest(
                            "FLEX", Set.of("DOES_NOT_EXIST"), List.of())));

            ErrorIdentity hiddenPackage = clientPreviewError(clientToken, new SubscriptionChangeRequest(
                    "FLEX", Set.of(), List.of(new QuotaPackageSelection("MEMBERS_5", 1))));
            ErrorIdentity unknownPackage = clientPreviewError(clientToken, new SubscriptionChangeRequest(
                    "FLEX", Set.of(), List.of(new QuotaPackageSelection("DOES_NOT_EXIST", 1))));
            assertThat(hiddenPackage).isEqualTo(unknownPackage);
            assertThat(clientApplyError(clientToken, new SubscriptionChangeRequest(
                    "FLEX", Set.of(), List.of(new QuotaPackageSelection("MEMBERS_5", 1)))))
                    .isEqualTo(clientApplyError(clientToken, new SubscriptionChangeRequest(
                            "FLEX", Set.of(), List.of(new QuotaPackageSelection("DOES_NOT_EXIST", 1)))));

            changePlanVisibility(
                    adminToken, flex.getId(), ProductSalesVisibility.DIRECT_ONLY,
                    "Reserve this Plan for direct operator assignment");
            assertThat(subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                    .getEntitlementSnapshot()).isEqualTo(initialSnapshot);

            JsonNode hiddenCatalog = clientCatalog(clientToken);
            assertThat(hiddenCatalog.get("plans").toString()).doesNotContain("\"code\":\"FLEX\"");
            ErrorIdentity hiddenPlan = clientPreviewError(
                    clientToken, new SubscriptionChangeRequest("FLEX", Set.of(), List.of()));
            ErrorIdentity unknownPlan = clientPreviewError(
                    clientToken, new SubscriptionChangeRequest("UNKNOWN_PLAN", Set.of(), List.of()));
            assertThat(hiddenPlan).isEqualTo(unknownPlan);
            assertThat(clientApplyError(
                    clientToken, new SubscriptionChangeRequest("FLEX", Set.of(), List.of())))
                    .isEqualTo(clientApplyError(
                            clientToken, new SubscriptionChangeRequest("UNKNOWN_PLAN", Set.of(), List.of())));

            var exactPrice = productPriceRepository.findApplicable(
                    ProductPriceOwnerType.PLAN, flex.getId(), "USD", BillingCycle.MONTHLY,
                    Instant.now()).getFirst();
            mockMvc.perform(post("/api/admin/subscriptions/account/{id}", accountId)
                            .param("planCode", "FLEX")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ProductPriceSelectionRequest(
                                    exactPrice.getId(), "USD", BillingCycle.MONTHLY))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.plan.code").value("FLEX"));

            updateOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                    Set.of("CUSTOM_ROLES"),
                    List.of(new QuotaPackageSelection("MEMBERS_5", 1))));
            var heldBeforePause = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                    .getEntitlementSnapshot();
            transactionTemplate.executeWithoutResult(ignored -> {
                var heldAddOn = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
                heldAddOn.setStatus(com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.INACTIVE);
                addOnRepository.saveAndFlush(heldAddOn);
                var heldPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
                heldPackage.setStatus(
                        com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.INACTIVE);
                quotaPackageRepository.saveAndFlush(heldPackage);
                var heldAddOnPrice = productPriceRepository.findById(
                        heldBeforePause.addOns().getFirst().priceEntryId()).orElseThrow();
                pausedPriceIds.add(heldAddOnPrice.getId());
                heldAddOnPrice.pause();
                productPriceRepository.saveAndFlush(heldAddOnPrice);
                var heldPackagePrice = productPriceRepository.findById(
                        heldBeforePause.quotaPackages().getFirst().priceEntryId()).orElseThrow();
                pausedPriceIds.add(heldPackagePrice.getId());
                heldPackagePrice.pause();
                productPriceRepository.saveAndFlush(heldPackagePrice);
                var heldPlanPrice = productPriceRepository.findById(
                        heldBeforePause.planPriceEntryId()).orElseThrow();
                pausedPriceIds.add(heldPlanPrice.getId());
                heldPlanPrice.pause();
                productPriceRepository.saveAndFlush(heldPlanPrice);
            });
            updateOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                    Set.of("CUSTOM_ROLES"),
                    List.of(
                            new QuotaPackageSelection("MEMBERS_5", 1),
                            new QuotaPackageSelection("COMPANY_1", 1))));
            var retained = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                    .getEntitlementSnapshot();
            assertThat(retained.addOns()).extracting(item -> item.code())
                    .containsExactly("CUSTOM_ROLES");
            assertThat(retained.quotaPackages()).extracting(item -> item.code())
                    .containsExactly("COMPANY_1", "MEMBERS_5");
            assertThat(retained.addOns().getFirst().priceEntryId())
                    .isEqualTo(heldBeforePause.addOns().getFirst().priceEntryId());
            assertThat(retained.planPriceEntryId()).isEqualTo(heldBeforePause.planPriceEntryId());
            assertThat(retained.quotaPackages().stream()
                    .filter(item -> item.code().equals("MEMBERS_5")).findFirst().orElseThrow().priceEntryId())
                    .isEqualTo(heldBeforePause.quotaPackages().getFirst().priceEntryId());

            JsonNode currentDirect = clientCatalog(clientToken);
            assertThat(currentDirect.get("currentSubscription").get("planCode").asText())
                    .isEqualTo("FLEX");
            assertThat(currentDirect.at("/currentSubscription/retainedAddOns/0/code").asText())
                    .isEqualTo("CUSTOM_ROLES");
            assertThat(currentDirect.at("/currentSubscription/retainedAddOns/0/name").asText())
                    .isEqualTo(heldBeforePause.addOns().getFirst().name());
            assertThat(currentDirect.at("/currentSubscription/retainedAddOns/0/state").asText())
                    .isEqualTo("RETAINED_ONLY");
            assertThat(currentDirect.at("/currentSubscription/retainedAddOns/0/removable").asBoolean())
                    .isTrue();
            assertThat(currentDirect.at("/currentSubscription/retainedQuotaPackages/1/code").asText())
                    .isEqualTo("MEMBERS_5");
            assertThat(currentDirect.at("/currentSubscription/retainedQuotaPackages/1/state").asText())
                    .isEqualTo("RETAINED_ONLY");
            assertThat(currentDirect.at("/currentSubscription/retainedQuotaPackages/1/unitPrice")
                    .isTextual()).isTrue();
            mockMvc.perform(get("/api/admin/subscriptions/account/{id}/override-choices/add-ons", accountId)
                            .header("Authorization", bearer(adminToken))
                            .param("search", "custom")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.retainedSelections[0].code").value("CUSTOM_ROLES"))
                    .andExpect(jsonPath("$.retainedSelections[0].state").value("RETAINED_ONLY"))
                    .andExpect(jsonPath("$.retainedSelections[0].removable").value(true))
                    .andExpect(jsonPath("$.retainedSelections[0].unavailabilityReasons").isArray());
            mockMvc.perform(get(
                                    "/api/admin/subscriptions/account/{id}/override-choices/quota-packages",
                                    accountId)
                            .header("Authorization", bearer(adminToken))
                            .param("featureCode", "platform.staff")
                            .param("resource", "members")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.retainedSelections[?(@.code == 'MEMBERS_5')].retainedQuantity")
                            .value(org.hamcrest.Matchers.hasItem(1)))
                    .andExpect(jsonPath("$.retainedSelections[?(@.code == 'MEMBERS_5')].state")
                            .value(org.hamcrest.Matchers.hasItem("RETAINED_ONLY")));

            UUID managedSubscriptionId = subscriptionRepository
                    .findActiveByAccountId(accountId).orElseThrow().getId();
            for (SubscriptionStatus managedStatus : List.of(
                    SubscriptionStatus.TRIALING,
                    SubscriptionStatus.PAST_DUE,
                    SubscriptionStatus.SUSPENDED)) {
                transactionTemplate.executeWithoutResult(ignored -> {
                    var managed = subscriptionRepository.findById(managedSubscriptionId).orElseThrow();
                    managed.setStatus(managedStatus);
                    subscriptionRepository.saveAndFlush(managed);
                });
                mockMvc.perform(get(
                                        "/api/admin/subscriptions/account/{id}/override-choices/add-ons",
                                        accountId)
                                .header("Authorization", bearer(adminToken))
                                .param("size", "1"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.retainedSelections[0].code")
                                .value("CUSTOM_ROLES"));
                mockMvc.perform(get(
                                        "/api/admin/subscriptions/account/{id}/override-choices/quota-packages",
                                        accountId)
                                .header("Authorization", bearer(adminToken))
                                .param("size", "1"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath(
                                "$.retainedSelections[?(@.code == 'MEMBERS_5')].retainedQuantity")
                                .value(org.hamcrest.Matchers.hasItem(1)));
            }
            transactionTemplate.executeWithoutResult(ignored -> {
                var managed = subscriptionRepository.findById(managedSubscriptionId).orElseThrow();
                managed.setStatus(SubscriptionStatus.ACTIVE);
                subscriptionRepository.saveAndFlush(managed);
                var dependencyCandidate = addOnRepository
                        .findByCode("ORGANIZATION_TOOLS").orElseThrow();
                dependencyCandidate.setDependencyCodes(Set.of("CUSTOM_ROLES"));
                addOnRepository.saveAndFlush(dependencyCandidate);
            });
            mockMvc.perform(get(
                                    "/api/admin/subscriptions/account/{id}/override-choices/add-ons",
                                    accountId)
                            .header("Authorization", bearer(adminToken))
                            .param("search", "Organization Tools")
                            .param("useCurrentAddOnSelections", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
            mockMvc.perform(get(
                                    "/api/admin/subscriptions/account/{id}/override-choices/add-ons",
                                    UUID.randomUUID())
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            JsonNode heldPlan = findByCode(currentDirect.get("plans"), "FLEX");
            assertThat(heldPlan).isNotNull();
            assertThat(heldPlan.get("current").asBoolean()).isTrue();
            assertThat(heldPlan.get("selectable").asBoolean()).isFalse();
            assertThat(heldPlan.get("addOns").isEmpty()).isTrue();
            assertThat(heldPlan.get("quotaPackages").isEmpty()).isTrue();
            assertThat(heldPlan.get("prices").isEmpty()).isTrue();
            assertThat(currentDirect.get("plans").toString()).contains("\"code\":\"FREE\"");
            mockMvc.perform(get("/api/v1/subscriptions/me")
                            .header("Authorization", bearer(clientToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plan.code").value("FLEX"));
        } finally {
            transactionTemplate.executeWithoutResult(ignored -> {
                pausedPriceIds.forEach(id -> {
                    var price = productPriceRepository.findById(id).orElseThrow();
                    if (price.getStatus()
                            == com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.INACTIVE) {
                        price.activate();
                        productPriceRepository.save(price);
                    }
                });
                var restoredAddOn = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
                restoredAddOn.setStatus(originalCustomRolesStatus);
                addOnRepository.save(restoredAddOn);
                var restoredPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
                restoredPackage.setStatus(originalMembersStatus);
                quotaPackageRepository.save(restoredPackage);
                var restoredCandidate = addOnRepository
                        .findByCode("ORGANIZATION_TOOLS").orElseThrow();
                restoredCandidate.setDependencyCodes(originalOrganizationDependencies);
                addOnRepository.save(restoredCandidate);
                subscriptionRepository.findTopByAccountIdAndStatusInOrderByCreatedAtDesc(
                                accountId, List.of(
                                        SubscriptionStatus.ACTIVE,
                                        SubscriptionStatus.TRIALING,
                                        SubscriptionStatus.PAST_DUE,
                                        SubscriptionStatus.SUSPENDED))
                        .ifPresent(subscription -> {
                            subscription.setStatus(SubscriptionStatus.ACTIVE);
                            subscriptionRepository.save(subscription);
                        });
            });
            resetVisibility();
        }
    }

    @Test
    void equalCountPriceIdentityChurnInvalidatesVisibilityPreview() throws Exception {
        String token = loginAdminAndGetToken();
        var addOn = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        var original = productPriceRepository.findApplicable(
                ProductPriceOwnerType.ADD_ON, addOn.getId(), "USD", BillingCycle.MONTHLY,
                Instant.now()).getFirst();
        String previewBody = mockMvc.perform(post(
                                "/api/admin/add-ons/{id}/sales-visibility/preview", addOn.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salesVisibility\":\"DIRECT_ONLY\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode preview = objectMapper.readTree(previewBody);
        UUID[] replacementId = new UUID[1];
        try {
            transactionTemplate.executeWithoutResult(ignored -> {
                var managedOriginal = productPriceRepository.findById(original.getId()).orElseThrow();
                managedOriginal.pause();
                productPriceRepository.saveAndFlush(managedOriginal);
                ProductPrice replacement = ProductPrice.draft(
                        addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow(),
                        original.money(),
                        BillingCycle.MONTHLY, Instant.EPOCH, null);
                replacement.activate();
                replacementId[0] = productPriceRepository.saveAndFlush(replacement).getId();
            });

            mockMvc.perform(patch("/api/admin/add-ons/{id}/sales-visibility", addOn.getId())
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "expectedVersion", preview.get("expectedVersion").asLong(),
                                    "salesVisibility", "DIRECT_ONLY",
                                    "reason", "Must reject an equal-count price replacement",
                                    "previewToken", preview.get("previewToken").asText()))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        } finally {
            transactionTemplate.executeWithoutResult(ignored -> {
                if (replacementId[0] != null) {
                    var replacement = productPriceRepository.findById(replacementId[0]).orElseThrow();
                    if (replacement.getStatus()
                            == com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE) {
                        replacement.pause();
                    }
                    productPriceRepository.delete(replacement);
                }
                var managedOriginal = productPriceRepository.findById(original.getId()).orElseThrow();
                if (managedOriginal.getStatus()
                        == com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.INACTIVE) {
                    managedOriginal.activate();
                    productPriceRepository.save(managedOriginal);
                }
            });
        }
    }

    @Test
    void compatibilityInspectionCombinesOuterPlanBlockers() throws Exception {
        String token = loginAdminAndGetToken();
        var flex = planRepository.findByCode("FLEX").orElseThrow();
        try {
            flex.setStatus(PlanStatus.INACTIVE);
            planRepository.saveAndFlush(flex);

            mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                            .param("search", "custom")
                            .param("available", "true")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
            mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                            .param("search", "custom")
                            .param("available", "false")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].operatorSelectable").value(false))
                    .andExpect(jsonPath("$.content[0].issues[?(@.reason == 'PRODUCT_NOT_ACTIVE')]")
                            .isNotEmpty());
        } finally {
            var managed = planRepository.findByCode("FLEX").orElseThrow();
            managed.setStatus(PlanStatus.ACTIVE);
            planRepository.saveAndFlush(managed);
        }
    }

    @Test
    void compatibilityInspectionHonorsSafeSortsAndRejectsUnboundedOrUnknownOnes() throws Exception {
        String token = loginAdminAndGetToken();
        var flex = planRepository.findByCode("FLEX").orElseThrow();

        String body = mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                        .param("sort", "name,desc")
                        .param("size", "100")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> names = new java.util.ArrayList<>();
        objectMapper.readTree(body).get("content").forEach(item -> names.add(item.get("name").asText()));
        assertThat(names).hasSizeGreaterThan(1);
        assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER.reversed());

        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                        .param("search", "CUSTOM_ROLES")
                        .param("type", "ADD_ON")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].featureCodes").isNotEmpty())
                .andExpect(jsonPath("$.content[0].quotaFeatureCode").doesNotExist());
        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                        .param("search", "MEMBERS_5")
                        .param("type", "QUOTA_PACKAGE")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].featureCodes[0]").value("platform.staff"))
                .andExpect(jsonPath("$.content[0].quotaFeatureCode").value("platform.staff"))
                .andExpect(jsonPath("$.content[0].quotaResource").value("members"))
                .andExpect(jsonPath("$.content[0].capacityPerUnit").isNumber())
                .andExpect(jsonPath("$.content[0].maximumQuantity").isNumber());

        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                        .param("size", "101")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                        .param("page", "-1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                        .param("sort", "status,asc")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private JsonNode planPreview(
            String token,
            UUID planId,
            PlanExtensionPolicy policy,
            ProductSalesVisibility visibility
    ) throws Exception {
        String response = mockMvc.perform(
                        post("/api/admin/plans/{id}/commercial-availability/preview", planId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(java.util.Map.of(
                                        "extensionPolicy", policy,
                                        "salesVisibility", visibility))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void changePlanVisibility(
            String token, UUID id, ProductSalesVisibility visibility, String reason) throws Exception {
        var plan = planRepository.findById(id).orElseThrow();
        JsonNode preview = planPreview(token, id, plan.getExtensionPolicy(), visibility);
        applyPlanPreview(token, id, preview, plan.getExtensionPolicy(), visibility, reason);
    }

    private void applyPlanPreview(
            String token,
            UUID id,
            JsonNode preview,
            PlanExtensionPolicy policy,
            ProductSalesVisibility visibility,
            String reason
    ) throws Exception {
        mockMvc.perform(patch("/api/admin/plans/{id}/commercial-availability", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "expectedVersion", preview.get("expectedVersion").asLong(),
                                "extensionPolicy", policy,
                                "salesVisibility", visibility,
                                "reason", reason,
                                "previewToken", preview.get("previewToken").asText()))))
                .andExpect(status().isOk());
    }

    private void changeAddOnVisibility(
            String token, UUID id, ProductSalesVisibility visibility, String reason) throws Exception {
        changeProductVisibility(token, "add-ons", id, visibility, reason);
    }

    private void changeQuotaVisibility(
            String token, UUID id, ProductSalesVisibility visibility, String reason) throws Exception {
        changeProductVisibility(token, "quota-packages", id, visibility, reason);
    }

    private void changeProductVisibility(
            String token, String path, UUID id, ProductSalesVisibility visibility, String reason)
            throws Exception {
        String previewBody = mockMvc.perform(
                        post("/api/admin/{path}/{id}/sales-visibility/preview", path, id)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(java.util.Map.of(
                                        "salesVisibility", visibility))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode preview = objectMapper.readTree(previewBody);
        mockMvc.perform(patch("/api/admin/{path}/{id}/sales-visibility", path, id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "expectedVersion", preview.get("expectedVersion").asLong(),
                                "salesVisibility", visibility,
                                "reason", reason,
                                "previewToken", preview.get("previewToken").asText()))))
                .andExpect(status().isOk());
    }

    private ErrorIdentity clientPreviewError(String token, SubscriptionChangeRequest request)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/subscriptions/preview")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("The requested commercial selection is unavailable."))
                .andReturn();
        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString());
        return new ErrorIdentity(
                result.getResponse().getStatus(), error.get("code").asText(), error.get("message").asText());
    }

    private ErrorIdentity clientApplyError(String token, SubscriptionChangeRequest request)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/subscriptions/apply")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("The requested commercial selection is unavailable."))
                .andReturn();
        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString());
        return new ErrorIdentity(
                result.getResponse().getStatus(), error.get("code").asText(), error.get("message").asText());
    }

    private JsonNode clientCatalog(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void updateOverrides(
            String token, UUID accountId, UpdateSubscriptionOverridesRequest request) throws Exception {
        mockMvc.perform(patch("/api/admin/subscriptions/account/{id}/overrides", accountId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private JsonNode findByCode(JsonNode items, String code) {
        for (JsonNode item : items) {
            if (code.equals(item.get("code").asText())) return item;
        }
        return null;
    }

    private UUID currentAccountId(String clientToken) throws Exception {
        String response = mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private HttpResult patchPlanAfter(CountDownLatch start, String token, UUID id, String request) {
        try {
            start.await();
            MvcResult result = mockMvc.perform(patch(
                            "/api/admin/plans/{id}/commercial-availability", id)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andReturn();
            return new HttpResult(
                    result.getResponse().getStatus(), result.getResponse().getContentAsString());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void resetVisibility() {
        transactionTemplate.executeWithoutResult(ignored -> {
            var plan = planRepository.findByCode("FLEX").orElseThrow();
            plan.setExtensionPolicy(PlanExtensionPolicy.OPEN_COMPATIBLE);
            plan.setSalesVisibility(ProductSalesVisibility.PUBLIC);
            planRepository.saveAndFlush(plan);
            var addOn = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
            addOn.setSalesVisibility(ProductSalesVisibility.PUBLIC);
            addOn.setStatus(com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.ACTIVE);
            addOnRepository.saveAndFlush(addOn);
            var quota = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
            quota.setSalesVisibility(ProductSalesVisibility.PUBLIC);
            quota.setStatus(com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.ACTIVE);
            quotaPackageRepository.saveAndFlush(quota);
        });
    }

    private record HttpResult(int status, String body) {}
    private record ErrorIdentity(int status, String code, String message) {}
}
