package com.hiveapp.platform.security;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.money.Money;
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
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Inactive plans cannot be selected."));
        } finally {
            var currentPro = planRepository.findByCode("PRO").orElseThrow();
            currentPro.setStatus(originalActive ? PlanStatus.ACTIVE : PlanStatus.INACTIVE);
            planRepository.saveAndFlush(currentPro);
        }

        preview(token, new SubscriptionChangeRequest("FREE", Set.of("platform.plans"), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("One or more selected AddOns do not exist."));
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
                    .andExpect(jsonPath("$.message").value("One or more selected AddOns do not exist."));
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
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID checkoutId = UUID.fromString(objectMapper.readTree(applyResponse)
                .get("operation").get("checkout").get("id").asText());

        assertThat(subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                .getEntitlementSnapshot().planCode()).isEqualTo("FREE");

        String adminToken = loginAdminAndGetToken();
        mockMvc.perform(get("/api/admin/subscriptions/account/{accountId}/changes", accountId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].checkout.id").value(checkoutId.toString()));

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

        // The same reference is an idempotent acknowledgement, not a second activation.
        mockMvc.perform(post("/api/admin/subscriptions/checkouts/{checkoutId}/confirm-manual", checkoutId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

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
                .andExpect(jsonPath("$[0].id").value(operationId.toString()))
                .andExpect(jsonPath("$[0].status").value("AWAITING_CONFIRMATION"));

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
    void adminCreatedTrialIsVisibleToTheAccountWithExplicitBounds() throws Exception {
        String token = registerClientAndGetToken();
        UUID accountId = currentAccountId(token);
        String adminToken = loginAdminAndGetToken();

        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}/trial", accountId)
                        .param("planCode", "PRO")
                        .param("trialDays", "14")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TRIALING"))
                .andExpect(jsonPath("$.currentPeriodStart").isNotEmpty())
                .andExpect(jsonPath("$.currentPeriodEnd").isNotEmpty());

        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIALING"))
                .andExpect(jsonPath("$.plan.code").value("PRO"));
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
        return mockMvc.perform(post("/api/v1/subscriptions/apply")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
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
