package com.hiveapp.platform.security;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.quota.QuotaOverride;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientSubscriptionSelfServiceIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

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
            pro.setActive(false);
            planRepository.saveAndFlush(pro);

            preview(token, new SubscriptionChangeRequest("PRO", Set.of(), List.of()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Inactive plans cannot be selected."));
        } finally {
            pro.setActive(originalActive);
            planRepository.saveAndFlush(pro);
        }

        preview(token, new SubscriptionChangeRequest("FREE", Set.of("platform.plans"), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Feature platform.plans cannot be assigned to billing configuration."));
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
                    .andExpect(jsonPath("$.message").value("Feature platform.company is not available for billing configuration."));
        } finally {
            company.setStatus(originalStatus);
            featureRepository.saveAndFlush(company);
        }
    }

    @Test
    void previewReportsQuotaConflictAndApplyRejectsItWithoutMutatingSubscription() throws Exception {
        String token = registerClientAndGetToken();
        UUID accountId = currentAccountId(token);

        var request = new SubscriptionChangeRequest(
                "FREE",
                Set.of(),
                List.of(new QuotaOverride(WorkspaceFeature.CODE, WorkspaceFeature.MEMBERS, 0L)));

        preview(token, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.immediateAllowed").value(false))
                .andExpect(jsonPath("$.conflicts[0].code").value("QUOTA_BELOW_USAGE"));

        apply(token, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Subscription change cannot be applied until conflicts are resolved."));

        assertThat(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .hasSize(1)
                .first()
                .extracting(subscription -> subscription.getEntitlementSnapshot())
                .asString()
                .contains("\"planCode\":\"FREE\"");
    }

    @Test
    void applyCreatesReplacementSnapshotAndLeavesOneUsableSubscription() throws Exception {
        String token = registerClientAndGetToken();
        UUID accountId = currentAccountId(token);

        apply(token, new SubscriptionChangeRequest("PRO", Set.of(), List.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.plan.code").value("PRO"))
                .andExpect(jsonPath("$.preview.immediateAllowed").value(true));

        var usable = subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING));
        assertThat(usable).hasSize(1);
        assertThat(usable.getFirst().getEntitlementSnapshot()).contains("\"planCode\":\"PRO\"");
    }

    @Test
    void concurrentClientChangesSerializeToOneUsableSubscription() throws Exception {
        String token = registerClientAndGetToken();
        UUID accountId = currentAccountId(token);

        CompletableFuture<Integer> pro = applyAsync(token, "PRO");
        CompletableFuture<Integer> enterprise = applyAsync(token, "ENTERPRISE");

        assertThat(List.of(pro.join(), enterprise.join())).allSatisfy(status -> assertThat(status).isEqualTo(201));
        assertThat(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .hasSize(1);
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
