package com.hiveapp.platform.security;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaOverride;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanBillingConfigurationIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Test
    void adminPlanAssignmentRejectsControlPlaneUnknownAndUnavailableFeatures() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();

        assignPlanFeature(adminToken, freePlanId, new AssignPlanFeatureRequest("platform.plans", null, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Feature platform.plans cannot be assigned to billing configuration."));

        assignPlanFeature(adminToken, freePlanId, new AssignPlanFeatureRequest("platform.unknown", null, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Feature platform.unknown cannot be assigned to billing configuration."));

        Feature company = featureRepository.findByCode("platform.company").orElseThrow();
        FeatureStatus originalStatus = company.getStatus();
        try {
            company.setStatus(FeatureStatus.INTERNAL);
            featureRepository.saveAndFlush(company);
            assignPlanFeature(adminToken, freePlanId, new AssignPlanFeatureRequest("platform.company", null, List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Feature platform.company is not available for billing configuration."));

            company.setStatus(FeatureStatus.DEPRECATED);
            featureRepository.saveAndFlush(company);
            assignPlanFeature(adminToken, freePlanId, new AssignPlanFeatureRequest("platform.company", null, List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Feature platform.company is not available for billing configuration."));
        } finally {
            company.setStatus(originalStatus);
            featureRepository.saveAndFlush(company);
        }
    }

    @Test
    void adminPlanAssignmentRejectsInvalidQuotaConfiguration() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();

        assignPlanFeature(adminToken, freePlanId, new AssignPlanFeatureRequest(
                        "platform.workspace", null, List.of(new QuotaLimitEntry("projects", 5L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Quota resource projects is not declared for feature platform.workspace."));

        assignPlanFeature(adminToken, freePlanId, new AssignPlanFeatureRequest(
                        "platform.workspace",
                        null,
                        List.of(
                                new QuotaLimitEntry("members", 3L),
                                new QuotaLimitEntry("members", 4L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Duplicate quota configuration for platform.workspace.members."));

        assignPlanFeature(adminToken, freePlanId, new AssignPlanFeatureRequest(
                        "platform.workspace", null, List.of(new QuotaLimitEntry("members", -1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quota limit cannot be negative."));

        assignPlanFeature(adminToken, freePlanId, new AssignPlanFeatureRequest(
                        "platform.workspace",
                        null,
                        List.of(new QuotaLimitEntry("members", 3L, BigDecimal.valueOf(-1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quota price per unit cannot be negative."));

        assignPlanFeature(adminToken, freePlanId, new AssignPlanFeatureRequest(
                        "platform.company", BigDecimal.valueOf(-1), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Feature add-on price cannot be negative."));
    }

    @Test
    void adminPlanFeatureUpdateUsesSameBillingValidationAsAssignment() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();
        UUID workspacePlanFeatureId = planFeatureRepository
                .findByPlanIdAndFeature_Code(freePlanId, "platform.workspace")
                .orElseThrow()
                .getId();

        updatePlanFeature(adminToken, freePlanId, workspacePlanFeatureId, new AssignPlanFeatureRequest(
                        "platform.company", null, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A plan feature update cannot change its feature code."));

        updatePlanFeature(adminToken, freePlanId, workspacePlanFeatureId, new AssignPlanFeatureRequest(
                        "platform.workspace", null, List.of(new QuotaLimitEntry("projects", 5L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Quota resource projects is not declared for feature platform.workspace."));
    }

    @Test
    void adminSubscriptionOverrideUpdateRejectsInvalidFeaturesAndQuotaOverrides() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of("platform.plans"), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Feature platform.plans cannot be assigned to billing configuration."));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of("platform.unknown"), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Feature platform.unknown cannot be assigned to billing configuration."));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of(),
                        List.of(new QuotaOverride("platform.workspace", "projects", 10L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Quota resource projects is not declared for feature platform.workspace."));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of(),
                        List.of(new QuotaOverride("platform.company", "members", 10L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Quota resource members is not declared for feature platform.company."));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of(),
                        List.of(
                                new QuotaOverride("platform.workspace", "members", 10L),
                                new QuotaOverride("platform.workspace", "members", 12L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Duplicate quota override for platform.workspace.members."));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of(),
                        List.of(new QuotaOverride("platform.workspace", "members", -1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quota override limit cannot be negative."));
    }

    @Test
    void adminSubscriptionOverrideRejectsUnavailableFeatureStatus() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        Feature company = featureRepository.findByCode("platform.company").orElseThrow();
        FeatureStatus originalStatus = company.getStatus();

        try {
            company.setStatus(FeatureStatus.INTERNAL);
            featureRepository.saveAndFlush(company);

            updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                            Set.of("platform.company"), List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Feature platform.company is not available for billing configuration."));
        } finally {
            company.setStatus(originalStatus);
            featureRepository.saveAndFlush(company);
        }
    }

    @Test
    void adminSubscriptionReadModelIncludesSnapshotAndOverridesForUiEditing() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of("platform.company"),
                        List.of(new QuotaOverride("platform.workspace", "members", 5L))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/subscriptions/account/{accountId}", accountId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.customOverrides.addedFeatures", hasItem("platform.company")))
                .andExpect(jsonPath("$.customOverrides.quotaOverrides[0].featureCode").value("platform.workspace"))
                .andExpect(jsonPath("$.customOverrides.quotaOverrides[0].resource").value("members"))
                .andExpect(jsonPath("$.customOverrides.quotaOverrides[0].limit").value(5))
                .andExpect(jsonPath("$.entitlementSnapshot.planCode").value("FREE"))
                .andExpect(jsonPath("$.entitlementSnapshot.features[*].featureCode", hasItem("platform.workspace")));
    }

    @Test
    void adminPlanDetailSubscribersUpdateAndSafeDeleteEndpoints() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();

        mockMvc.perform(get("/api/admin/plans/{planId}", freePlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FREE"))
                .andExpect(jsonPath("$.currentSubscriberCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.warnings", hasItem("HAS_CURRENT_SUBSCRIBERS")))
                .andExpect(jsonPath("$.warnings", hasItem("TEMPLATE_EDITS_DO_NOT_UPDATE_EXISTING_SNAPSHOTS")));

        mockMvc.perform(get("/api/admin/plans/{planId}/subscribers", freePlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountId", hasItem(accountId.toString())))
                .andExpect(jsonPath("$[*].planCode", hasItem("FREE")));

        UUID draftPlanId = createPlan(adminToken, new CreatePlanRequest(
                "TMP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
                "Temporary Plan",
                null,
                new BigDecimal("12.00"),
                BillingCycle.MONTHLY,
                freePlanId
        ));

        mockMvc.perform(put("/api/admin/plans/{planId}", draftPlanId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlanRequest(
                                "Temporary Plan Edited",
                                "Editable plan basics",
                                new BigDecimal("18.00"),
                                BillingCycle.YEARLY
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Temporary Plan Edited"))
                .andExpect(jsonPath("$.description").value("Editable plan basics"))
                .andExpect(jsonPath("$.price").value(18.00))
                .andExpect(jsonPath("$.billingCycle").value("YEARLY"));

        mockMvc.perform(get("/api/admin/plans/{planId}", draftPlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSubscriberCount").value(0));

        mockMvc.perform(delete("/api/admin/plans/{planId}", draftPlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/plans/{planId}", draftPlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/plans/{planId}", freePlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be deleted")));
    }

    private org.springframework.test.web.servlet.ResultActions assignPlanFeature(
            String adminToken,
            UUID planId,
            AssignPlanFeatureRequest request
    ) throws Exception {
        return mockMvc.perform(post("/api/admin/plans/{planId}/features", planId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions updatePlanFeature(
            String adminToken,
            UUID planId,
            UUID planFeatureId,
            AssignPlanFeatureRequest request
    ) throws Exception {
        return mockMvc.perform(put("/api/admin/plans/{planId}/features/{planFeatureId}", planId, planFeatureId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions updateSubscriptionOverrides(
            String adminToken,
            UUID accountId,
            UpdateSubscriptionOverridesRequest request
    ) throws Exception {
        return mockMvc.perform(patch("/api/admin/subscriptions/account/{accountId}/overrides", accountId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private UUID createPlan(String adminToken, CreatePlanRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
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
