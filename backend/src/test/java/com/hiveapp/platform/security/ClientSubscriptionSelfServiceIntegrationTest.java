package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentKind;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientSubscriptionSelfServiceIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private SubscriptionChangeOperationRepository subscriptionChangeOperationRepository;

    @Autowired
    private BillingInvoiceRepository billingInvoiceRepository;

    @Autowired
    private BillingPaymentAttemptRepository billingPaymentAttemptRepository;

    @Autowired
    private BillingOutboxCommandRepository billingOutboxCommandRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void removedLegacyPlanCatalogueCannotBypassTheGuardedClientCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isUnauthorized());

        String clientToken = registerClientAndGetToken();
        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isNotFound());

        String adminToken = loginAdminAndGetToken();
        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogShowsSafePlanDataAndCurrentUsageOnly() throws Exception {
        String token = registerClientAndGetToken();
        createCompany(token, "Catalog Usage Company");

        String response = mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSubscription.planCode").value("FREE"))
                .andExpect(jsonPath("$.currentSubscription.currentPriceCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.plans[*].currencyCode", everyItem(org.hamcrest.Matchers.is("USD"))))
                .andExpect(jsonPath(
                        "$.plans[?(@.code == 'PRO')].features[?(@.featureCode == 'platform.staff')]"
                                + ".quotas[?(@.slot.resource == 'members')].mode",
                        hasItem("FINITE")))
                .andExpect(jsonPath("$.plans[0].features[*].featureCode").value(not(containsString("platform.plans"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("platform.workspace");
        assertThat(response).doesNotContain("platform.plans");
        assertThat(response).contains("\"currentUsage\":1");
        JsonNode catalog = objectMapper.readTree(response);
        assertThat(catalog.path("currentSubscription").path("currentPrice").isTextual()).isTrue();
        catalog.path("plans").forEach(plan -> {
            assertThat(plan.path("basePrice").isTextual()).isTrue();
            plan.path("prices").forEach(price ->
                    assertThat(price.path("amount").isTextual()).isTrue());
            plan.path("addOns").forEach(addOn -> {
                assertThat(addOn.path("price").isTextual()).isTrue();
                addOn.path("prices").forEach(price ->
                        assertThat(price.path("amount").isTextual()).isTrue());
            });
            plan.path("quotaPackages").forEach(item -> {
                assertThat(item.path("price").isTextual()).isTrue();
                item.path("prices").forEach(price ->
                        assertThat(price.path("amount").isTextual()).isTrue());
            });
        });
    }

    @Test
    void operatorChangeCatalogCanSelectDirectOnlyPlansWithoutExposingThemToClients() throws Exception {
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        String adminToken = loginAdminAndGetToken();
        var pro = planRepository.findByCode("PRO").orElseThrow();
        ProductSalesVisibility original = pro.getSalesVisibility();

        try {
            pro.setSalesVisibility(ProductSalesVisibility.DIRECT_ONLY);
            planRepository.saveAndFlush(pro);

            mockMvc.perform(get("/api/v1/subscriptions/catalog")
                            .header("Authorization", bearer(clientToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plans[?(@.code == 'PRO')]").isEmpty());
            preview(clientToken, new SubscriptionChangeRequest("PRO", Set.of(), List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "The requested commercial selection is unavailable."));

            mockMvc.perform(get(
                                    "/api/admin/subscriptions/account/{accountId}/change-catalog",
                                    accountId)
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plans[?(@.code == 'PRO')].selectable").value(true));
            mockMvc.perform(post(
                                    "/api/admin/subscriptions/account/{accountId}/changes/preview",
                                    accountId)
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SubscriptionChangeRequest(
                                    "PRO", Set.of(), List.of(), SubscriptionChangeTiming.AT_RENEWAL))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.targetPlanCode").value("PRO"));
        } finally {
            var restored = planRepository.findByCode("PRO").orElseThrow();
            restored.setSalesVisibility(original);
            planRepository.saveAndFlush(restored);
        }
    }

    @Test
    void previewRejectsInactivePlanAndControlPlaneAddOn() throws Exception {
        String token = registerClientAndGetToken();
        var pro = planRepository.findByCode("PRO").orElseThrow();
        boolean originalActive = pro.isActive();

        try {
            pro.setStatus(PlanStatus.INACTIVE);
            planRepository.saveAndFlush(pro);

            preview(token, new SubscriptionChangeRequest("PRO", Set.of(), List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message")
                            .value("The requested commercial selection is unavailable."));
        } finally {
            var currentPro = planRepository.findByCode("PRO").orElseThrow();
            currentPro.setStatus(originalActive ? PlanStatus.ACTIVE : PlanStatus.INACTIVE);
            planRepository.saveAndFlush(currentPro);
        }

        preview(token, new SubscriptionChangeRequest("FREE", Set.of("platform.plans"), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("The requested commercial selection is unavailable."));
    }

    @Test
    void malformedSelectionElementsAreRejectedWithoutReachingCommercialResolution() throws Exception {
        String token = registerClientAndGetToken();

        mockMvc.perform(post("/api/v1/subscriptions/preview")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetPlanCode":"FLEX","addOnCodes":[null],"quotaPackages":[]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/subscriptions/preview")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetPlanCode":"FLEX","addOnCodes":[],"quotaPackages":[null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void subscriptionReviewEvidenceIsTamperActorAccountAndSelectionBound() throws Exception {
        String firstClient = registerClientAndGetToken();
        var reviewed = new SubscriptionChangeRequest("PRO", Set.of(), List.of());
        String previewResponse = preview(firstClient, reviewed)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String evidence = objectMapper.readTree(previewResponse).get("previewToken").asText();

        int offset = evidence.indexOf('.') + 4;
        char replacement = evidence.charAt(offset) == 'A' ? 'B' : 'A';
        String tampered = evidence.substring(0, offset) + replacement + evidence.substring(offset + 1);
        applyWithToken(firstClient, reviewed, tampered)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));

        applyWithToken(firstClient,
                new SubscriptionChangeRequest("ENTERPRISE", Set.of(), List.of()), evidence)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));

        String secondClient = registerClientAndGetToken();
        applyWithToken(secondClient, reviewed, evidence)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));

        mockMvc.perform(get("/api/v1/subscriptions/changes")
                        .header("Authorization", bearer(firstClient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/v1/subscriptions/changes")
                        .header("Authorization", bearer(secondClient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void internalFeatureIsHiddenFromCatalogAndCannotBeSelected() throws Exception {
        String token = registerClientAndGetToken();
        var company = featureRepository.findByCode("platform.company").orElseThrow();
        FeatureStatus originalStatus = company.getStatus();

        try {
            company.setStatus(FeatureStatus.INTERNAL);
            featureRepository.saveAndFlush(company);

            String response = mockMvc.perform(get("/api/v1/subscriptions/catalog")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(response).doesNotContain("platform.company");

            preview(token, new SubscriptionChangeRequest("PRO", Set.of("platform.company"), List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message")
                            .value("The requested commercial selection is unavailable."));
        } finally {
            company.setStatus(originalStatus);
            featureRepository.saveAndFlush(company);
        }
    }

    @Test
    void previewReportsQuotaConflictAndApplyRejectsItWithoutMutatingSubscription() throws Exception {
        String token = registerClientAndGetToken();
        UUID accountId = currentAccountId(token);
        var free = planRepository.findByCode("FREE").orElseThrow();
        var staff = planFeatureRepository
                .findByPlanIdAndFeature_Code(free.getId(), StaffFeature.CODE).orElseThrow();
        List<QuotaLimitEntry> original = List.copyOf(staff.getQuotaConfigs());
        try {
            staff.setQuotaConfigs(List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 0L)));
            planFeatureRepository.saveAndFlush(staff);
            var request = new SubscriptionChangeRequest("FREE", Set.of(), List.of());

            preview(token, request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.immediateAllowed").value(false))
                    .andExpect(jsonPath("$.conflicts[0].code").value("QUOTA_BELOW_USAGE"));

            apply(token, request)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "Subscription change cannot be applied until conflicts are resolved."));

            assertThat(subscriptionRepository.findAllByAccountIdAndStatusIn(
                    accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                    .hasSize(1)
                    .first()
                    .extracting(subscription -> subscription.getEntitlementSnapshot().planCode())
                    .isEqualTo("FREE");
        } finally {
            staff.setQuotaConfigs(original);
            planFeatureRepository.saveAndFlush(staff);
        }
    }

    @Test
    void paidApplyWaitsForManualConfirmationThenRevalidatesAndActivates() throws Exception {
        String token = registerClientAndGetToken();
        UUID accountId = currentAccountId(token);

        String applyResponse = apply(token, new SubscriptionChangeRequest("PRO", Set.of(), List.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.plan.code").value("FREE"))
                .andExpect(jsonPath("$.preview.currencyCode").value("USD"))
                .andExpect(jsonPath("$.preview.immediateAllowed").value(true))
                .andExpect(jsonPath("$.operation.status").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.operation.checkout.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.operation.checkout.gatewayAttemptStatus").value("PENDING"))
                .andExpect(jsonPath("$.operation.checkout.gatewayReference").doesNotExist())
                .andExpect(jsonPath("$.operation.checkout.gatewayFailureReason").doesNotExist())
                .andExpect(jsonPath("$.operation.checkout.confirmationSource").doesNotExist())
                .andExpect(jsonPath("$.operation.checkout.confirmationReference").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID checkoutId = UUID.fromString(objectMapper.readTree(applyResponse)
                .get("operation").get("checkout").get("id").asText());

        var invoice = billingInvoiceRepository.findByCheckoutId(checkoutId).orElseThrow();
        assertThat(invoice.getStatus()).isEqualTo(BillingInvoiceStatus.OPEN);
        assertThat(invoice.getLines()).isNotEmpty();
        assertThat(invoice.getLines().stream()
                .map(line -> line.getLineAmount())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo(invoice.getTotalAmount());
        assertThat(billingPaymentAttemptRepository.findAllByInvoiceIdOrderByCreatedAtDesc(invoice.getId()))
                .singleElement()
                .extracting(payment -> payment.getKind())
                .isEqualTo(BillingPaymentKind.PROVIDER);
        assertThat(billingOutboxCommandRepository.findAll())
                .anySatisfy(command -> {
                    assertThat(command.getAggregateId()).isNotNull();
                    assertThat(command.getStatus()).isIn(
                            BillingOutboxStatus.PENDING,
                            BillingOutboxStatus.PROCESSING,
                            BillingOutboxStatus.PROCESSED);
                });

        assertThat(subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                .getEntitlementSnapshot().planCode()).isEqualTo("FREE");

        String adminToken = loginAdminAndGetToken();
        mockMvc.perform(get("/api/admin/subscriptions/account/{accountId}/changes", accountId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].checkout.id").value(checkoutId.toString()));

        var confirmation = Map.of(
                "reference", "manual-contract-" + checkoutId,
                "reason", "Authorized operator confirmed the external settlement");
        mockMvc.perform(post("/api/admin/subscriptions/checkouts/{checkoutId}/confirm-manual", checkoutId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmationSource").value("MANUAL_OPERATOR"));

        assertThat(billingInvoiceRepository.findByCheckoutId(checkoutId).orElseThrow().getStatus())
                .isEqualTo(BillingInvoiceStatus.SETTLED);
        assertThat(billingPaymentAttemptRepository.findAllByInvoiceIdOrderByCreatedAtDesc(invoice.getId()))
                .extracting(payment -> payment.getKind())
                .containsExactlyInAnyOrder(BillingPaymentKind.PROVIDER, BillingPaymentKind.MANUAL);
        var cancelledProviderPayment = billingPaymentAttemptRepository
                .findAllByInvoiceIdOrderByCreatedAtDesc(invoice.getId()).stream()
                .filter(payment -> payment.getKind() == BillingPaymentKind.PROVIDER)
                .findFirst().orElseThrow();
        assertThat(cancelledProviderPayment)
                .extracting(payment -> payment.getStatus())
                .isEqualTo(com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus.CANCELLED);
        assertThat(billingOutboxCommandRepository.findAll())
                .filteredOn(command -> command.getAggregateId().equals(cancelledProviderPayment.getId()))
                .singleElement()
                .extracting(command -> command.getStatus())
                .isEqualTo(BillingOutboxStatus.CANCELLED);

        // The same reference is an idempotent acknowledgement, not a second activation.
        mockMvc.perform(post("/api/admin/subscriptions/checkouts/{checkoutId}/confirm-manual", checkoutId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/subscriptions/changes")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].checkout.id").value(checkoutId.toString()))
                .andExpect(jsonPath("$.content[0].checkout.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].checkout.gatewayReference").doesNotExist())
                .andExpect(jsonPath("$.content[0].checkout.gatewayFailureReason").doesNotExist())
                .andExpect(jsonPath("$.content[0].checkout.confirmationSource").doesNotExist())
                .andExpect(jsonPath("$.content[0].checkout.confirmationReference").doesNotExist());

        var usable = subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING));
        assertThat(usable).hasSize(1);
        assertThat(usable.getFirst().getEntitlementSnapshot().planCode()).isEqualTo("PRO");
    }

    @Test
    void explicitlyZeroPricedChangeActivatesWithoutCreatingFakePayment() throws Exception {
        String token = registerClientAndGetToken();
        var pro = planRepository.findByCode("PRO").orElseThrow();
        ProductPrice zeroPrice = ProductPrice.draft(
                pro, Money.zero(pro.getCurrencyCode()), pro.getBillingCycle(), Instant.EPOCH, null);
        zeroPrice.activate();
        productPriceRepository.saveAndFlush(zeroPrice);
        try {
            var selection = new ProductPriceSelectionRequest(zeroPrice.getId(), null, null);
            apply(token, new SubscriptionChangeRequest(
                    "PRO", Set.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE, selection))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.subscription.plan.code").value("PRO"))
                    .andExpect(jsonPath("$.operation.status").value("APPLIED"))
                    .andExpect(jsonPath("$.operation.checkout").doesNotExist());
        } finally {
            productPriceRepository.deleteById(zeroPrice.getId());
            productPriceRepository.flush();
        }
    }

    @Test
    void renewalChangeStaysPendingAndCanBeCancelledWithoutChangingCurrentAccess() throws Exception {
        String token = registerClientAndGetToken();
        var request = new SubscriptionChangeRequest(
                "PRO", Set.of(), List.of(), SubscriptionChangeTiming.AT_RENEWAL);

        String applyResponse = apply(token, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.plan.code").value("FREE"))
                .andExpect(jsonPath("$.operation.timing").value("AT_RENEWAL"))
                .andExpect(jsonPath("$.operation.status").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.operation.checkout.status").value("PENDING_CONFIRMATION"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID operationId = UUID.fromString(objectMapper.readTree(applyResponse).get("operation").get("id").asText());

        mockMvc.perform(get("/api/v1/subscriptions/changes")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(operationId.toString()))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].status").value("AWAITING_CONFIRMATION"));

        mockMvc.perform(delete("/api/v1/subscriptions/changes/{operationId}", operationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.checkout.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.code").value("FREE"));
    }

    @Test
    void adminCanReviewApplyAndCancelAnAccountChangeWithExplicitReasons() throws Exception {
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        String adminToken = loginAdminAndGetToken();
        var selection = new SubscriptionChangeRequest(
                "PRO", Set.of(), List.of(), SubscriptionChangeTiming.AT_RENEWAL);

        String previewBody = mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{accountId}/changes/preview", accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selection)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlanCode").value("FREE"))
                .andExpect(jsonPath("$.targetPlanCode").value("PRO"))
                .andExpect(jsonPath("$.previewToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String previewToken = objectMapper.readTree(previewBody).get("previewToken").asText();

        var reviewedChange = Map.of(
                "selection", selection,
                "previewToken", previewToken,
                "reason", "Customer contract approved by the commercial operator");
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}/changes/apply", accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "selection", selection,
                                "previewToken", previewToken))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String applyBody = mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{accountId}/changes/apply", accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewedChange)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.plan.code").value("FREE"))
                .andExpect(jsonPath("$.operation.timing").value("AT_RENEWAL"))
                .andExpect(jsonPath("$.operation.status").value("AWAITING_CONFIRMATION"))
                .andReturn().getResponse().getContentAsString();
        UUID operationId = UUID.fromString(
                objectMapper.readTree(applyBody).path("operation").path("id").asText());
        UUID checkoutId = UUID.fromString(
                objectMapper.readTree(applyBody).path("operation").path("checkout").path("id").asText());
        var createdOperation = subscriptionChangeOperationRepository.findById(operationId).orElseThrow();
        assertThat(createdOperation.getRequestOrigin())
                .isEqualTo(SubscriptionChangeOrigin.PLATFORM_ADMIN);
        assertThat(createdOperation.getRequestedByUserId()).isNotNull();
        UUID adminActorId = createdOperation.getRequestedByUserId();
        assertThat(createdOperation.getRequestReason())
                .isEqualTo("Customer contract approved by the commercial operator");

        mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{accountId}/changes/{operationId}/cancel",
                                accountId, operationId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{accountId}/changes/{operationId}/cancel",
                                accountId, operationId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer withdrew the approved change\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.checkout.status").value("CANCELLED"));

        var cancelledOperation = subscriptionChangeOperationRepository.findById(operationId).orElseThrow();
        assertThat(cancelledOperation.getCancellationOrigin())
                .isEqualTo(SubscriptionChangeOrigin.PLATFORM_ADMIN);
        assertThat(cancelledOperation.getCancelledByUserId()).isEqualTo(adminActorId);
        assertThat(cancelledOperation.getCancellationReason())
                .isEqualTo("Customer withdrew the approved change");
        assertThat(cancelledOperation.getCancelledAt()).isNotNull();
        var cancelledInvoice = billingInvoiceRepository.findByCheckoutId(checkoutId).orElseThrow();
        assertThat(cancelledInvoice.getStatus()).isEqualTo(BillingInvoiceStatus.CANCELLED);
        assertThat(billingPaymentAttemptRepository
                .findAllByInvoiceIdOrderByCreatedAtDesc(cancelledInvoice.getId()))
                .singleElement()
                .extracting(payment -> payment.getStatus())
                .isEqualTo(com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus.CANCELLED);

        mockMvc.perform(get("/api/admin/subscriptions/account/{accountId}/changes", accountId)
                        .header("Authorization", bearer(adminToken))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(operationId.toString()))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].requestOrigin").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.content[0].requestedByUserId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].requestReason").value(
                        "Customer contract approved by the commercial operator"))
                .andExpect(jsonPath("$.content[0].cancellationOrigin").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.content[0].cancellationReason").value(
                        "Customer withdrew the approved change"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/admin/subscriptions/account/{accountId}/changes", accountId)
                        .header("Authorization", bearer(adminToken))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{accountId}/changes/{operationId}/cancel",
                                accountId, operationId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Repeated cancellation must be audited\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        var auditLogs = auditLogRepository.findAllByTargetAccountIdOrderByOccurredAtDesc(accountId);
        assertThat(auditLogs).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("platform.subscriptions.apply_change");
            assertThat(log.getOutcome()).isEqualTo(AuditOutcome.SUCCEEDED);
            assertThat(log.getActorUserId()).isEqualTo(adminActorId);
            assertThat(log.getRequestData())
                    .contains("Customer contract approved by the commercial operator")
                    .contains("[REDACTED]")
                    .doesNotContain(previewToken);
        });
        assertThat(auditLogs).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("platform.subscriptions.cancel_change");
            assertThat(log.getOutcome()).isEqualTo(AuditOutcome.SUCCEEDED);
            assertThat(log.getActorUserId()).isEqualTo(adminActorId);
            assertThat(log.getRequestData()).contains("Customer withdrew the approved change");
        });
        assertThat(auditLogs).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("platform.subscriptions.cancel_change");
            assertThat(log.getOutcome()).isEqualTo(AuditOutcome.FAILED);
            assertThat(log.getRequestData()).contains("Repeated cancellation must be audited");
        });

        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.code").value("FREE"));
        mockMvc.perform(get("/api/v1/subscriptions/changes")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(operationId.toString()))
                .andExpect(jsonPath("$.content[0].requestReason").doesNotExist())
                .andExpect(jsonPath("$.content[0].requestedByUserId").doesNotExist())
                .andExpect(jsonPath("$.content[0].cancellationReason").doesNotExist());
        mockMvc.perform(get("/api/v1/subscriptions/changes")
                        .header("Authorization", bearer(clientToken))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void clientAndAdminSubscriptionEvidenceCannotReplayAcrossSurfaces() throws Exception {
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        String adminToken = loginAdminAndGetToken();
        var selection = new SubscriptionChangeRequest(
                "PRO", Set.of(), List.of(), SubscriptionChangeTiming.AT_RENEWAL);

        String clientPreview = preview(clientToken, selection)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String clientEvidence = objectMapper.readTree(clientPreview).path("previewToken").asText();
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}/changes/apply", accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "selection", selection,
                                "previewToken", clientEvidence,
                                "reason", "Cross-surface replay must fail"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));

        String adminPreview = mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{accountId}/changes/preview", accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selection)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String adminEvidence = objectMapper.readTree(adminPreview).path("previewToken").asText();
        applyWithToken(clientToken, selection, adminEvidence)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));

        assertThat(subscriptionChangeOperationRepository
                .findAllByAccountId(
                        accountId,
                        org.springframework.data.domain.PageRequest.of(0, 1)))
                .isEmpty();
    }

    @Test
    void retiredDirectTrialRouteCannotReplaceTheClientSubscription() throws Exception {
        String token = registerClientAndGetToken();
        UUID accountId = currentAccountId(token);
        String adminToken = loginAdminAndGetToken();
        UUID beforeId = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow().getId();

        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}/trial", accountId)
                        .param("planCode", "PRO")
                        .param("trialDays", "14")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(beforeId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan.code").value("FREE"));
    }

    @Test
    void concurrentPaidChangesCreateOnlyOneOutstandingCheckout() throws Exception {
        String token = registerClientAndGetToken();
        UUID accountId = currentAccountId(token);

        CompletableFuture<Integer> pro = applyAsync(token, "PRO");
        CompletableFuture<Integer> enterprise = applyAsync(token, "ENTERPRISE");

        assertThat(List.of(pro.join(), enterprise.join())).containsExactlyInAnyOrder(201, 409);
        assertThat(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .singleElement()
                .satisfies(subscription -> assertThat(
                        subscription.getEntitlementSnapshot().planCode()).isEqualTo("FREE"));
    }

    private org.springframework.test.web.servlet.ResultActions preview(
            String token,
            SubscriptionChangeRequest request
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/subscriptions/preview")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions apply(
            String token,
            SubscriptionChangeRequest request
    ) throws Exception {
        String previewResponse = preview(token, request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String previewToken = objectMapper.readTree(previewResponse).get("previewToken").asText();
        return applyWithToken(token, request, previewToken);
    }

    private org.springframework.test.web.servlet.ResultActions applyWithToken(
            String token,
            SubscriptionChangeRequest request,
            String previewToken
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/subscriptions/apply")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "selection", request,
                        "previewToken", previewToken))));
    }

    private CompletableFuture<Integer> applyAsync(String token, String planCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apply(token, new SubscriptionChangeRequest(planCode, Set.of(), List.of()))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private UUID currentAccountId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", containsString("-")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
