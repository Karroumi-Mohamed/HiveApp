package com.hiveapp.platform.security;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.DeletePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.PlanDeletionPreview;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.AssignAddOnFeatureRequest;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.QuotaLimitRequest;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
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
import static org.hamcrest.Matchers.not;
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

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void adminPlanAssignmentRejectsControlPlaneUnknownAndUnavailableFeatures() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID draftPlanId = duplicateFreeDraft(adminToken);

        assignPlanFeature(adminToken, draftPlanId, new AssignPlanFeatureRequest(
                "platform.plans", PlanFeatureMode.INCLUDED, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Feature platform.plans cannot be assigned to billing configuration."));

        assignPlanFeature(adminToken, draftPlanId, new AssignPlanFeatureRequest(
                "platform.unknown", PlanFeatureMode.INCLUDED, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Feature platform.unknown cannot be assigned to billing configuration."));

        Feature company = featureRepository.findByCode("platform.company").orElseThrow();
        FeatureStatus originalStatus = company.getStatus();
        try {
            company.setStatus(FeatureStatus.INTERNAL);
            featureRepository.saveAndFlush(company);
            assignPlanFeature(adminToken, draftPlanId, new AssignPlanFeatureRequest(
                    "platform.company", PlanFeatureMode.INCLUDED, List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Feature platform.company is not available for billing configuration."));

            company.setStatus(FeatureStatus.DEPRECATED);
            featureRepository.saveAndFlush(company);
            assignPlanFeature(adminToken, draftPlanId, new AssignPlanFeatureRequest(
                    "platform.company", PlanFeatureMode.INCLUDED, List.of()))
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
        UUID draftPlanId = duplicateFreeDraft(adminToken);

        assignPlanFeature(adminToken, draftPlanId, new AssignPlanFeatureRequest(
                        "platform.workspace", PlanFeatureMode.INCLUDED,
                        List.of(new QuotaLimitRequest("projects", null, 5L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Quota resource projects is not declared for feature platform.workspace."));

        assignPlanFeature(adminToken, draftPlanId, new AssignPlanFeatureRequest(
                        "platform.workspace",
                        PlanFeatureMode.INCLUDED,
                        List.of(
                                new QuotaLimitRequest("members", null, 3L),
                                new QuotaLimitRequest("members", null, 4L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Duplicate quota configuration for platform.workspace.members."));

        assignPlanFeature(adminToken, draftPlanId, new AssignPlanFeatureRequest(
                        "platform.company", PlanFeatureMode.OPTIONAL_ADD_ON,
                        List.of(new QuotaLimitRequest("not-allowed", null, 1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Only included Plan features may define base quota limits."));
    }

    @Test
    void adminPlanFeatureUpdateUsesSameBillingValidationAsAssignment() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID draftPlanId = duplicateFreeDraft(adminToken);
        UUID workspacePlanFeatureId = planFeatureRepository
                .findByPlanIdAndFeature_Code(draftPlanId, "platform.workspace")
                .orElseThrow()
                .getId();

        updatePlanFeature(adminToken, draftPlanId, workspacePlanFeatureId, new AssignPlanFeatureRequest(
                        "platform.company", PlanFeatureMode.INCLUDED, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A plan feature update cannot change its feature code."));

        updatePlanFeature(adminToken, draftPlanId, workspacePlanFeatureId, new AssignPlanFeatureRequest(
                        "platform.workspace", PlanFeatureMode.INCLUDED,
                        List.of(new QuotaLimitRequest("projects", null, 5L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Quota resource projects is not declared for feature platform.workspace."));
    }

    @Test
    void adminSubscriptionUpdateRejectsUnknownAndDuplicateQuotaPackages() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of("platform.plans"), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("One or more selected AddOns do not exist."));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of("platform.unknown"), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("One or more selected AddOns do not exist."));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of(),
                        List.of(new QuotaPackageSelection("MISSING_PACKAGE", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("One or more selected quota packages do not exist."));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of(),
                        List.of(
                                new QuotaPackageSelection("DUPLICATE_PACKAGE", 1),
                                new QuotaPackageSelection("DUPLICATE_PACKAGE", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Duplicate quota package selection: DUPLICATE_PACKAGE"));
    }

    @Test
    void quotaPackageCreationRejectsUnavailableFeatureStatus() throws Exception {
        String adminToken = loginAdminAndGetToken();
        Feature workspace = featureRepository.findByCode("platform.workspace").orElseThrow();
        FeatureStatus originalStatus = workspace.getStatus();

        try {
            workspace.setStatus(FeatureStatus.INTERNAL);
            featureRepository.saveAndFlush(workspace);

            createQuotaPackage(adminToken, "REJECTED_" + shortSuffix(), "FREE")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Feature platform.workspace is not available for billing configuration."));
        } finally {
            workspace.setStatus(originalStatus);
            featureRepository.saveAndFlush(workspace);
        }
    }

    @Test
    void adminSubscriptionReadModelIncludesSnapshotAndOverridesForUiEditing() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);

        String packageCode = "MEMBERS_2_" + shortSuffix();
        String created = createQuotaPackage(adminToken, packageCode, "FREE")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID packageId = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        mockMvc.perform(patch("/api/admin/quota-packages/{id}/status", packageId)
                        .param("status", "ACTIVE")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans[?(@.code == 'FREE')].quotaPackages[?(@.code == '"
                        + packageCode + "')].featureCode")
                        .value("platform.workspace"))
                .andExpect(jsonPath("$.plans[?(@.code == 'FREE')].quotaPackages[?(@.code == '"
                        + packageCode + "')].resource")
                        .value("members"));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of(),
                        List.of(new QuotaPackageSelection(packageCode, 1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/subscriptions/account/{accountId}", accountId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.customOverrides.addOnCodes").isEmpty())
                .andExpect(jsonPath("$.customOverrides.quotaPackages[0].packageCode").value(packageCode))
                .andExpect(jsonPath("$.customOverrides.quotaPackages[0].quantity").value(1))
                .andExpect(jsonPath("$.currentPriceCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.entitlementSnapshot.planCode").value("FREE"))
                .andExpect(jsonPath("$.entitlementSnapshot.currencyCode").value("USD"))
                .andExpect(jsonPath("$.entitlementSnapshot.quotaPackages[0].code").value(packageCode))
                .andExpect(jsonPath("$.entitlementSnapshot.features[*].featureCode", hasItem("platform.workspace")));
    }

    @Test
    void adminPlanDetailSubscribersUpdateAndSafeDeleteEndpoints() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        String accountName = accountRepository.findById(accountId).orElseThrow().getName();
        String ownerEmail = accountRepository.findOwnerEmailById(accountId).orElseThrow();
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();

        mockMvc.perform(get("/api/admin/plans/{planId}", freePlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FREE"))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.configuredRecurringPriceCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.currentSubscriberCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.warnings", hasItem("HAS_CURRENT_SUBSCRIBERS")))
                .andExpect(jsonPath("$.warnings", hasItem(
                        "SUBSCRIBER_TERMS_CHANGE_ONLY_THROUGH_EXPLICIT_OPERATIONS")));

        mockMvc.perform(get("/api/admin/plans/{planId}/subscribers", freePlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].accountId", hasItem(accountId.toString())))
                .andExpect(jsonPath("$.content[*].planCode", hasItem("FREE")))
                .andExpect(jsonPath("$.content[*].configuredRecurringPriceCurrencyCode", hasItem("USD")));

        mockMvc.perform(get("/api/admin/plans/{planId}/subscribers", freePlanId)
                        .param("search", accountName)
                        .param("status", "ACTIVE")
                        .param("size", "1")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content[0].accountId").value(accountId.toString()));

        mockMvc.perform(get("/api/admin/plans/{planId}/subscribers/by-owner-email", freePlanId)
                        .param("ownerEmail", ownerEmail)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ownerEmail").value(ownerEmail))
                .andExpect(jsonPath("$.content[0].subscriber.accountId").value(accountId.toString()));

        mockMvc.perform(put("/api/admin/plans/{planId}", freePlanId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlanRequest(
                                "Unsafe live edit", null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("create a draft revision")));

        mockMvc.perform(patch("/api/admin/plans/{planId}/status", freePlanId)
                        .param("status", "INACTIVE")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("default FREE plan must remain ACTIVE")));

        String draftCode = "TMP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        UUID draftPlanId = duplicatePlan(adminToken, freePlanId, new PlanBranchRequest(
                draftCode,
                "Temporary Plan",
                null,
                new BigDecimal("12.00"),
                "USD",
                BillingCycle.MONTHLY
        ));

        mockMvc.perform(put("/api/admin/plans/{planId}", draftPlanId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlanRequest(
                                "Temporary Plan Edited",
                                "Editable plan basics",
                                new BigDecimal("18.00"),
                                "USD",
                                BillingCycle.YEARLY
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Temporary Plan Edited"))
                .andExpect(jsonPath("$.description").value("Editable plan basics"))
                .andExpect(jsonPath("$.price").value(18.00))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.billingCycle").value("YEARLY"));

        mockMvc.perform(get("/api/admin/plans/{planId}", draftPlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSubscriberCount").value(0));

        String previewJson = mockMvc.perform(get("/api/admin/plans/{planId}/deletion-preview", draftPlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(true))
                .andExpect(jsonPath("$.ownedFeatureCount", greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();
        PlanDeletionPreview preview = objectMapper.readValue(previewJson, PlanDeletionPreview.class);

        mockMvc.perform(delete("/api/admin/plans/{planId}", draftPlanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeletePlanRequest(
                                draftCode, preview.expectedVersion(), preview.previewToken())))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/plans/{planId}", draftPlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        String freePreviewJson = mockMvc.perform(get("/api/admin/plans/{planId}/deletion-preview", freePlanId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(false))
                .andExpect(jsonPath("$.blockers", hasItem("DEFAULT_PROVISIONING_PLAN")))
                .andReturn().getResponse().getContentAsString();
        PlanDeletionPreview freePreview = objectMapper.readValue(freePreviewJson, PlanDeletionPreview.class);
        mockMvc.perform(delete("/api/admin/plans/{planId}", freePlanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeletePlanRequest(
                                "FREE", freePreview.expectedVersion(), freePreview.previewToken())))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be deleted")));
    }

    @Test
    void planRevisionPreservesLineageWhileDuplicateStartsANewLineage() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var free = planRepository.findByCode("FREE").orElseThrow();
        String revisionCode = "FREE_REV_" + shortSuffix();

        mockMvc.perform(post("/api/admin/plans/{sourcePlanId}/revisions", free.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlanBranchRequest(
                                revisionCode, "Free revision", null, BigDecimal.ZERO,
                                "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lineageId").value(free.getLineageId().toString()))
                .andExpect(jsonPath("$.revisionNumber").value(2))
                .andExpect(jsonPath("$.sourcePlanId").value(free.getId().toString()))
                .andExpect(jsonPath("$.creationReason").value("REVISED"));

        mockMvc.perform(post("/api/admin/plans/{sourcePlanId}/duplicate", free.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlanBranchRequest(
                                "FREE_COPY_" + shortSuffix(), "Free copy", null, BigDecimal.ZERO,
                                "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lineageId", not(free.getLineageId().toString())))
                .andExpect(jsonPath("$.revisionNumber").value(1))
                .andExpect(jsonPath("$.creationReason").value("DUPLICATED"));
    }

    @Test
    void emptyDraftDeletionRejectsAStalePreview() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String code = "EMPTY_" + shortSuffix();
        UUID planId = createPlan(adminToken, new CreatePlanRequest(
                code, "Empty draft", null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY));

        String previewJson = mockMvc.perform(get("/api/admin/plans/{planId}/deletion-preview", planId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(true))
                .andExpect(jsonPath("$.ownedFeatureCount").value(0))
                .andReturn().getResponse().getContentAsString();
        PlanDeletionPreview stalePreview = objectMapper.readValue(previewJson, PlanDeletionPreview.class);

        mockMvc.perform(put("/api/admin/plans/{planId}", planId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlanRequest(
                                "Edited empty draft", null, BigDecimal.ONE, "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/plans/{planId}", planId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeletePlanRequest(
                                code, stalePreview.expectedVersion(), stalePreview.previewToken()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("preview is stale")));
    }

    @Test
    void adminCanPublishFirstClassAddOnAndCatalogShowsItsComposition() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String planCode = "ADDON_PLAN_" + suffix;
        String addOnCode = "REPORTING_" + suffix;
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();
        UUID planId = duplicatePlan(adminToken, freePlanId, new PlanBranchRequest(
                planCode, "AddOn-ready plan", null, BigDecimal.ZERO, "USD",
                BillingCycle.MONTHLY));
        UUID companyPlanFeatureId = planFeatureRepository
                .findByPlanIdAndFeature_Code(planId, "platform.company")
                .orElseThrow()
                .getId();

        updatePlanFeature(adminToken, planId, companyPlanFeatureId, new AssignPlanFeatureRequest(
                "platform.company", PlanFeatureMode.OPTIONAL_ADD_ON, List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("OPTIONAL_ADD_ON"));
        mockMvc.perform(patch("/api/admin/plans/{planId}/status", planId)
                        .param("status", "ACTIVE")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String created = mockMvc.perform(post("/api/admin/add-ons")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAddOnRequest(
                                addOnCode, "Reporting", "Reporting module", BigDecimal.TEN, "USD",
                                BillingCycle.MONTHLY, Set.of(planCode), Set.of(), Set.of(), Set.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        UUID addOnId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(post("/api/admin/add-ons/{addOnId}/features", addOnId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignAddOnFeatureRequest("platform.company", List.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.featureCode").value("platform.company"));
        mockMvc.perform(patch("/api/admin/add-ons/{addOnId}/status", addOnId)
                        .param("status", "ACTIVE")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.features[0].featureCode").value("platform.company"));

        String clientToken = registerClientAndGetToken();
        mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans[?(@.code == '" + planCode + "')].addOns[0].code")
                        .value(addOnCode))
                .andExpect(jsonPath("$.plans[?(@.code == '" + planCode
                        + "')].addOns[0].features[0].featureCode").value("platform.company"));
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

    private org.springframework.test.web.servlet.ResultActions createQuotaPackage(
            String adminToken,
            String code,
            String planCode
    ) throws Exception {
        var request = new CreateQuotaPackageRequest(
                code, "Two more members", null, "platform.workspace", "members",
                2, BigDecimal.ONE, "USD", BillingCycle.MONTHLY,
                false, 1, Set.of(planCode), Set.of());
        return mockMvc.perform(post("/api/admin/quota-packages")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private String shortSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private UUID duplicateFreeDraft(String adminToken) throws Exception {
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();
        String suffix = shortSuffix();
        return duplicatePlan(adminToken, freePlanId, new PlanBranchRequest(
                "DRAFT_" + suffix, "Draft " + suffix, null, BigDecimal.ZERO,
                "USD", BillingCycle.MONTHLY));
    }

    private UUID duplicatePlan(String adminToken, UUID sourcePlanId, PlanBranchRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/admin/plans/{sourcePlanId}/duplicate", sourcePlanId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
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
