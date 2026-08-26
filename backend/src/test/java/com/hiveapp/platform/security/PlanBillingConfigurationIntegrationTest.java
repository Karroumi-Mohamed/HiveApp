package com.hiveapp.platform.security;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
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
import com.hiveapp.platform.client.plan.dto.ProductPriceVersionRequest;
import com.hiveapp.shared.quota.QuotaLimitMode;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.definition.StaffFeature;
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
    private AddOnRepository addOnRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

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
                        List.of(new QuotaLimitRequest("projects", QuotaLimitMode.FINITE, 5L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Quota resource projects is not declared for feature platform.workspace."));

        assignPlanFeature(adminToken, draftPlanId, new AssignPlanFeatureRequest(
                        StaffFeature.CODE,
                        PlanFeatureMode.INCLUDED,
                        List.of(
                                new QuotaLimitRequest(StaffFeature.MEMBERS, QuotaLimitMode.FINITE, 3L),
                                new QuotaLimitRequest(StaffFeature.MEMBERS, QuotaLimitMode.FINITE, 4L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Duplicate quota configuration for platform.staff.members."));

        assignPlanFeature(adminToken, draftPlanId, new AssignPlanFeatureRequest(
                        "platform.company", PlanFeatureMode.OPTIONAL_ADD_ON,
                        List.of(new QuotaLimitRequest("not-allowed", QuotaLimitMode.FINITE, 1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Only included Plan features may define base quota limits."));
    }

    @Test
    void activationRequiresAnExplicitQuotaDecisionForEveryResource() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createPlan(adminToken, new CreatePlanRequest(
                "Explicit quotas " + shortSuffix(), null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY));
        // Staff declares the members resource. Omitting it leaves an undecided resource,
        // which PLAN-FLOW-007 treats as an unfinished draft — never as "unlimited".
        String assigned = assignPlanFeature(adminToken, planId, new AssignPlanFeatureRequest(
                        StaffFeature.CODE, PlanFeatureMode.INCLUDED, List.of()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID planFeatureId = UUID.fromString(objectMapper.readTree(assigned).get("id").asText());
        publishPlanPriceDrafts(adminToken, planId);

        mockMvc.perform(patch("/api/admin/plans/{planId}/status", planId)
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(
                                planRepository.findById(planId).orElseThrow().getVersion()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Feature platform.staff: resource 'members' has no quota configuration. "
                                + "Declare a limit or an explicit UNLIMITED before activation."));

        updatePlanFeature(adminToken, planId, planFeatureId, new AssignPlanFeatureRequest(
                        StaffFeature.CODE, PlanFeatureMode.INCLUDED,
                        List.of(new QuotaLimitRequest(StaffFeature.MEMBERS, QuotaLimitMode.FINITE, 3L))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/plans/{planId}/status", planId)
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(
                                planRepository.findById(planId).orElseThrow().getVersion()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void planCreationWithCompositionIsAtomic() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String failingName = "Atomic fail " + shortSuffix();
        mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                failingName, null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                                List.of(
                                        new AssignPlanFeatureRequest(
                                                "platform.workspace", PlanFeatureMode.INCLUDED, List.of()),
                                        new AssignPlanFeatureRequest(
                                                "platform.unknown", PlanFeatureMode.INCLUDED, List.of()))))))
                .andExpect(status().isBadRequest());
        // The invalid second feature must roll the whole creation back: no plan half-matching
        // what the operator approved may survive.
        org.junit.jupiter.api.Assertions.assertTrue(planRepository.findAll().stream()
                .noneMatch(plan -> failingName.equals(plan.getName())));

        UUID planId = createPlan(adminToken, new CreatePlanRequest(
                "Atomic ok " + shortSuffix(), null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                List.of(new AssignPlanFeatureRequest(
                        StaffFeature.CODE, PlanFeatureMode.INCLUDED,
                        List.of(new QuotaLimitRequest(StaffFeature.MEMBERS, QuotaLimitMode.FINITE, 5L))))));
        mockMvc.perform(get("/api/admin/plans/{planId}/features", planId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].featureCode").value(StaffFeature.CODE));
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
                        List.of(new QuotaLimitRequest("projects", QuotaLimitMode.FINITE, 5L))))
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_BLOCKED"))
                .andExpect(jsonPath("$.message")
                        .value("The requested commercial selection is unavailable."))
                .andExpect(jsonPath("$.details[0]")
                        .value("PRODUCT_NOT_FOUND:PRODUCT_LIFECYCLE"));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of("platform.unknown"), List.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_BLOCKED"))
                .andExpect(jsonPath("$.details[0]")
                        .value("PRODUCT_NOT_FOUND:PRODUCT_LIFECYCLE"));

        updateSubscriptionOverrides(adminToken, accountId, new UpdateSubscriptionOverridesRequest(
                        Set.of(),
                        List.of(new QuotaPackageSelection("MISSING_PACKAGE", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_BLOCKED"))
                .andExpect(jsonPath("$.details[0]")
                        .value("PRODUCT_NOT_FOUND:PRODUCT_LIFECYCLE"));

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
        Feature staff = featureRepository.findByCode(StaffFeature.CODE).orElseThrow();
        FeatureStatus originalStatus = staff.getStatus();

        try {
            staff.setStatus(FeatureStatus.INTERNAL);
            featureRepository.saveAndFlush(staff);

            createQuotaPackage(adminToken, "REJECTED_" + shortSuffix(), "FREE")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Feature platform.staff is not available for billing configuration."));
        } finally {
            staff.setStatus(originalStatus);
            featureRepository.saveAndFlush(staff);
        }
    }

    @Test
    void adminSubscriptionReadModelIncludesSnapshotAndOverridesForUiEditing() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);

        String packageName = "Two members " + shortSuffix();
        String created = createQuotaPackage(adminToken, packageName, "FREE")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var createdPackage = objectMapper.readTree(created);
        UUID packageId = UUID.fromString(createdPackage.get("id").asText());
        String packageCode = createdPackage.get("code").asText();
        var activationPreview = objectMapper.readTree(mockMvc.perform(
                        get("/api/admin/quota-packages/{id}/activation-preview", packageId)
                                .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(patch("/api/admin/quota-packages/{id}/status", packageId)
                        .param("status", "ACTIVE")
                        .param("expectedVersion", activationPreview.get("expectedVersion").asText())
                        .param("reason", "Publish test capacity package")
                        .param("activationPreviewToken", activationPreview.get("previewToken").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans[?(@.code == 'FREE')].quotaPackages[?(@.code == '"
                        + packageCode + "')].featureCode")
                        .value(StaffFeature.CODE))
                .andExpect(jsonPath("$.plans[?(@.code == 'FREE')].quotaPackages[?(@.code == '"
                        + packageCode + "')].resource")
                        .value(StaffFeature.MEMBERS));

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
                                "Unsafe live edit", null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                                planRepository.findById(freePlanId).orElseThrow().getVersion()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("create a draft revision")));

        mockMvc.perform(patch("/api/admin/plans/{planId}/status", freePlanId)
                        .param("status", "INACTIVE")
                        .param("expectedVersion", String.valueOf(
                                planRepository.findById(freePlanId).orElseThrow().getVersion()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("default FREE plan must remain ACTIVE")));

        String draftName = "Temporary Plan " + shortSuffix();
        UUID draftPlanId = duplicatePlan(adminToken, freePlanId, new PlanBranchRequest(
                draftName,
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
                                BillingCycle.YEARLY,
                                planRepository.findById(draftPlanId).orElseThrow().getVersion()
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
                                "Temporary Plan Edited", preview.expectedVersion(), preview.previewToken())))
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
                                freePreview.planName(), freePreview.expectedVersion(), freePreview.previewToken())))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_BLOCKED"))
                .andExpect(jsonPath("$.message", containsString("cannot be deleted")));
    }

    @Test
    void planRevisionPreservesLineageWhileDuplicateStartsANewLineage() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var free = planRepository.findByCode("FREE").orElseThrow();
        mockMvc.perform(post("/api/admin/plans/{sourcePlanId}/revisions", free.getId())
                        .param("expectedVersion", String.valueOf(free.getVersion()))
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlanBranchRequest(
                                "Free revision", null, BigDecimal.ZERO,
                                "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lineageId").value(free.getLineageId().toString()))
                .andExpect(jsonPath("$.revisionNumber").value(2))
                .andExpect(jsonPath("$.sourcePlanId").value(free.getId().toString()))
                .andExpect(jsonPath("$.creationReason").value("REVISED"));

        mockMvc.perform(post("/api/admin/plans/{sourcePlanId}/duplicate", free.getId())
                        .param("expectedVersion", String.valueOf(
                                planRepository.findById(free.getId()).orElseThrow().getVersion()))
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlanBranchRequest(
                                "Free copy " + shortSuffix(), null, BigDecimal.ZERO,
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
        String name = "Empty draft " + shortSuffix();
        UUID planId = createPlan(adminToken, new CreatePlanRequest(
                name, null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY));

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
                                "Edited empty draft", null, BigDecimal.ONE, "USD", BillingCycle.MONTHLY,
                                stalePreview.expectedVersion()))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/plans/{planId}", planId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeletePlanRequest(
                                name, stalePreview.expectedVersion(), stalePreview.previewToken()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("preview is stale")));
    }

    @Test
    void adminCanPublishFirstClassAddOnAndCatalogShowsItsComposition() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();
        UUID planId = duplicatePlan(adminToken, freePlanId, new PlanBranchRequest(
                "AddOn-ready plan " + suffix, null, BigDecimal.ZERO, "USD",
                BillingCycle.MONTHLY));
        String planCode = planRepository.findById(planId).orElseThrow().getCode();
        UUID companyPlanFeatureId = planFeatureRepository
                .findByPlanIdAndFeature_Code(planId, "platform.company")
                .orElseThrow()
                .getId();

        updatePlanFeature(adminToken, planId, companyPlanFeatureId, new AssignPlanFeatureRequest(
                "platform.company", PlanFeatureMode.OPTIONAL_ADD_ON, List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("OPTIONAL_ADD_ON"));
        publishPlanPriceDrafts(adminToken, planId);
        mockMvc.perform(patch("/api/admin/plans/{planId}/status", planId)
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(
                                planRepository.findById(planId).orElseThrow().getVersion()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String created = mockMvc.perform(post("/api/admin/add-ons")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAddOnRequest(
                                "Reporting " + suffix, "Reporting module", BigDecimal.TEN, "USD",
                                BillingCycle.MONTHLY, Set.of(planCode), Set.of(), Set.of(), Set.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        var createdAddOn = objectMapper.readTree(created);
        UUID addOnId = UUID.fromString(createdAddOn.get("id").asText());
        String addOnCode = createdAddOn.get("code").asText();

        mockMvc.perform(post("/api/admin/add-ons/{addOnId}/features", addOnId)
                        .param("expectedVersion", String.valueOf(
                                addOnRepository.findById(addOnId).orElseThrow().getRowVersion()))
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignAddOnFeatureRequest("platform.company", List.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.featureCode").value("platform.company"));
        publishAddOnPriceDrafts(adminToken, addOnId);
        mockMvc.perform(patch("/api/admin/add-ons/{addOnId}/status", addOnId)
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(
                                addOnRepository.findById(addOnId).orElseThrow().getRowVersion()))
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

        String revisionResponse = mockMvc.perform(post("/api/admin/add-ons/{addOnId}/revisions", addOnId)
                        .param("expectedVersion", String.valueOf(
                                addOnRepository.findById(addOnId).orElseThrow().getRowVersion()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.revisionNumber").value(2))
                .andExpect(jsonPath("$.sourceAddOnId").value(addOnId.toString()))
                .andExpect(jsonPath("$.creationReason").value("REVISED"))
                .andExpect(jsonPath("$.features[0].featureCode").value("platform.company"))
                .andReturn().getResponse().getContentAsString();
        var revision = objectMapper.readTree(revisionResponse);
        UUID revisionId = UUID.fromString(revision.get("id").asText());
        String revisionCode = revision.get("code").asText();
        publishAddOnPriceDrafts(adminToken, revisionId);

        mockMvc.perform(patch("/api/admin/add-ons/{addOnId}/status", revisionId)
                        .param("status", "ACTIVE")
                        .param("expectedVersion", String.valueOf(
                                addOnRepository.findById(revisionId).orElseThrow().getRowVersion()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/admin/add-ons/{addOnId}", addOnId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans[?(@.code == '" + planCode + "')].addOns[*].code")
                        .value(hasItem(revisionCode)))
                .andExpect(jsonPath("$.plans[?(@.code == '" + planCode + "')].addOns[*].code")
                        .value(not(hasItem(addOnCode))));
    }

    private org.springframework.test.web.servlet.ResultActions assignPlanFeature(
            String adminToken,
            UUID planId,
            AssignPlanFeatureRequest request
    ) throws Exception {
        return mockMvc.perform(post("/api/admin/plans/{planId}/features", planId)
                .param("expectedVersion", String.valueOf(
                        planRepository.findById(planId).orElseThrow().getVersion()))
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
                .param("expectedVersion", String.valueOf(
                        planRepository.findById(planId).orElseThrow().getVersion()))
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
            String name,
            String planCode
    ) throws Exception {
        var request = new CreateQuotaPackageRequest(
                name, null, StaffFeature.CODE, StaffFeature.MEMBERS,
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
                "Draft " + suffix, null, BigDecimal.ZERO,
                "USD", BillingCycle.MONTHLY));
    }

    private UUID duplicatePlan(String adminToken, UUID sourcePlanId, PlanBranchRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/admin/plans/{sourcePlanId}/duplicate", sourcePlanId)
                        .param("expectedVersion", String.valueOf(
                                planRepository.findById(sourcePlanId).orElseThrow().getVersion()))
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

    private void publishPlanPriceDrafts(String token, UUID planId) throws Exception {
        for (var price : productPriceRepository.findAllByPlanId(planId)) {
            if (price.getStatus()
                    == com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT) {
                publishPrice(token, price.getId(), price.getVersion());
            }
        }
    }

    private void publishAddOnPriceDrafts(String token, UUID addOnId) throws Exception {
        for (var price : productPriceRepository.findAllByAddOnId(addOnId)) {
            if (price.getStatus()
                    == com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT) {
                publishPrice(token, price.getId(), price.getVersion());
            }
        }
    }

    private void publishPrice(String token, UUID priceId, long version) throws Exception {
        mockMvc.perform(post("/api/admin/product-prices/{id}/activate", priceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductPriceVersionRequest(
                                version, "Publish test price"))))
                .andExpect(status().isOk());
    }
}
