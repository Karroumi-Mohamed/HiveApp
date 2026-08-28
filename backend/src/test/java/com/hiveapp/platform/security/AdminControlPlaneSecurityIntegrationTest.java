package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.platform.admin.dto.AssignAdminRoleRequest;
import com.hiveapp.platform.admin.dto.AdminRoleImpactRequest;
import com.hiveapp.platform.admin.dto.AdminRolePermissionSetRequest;
import com.hiveapp.platform.admin.dto.CreateAdminRoleRequest;
import com.hiveapp.platform.admin.dto.CreateAdminUserRequest;
import com.hiveapp.platform.admin.dto.GrantAdminPermissionRequest;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.DeletePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanLifecycleRequest;
import com.hiveapp.platform.client.plan.dto.AddOnLifecycleRequest;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.AddOnLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyRequests;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentRequests;
import com.hiveapp.platform.client.plan.dto.CommercialCampaignRequests;
import com.hiveapp.platform.client.plan.dto.CommercialOfferRequests;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentSource;
import com.hiveapp.platform.registry.definition.PriceBooksFeature;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControlPlaneSecurityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private AddOnRepository addOnRepository;

    @Autowired
    private QuotaPackageRepository quotaPackageRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Test
    void adminUsersAndRolesExposeBoundedStablePages() throws Exception {
        String token = loginAdminAndGetToken();

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/admin/roles")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void operatorSearchIncludesTheDisplayedFullName() throws Exception {
        String token = loginAdminAndGetToken();
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String email = operatorEmail();
        CreateAdminUserRequest request = new CreateAdminUserRequest(
                "Nadia" + marker,
                "Benali" + marker,
                email,
                InitialAccessMethod.EMAIL_LINK,
                false);
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/users")
                        .param("search", "nadia" + marker + " benali" + marker)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(email));
    }

    @Test
    void composedPlanCreationRequiresTheAssignFeaturePermission() throws Exception {
        LimitedAdmin creator = createLimitedAdmin(
                "platform.plans.create", "platform.price_books.create");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        // The bulk path must enforce the same node as the dedicated assign endpoint. The
        // operator has both halves of bare commercial creation, but cannot compose features.
        mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(creator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                "Composed " + suffix, null, BigDecimal.ZERO, "USD",
                                BillingCycle.MONTHLY,
                                java.util.List.of(new AssignPlanFeatureRequest(
                                        "platform.workspace", PlanFeatureMode.INCLUDED, java.util.List.of()))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Composing a plan at creation requires the assign-feature permission."));

        // A bare creation stays within the create + initial-price-draft composite authority.
        mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(creator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                "Bare " + suffix, null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated());

        LimitedAdmin composer = createLimitedAdmin(
                "platform.plans.create",
                "platform.price_books.create",
                "platform.plans.assign_feature");
        mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(composer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                "Authorized composed " + suffix, null, BigDecimal.ZERO, "USD",
                                BillingCycle.MONTHLY,
                                java.util.List.of(new AssignPlanFeatureRequest(
                                        "platform.workspace", PlanFeatureMode.INCLUDED,
                                        java.util.List.of()))))))
                .andExpect(status().isCreated());
    }

    @Test
    void activationPreviewPermissionsAreIndependentAndEvidenceIsActorBound() throws Exception {
        String superToken = loginAdminAndGetToken();
        LimitedAdmin previewer = createLimitedAdmin(
                "platform.plans.preview_plan_activation",
                "platform.plans.preview_add_on_activation");
        LimitedAdmin lifecycleOperator = createLimitedAdmin(
                "platform.plans.transition_status",
                "platform.plans.transition_add_on");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        JsonNode plan = objectMapper.readTree(mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                "Actor-bound Plan " + suffix, null, BigDecimal.ONE, "USD",
                                BillingCycle.MONTHLY))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID planId = UUID.fromString(plan.get("id").asText());
        JsonNode addOn = objectMapper.readTree(mockMvc.perform(post("/api/admin/add-ons")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAddOnRequest(
                                "Actor-bound AddOn " + suffix, null, BigDecimal.ONE, "USD",
                                BillingCycle.MONTHLY, java.util.Set.of("FLEX"), java.util.Set.of(),
                                java.util.Set.of(), java.util.Set.of()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID addOnId = UUID.fromString(addOn.get("id").asText());
        try {
            mockMvc.perform(get("/api/admin/plans/{id}/activation-preview", planId)
                            .header("Authorization", bearer(lifecycleOperator.token())))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/add-ons/{id}/activation-preview", addOnId)
                            .header("Authorization", bearer(lifecycleOperator.token())))
                    .andExpect(status().isForbidden());

            JsonNode planPreview = objectMapper.readTree(mockMvc.perform(
                            get("/api/admin/plans/{id}/activation-preview", planId)
                                    .header("Authorization", bearer(previewer.token())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            JsonNode addOnPreview = objectMapper.readTree(mockMvc.perform(
                            get("/api/admin/add-ons/{id}/activation-preview", addOnId)
                                    .header("Authorization", bearer(previewer.token())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            mockMvc.perform(post("/api/admin/plans/{id}/lifecycle", planId)
                            .header("Authorization", bearer(previewer.token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PlanLifecycleRequest(
                                    PlanLifecycleAction.ACTIVATE,
                                    planPreview.get("expectedVersion").asLong(), null,
                                    planPreview.get("previewToken").asText()))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/admin/plans/{id}/lifecycle", planId)
                            .header("Authorization", bearer(lifecycleOperator.token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PlanLifecycleRequest(
                                    PlanLifecycleAction.ACTIVATE,
                                    planPreview.get("expectedVersion").asLong(), null,
                                    planPreview.get("previewToken").asText()))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("STALE_ACTIVATION_PREVIEW"));
            mockMvc.perform(post("/api/admin/add-ons/{id}/lifecycle", addOnId)
                            .header("Authorization", bearer(lifecycleOperator.token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AddOnLifecycleRequest(
                                    AddOnLifecycleAction.ACTIVATE,
                                    addOnPreview.get("expectedVersion").asLong(), null,
                                    addOnPreview.get("previewToken").asText()))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("STALE_ACTIVATION_PREVIEW"));

            assertThat(planRepository.findById(planId).orElseThrow().getStatus().name())
                    .isEqualTo("DRAFT");
            assertThat(addOnRepository.findById(addOnId).orElseThrow().getStatus().name())
                    .isEqualTo("DRAFT");
        } finally {
            productPriceRepository.deleteAllInBatch(productPriceRepository.findAllByPlanId(planId));
            productPriceRepository.deleteAllInBatch(productPriceRepository.findAllByAddOnId(addOnId));
            productPriceRepository.flush();
            addOnRepository.deleteById(addOnId);
            planRepository.deleteById(planId);
            addOnRepository.flush();
            planRepository.flush();
        }
    }

    @Test
    void nonDefaultAvailabilityAtCreationRequiresItsDedicatedPermission() throws Exception {
        LimitedAdmin planCreator = createLimitedAdmin("platform.plans.create");
        LimitedAdmin addOnCreator = createLimitedAdmin("platform.plans.create_add_on");
        LimitedAdmin quotaCreator = createLimitedAdmin(
                "platform.plans.create_quota_package", "platform.price_books.create");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(planCreator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                "Closed " + suffix, null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                                java.util.List.of(), PlanExtensionPolicy.CLOSED,
                                ProductSalesVisibility.PUBLIC))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/add-ons")
                        .header("Authorization", bearer(addOnCreator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAddOnRequest(
                                "Direct AddOn " + suffix, null, BigDecimal.ZERO, "USD",
                                BillingCycle.MONTHLY, java.util.Set.of(), java.util.Set.of(),
                                java.util.Set.of(), java.util.Set.of(),
                                ProductSalesVisibility.DIRECT_ONLY))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/quota-packages")
                        .header("Authorization", bearer(quotaCreator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateQuotaPackageRequest(
                                "Direct quota " + suffix, null, "platform.staff", "members", 1,
                                BigDecimal.ZERO, "USD", BillingCycle.MONTHLY, true, 10,
                                java.util.Set.of(), java.util.Set.of(),
                                ProductSalesVisibility.DIRECT_ONLY))))
                .andExpect(status().isForbidden());
    }

    @Test
    void commercialAvailabilityRuntimeShutdownAlsoBlocksCrossFeatureCreationPaths() throws Exception {
        String token = loginAdminAndGetToken();
        var feature = featureRepository.findByCode("platform.commercial_availability").orElseThrow();
        boolean runtimeEnabled = feature.isRuntimeEnabled();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String planName = "Runtime closed " + suffix;
        String addOnName = "Runtime direct AddOn " + suffix;
        String quotaName = "Runtime direct quota " + suffix;

        try {
            feature.setRuntimeEnabled(false);
            featureRepository.saveAndFlush(feature);

            mockMvc.perform(post("/api/admin/plans")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                    planName, null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                                    java.util.List.of(), PlanExtensionPolicy.CLOSED,
                                    ProductSalesVisibility.PUBLIC))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/admin/add-ons")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateAddOnRequest(
                                    addOnName, null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                                    java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                                    java.util.Set.of(), ProductSalesVisibility.DIRECT_ONLY))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/admin/quota-packages")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateQuotaPackageRequest(
                                    quotaName, null, "platform.staff", "members", 1,
                                    BigDecimal.ZERO, "USD", BillingCycle.MONTHLY, true, 10,
                                    java.util.Set.of(), java.util.Set.of(),
                                    ProductSalesVisibility.DIRECT_ONLY))))
                    .andExpect(status().isForbidden());

            assertThat(planRepository.findAll()).noneMatch(plan -> planName.equals(plan.getName()));
            assertThat(addOnRepository.findAll()).noneMatch(addOn -> addOnName.equals(addOn.getName()));
            assertThat(quotaPackageRepository.findAll())
                    .noneMatch(item -> quotaName.equals(item.getName()));
        } finally {
            var current = featureRepository.findByCode("platform.commercial_availability").orElseThrow();
            current.setRuntimeEnabled(runtimeEnabled);
            featureRepository.saveAndFlush(current);
        }
    }

    @Test
    void invalidCommercialPayloadReturnsStructuredValidationDetails() throws Exception {
        String token = loginAdminAndGetToken();
        CreatePlanRequest request = new CreatePlanRequest(
                "Invalid",
                null,
                new BigDecimal("-0.01"),
                "USD",
                BillingCycle.MONTHLY);

        mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("price")));
    }

    @Test
    void nonSuperAdminCannotReadUngrantedControlPlaneResources() throws Exception {
        LimitedAdmin admin = createLimitedAdmin("platform.plans.list");

        mockMvc.perform(get("/api/admin/plans")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/registry/inventory")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/roles/{id}", admin.roleId())
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users/{id}", admin.adminUserId())
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void priceBookPermissionsAreFineGrainedAndRejectClientIdentities() throws Exception {
        String clientToken = registerClientAndGetToken();
        LimitedAdmin reader = createLimitedAdmin("platform.price_books.list");

        mockMvc.perform(get("/api/admin/product-prices"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/product-prices")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());
        String priceList = mockMvc.perform(get("/api/admin/product-prices")
                        .header("Authorization", bearer(reader.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn().getResponse().getContentAsString();
        UUID priceId = UUID.fromString(objectMapper.readTree(priceList)
                .get("content").get(0).get("id").asText());
        assertThat(objectMapper.readTree(priceList).path("content"))
                .allSatisfy(item -> assertThat(item.path("availableActions")).isEmpty());

        mockMvc.perform(get("/api/admin/product-prices/{id}/history", priceId)
                        .header("Authorization", bearer(reader.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        LimitedAdmin historian = createLimitedAdmin("platform.price_books.read_history");
        mockMvc.perform(get("/api/admin/product-prices/{id}/history", priceId)
                        .header("Authorization", bearer(historian.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(post("/api/admin/product-prices")
                        .param("ownerType", "PLAN")
                        .param("ownerId", UUID.randomUUID().toString())
                        .header("Authorization", bearer(reader.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":0,"currencyCode":"USD","billingCycle":"YEARLY",
                                 "effectiveFrom":"2026-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void productChoosersDoNotGrantOperationalCatalogueAccess() throws Exception {
        LimitedAdmin chooser = createLimitedAdmin(
                "platform.plans.choose_plans", "platform.plans.resolve_plan_choices",
                "platform.plans.resolve_plan_choice_codes");
        UUID flexId = planRepository.findByCode("FLEX").orElseThrow().getId();

        mockMvc.perform(get("/api/admin/plans/chooser")
                        .header("Authorization", bearer(chooser.token()))
                        .param("search", "FLEX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(flexId.toString()))
                .andExpect(jsonPath("$.content[0].choiceState").value("SELECTABLE"));
        mockMvc.perform(get("/api/admin/plans/chooser/selected")
                        .header("Authorization", bearer(chooser.token()))
                        .param("ids", flexId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(flexId.toString()));
        mockMvc.perform(get("/api/admin/plans/chooser/selected-codes")
                        .header("Authorization", bearer(chooser.token()))
                        .param("codes", " flex ")
                        .param("codes", "DELETED_RETAINED_PLAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(flexId.toString()))
                .andExpect(jsonPath("$[0].code").value("FLEX"))
                .andExpect(jsonPath("$[0].choiceState").value("SELECTABLE"))
                .andExpect(jsonPath("$[1].code").value("DELETED_RETAINED_PLAN"))
                .andExpect(jsonPath("$[1].choiceState").value("MISSING"));

        mockMvc.perform(get("/api/admin/plans")
                        .header("Authorization", bearer(chooser.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/api/admin/plans/{id}", flexId)
                        .header("Authorization", bearer(chooser.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/api/admin/plans/{id}/operations", flexId)
                        .header("Authorization", bearer(chooser.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        LimitedAdmin operationsOnly = createLimitedAdmin("platform.plans.read_plan_operations");
        mockMvc.perform(get("/api/admin/plans/{id}/operations", flexId)
                        .header("Authorization", bearer(operationsOnly.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(flexId.toString()))
                .andExpect(jsonPath("$.availableActions.length()").value(0));
        mockMvc.perform(get("/api/admin/plans")
                        .header("Authorization", bearer(operationsOnly.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountOverrideChoosersAreBoundedAndSeparatelyPermissioned() throws Exception {
        String clientToken = registerClientAndGetToken();
        String accountBody = mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(objectMapper.readTree(accountBody).get("id").asText());
        LimitedAdmin addOnChooser = createLimitedAdmin(
                "platform.subscriptions.choose_add_on_overrides");

        mockMvc.perform(get("/api/admin/subscriptions/account/{id}/override-choices/add-ons", accountId)
                        .header("Authorization", bearer(addOnChooser.token()))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.retainedSelections").isArray())
                .andExpect(jsonPath("$.size").value(1));
        mockMvc.perform(get(
                                "/api/admin/subscriptions/account/{id}/override-choices/quota-packages",
                                accountId)
                        .header("Authorization", bearer(addOnChooser.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/api/admin/add-ons")
                        .header("Authorization", bearer(addOnChooser.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/subscriptions/account/{id}/overrides", accountId)
                        .header("Authorization", bearer(addOnChooser.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addOnCodes\":[],\"quotaPackages\":[]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/subscriptions/account/{id}/override-choices/add-ons", accountId)
                        .header("Authorization", bearer(addOnChooser.token()))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void quotaLifecycleCannotPublishPricesWithoutPriceActivationPermission() throws Exception {
        String superToken = loginAdminAndGetToken();
        QuotaActivationFixture fixture = createQuotaActivationFixture(superToken);
        try {
            LimitedAdmin lifecycleOnly = createLimitedAdmin(
                    "platform.plans.preview_quota_package_activation",
                    "platform.plans.lifecycle_quota_package");
            JsonNode lifecycleBody = objectMapper.readTree(fixture.lifecycleBody());
            ((com.fasterxml.jackson.databind.node.ObjectNode) lifecycleBody).put(
                    "activationPreviewToken",
                    fetchQuotaPackageActivationToken(lifecycleOnly.token(), fixture.packageId()));

            mockMvc.perform(post("/api/admin/quota-packages/{id}/lifecycle", fixture.packageId())
                            .header("Authorization", bearer(lifecycleOnly.token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(lifecycleBody.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.message").value(
                            "Publishing a capacity-package revision and its reviewed prices "
                                    + "requires platform.price_books.activate."));

            assertQuotaActivationWasAtomic(fixture);
        } finally {
            removeQuotaActivationFixture(fixture.packageId());
        }
    }

    @Test
    void quotaCreationCannotCreateAPriceDraftWithoutPriceBookCreatePermission() throws Exception {
        LimitedAdmin packageOnly = createLimitedAdmin("platform.plans.create_quota_package");
        String name = "Unauthorized price draft " + UUID.randomUUID();
        long packageCount = quotaPackageRepository.count();
        long priceCount = productPriceRepository.count();

        mockMvc.perform(post("/api/admin/quota-packages")
                        .header("Authorization", bearer(packageOnly.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateQuotaPackageRequest(
                                name, null, "platform.staff", "members", 1,
                                BigDecimal.ONE, "USD", BillingCycle.MONTHLY, true, 10,
                                java.util.Set.of("FLEX"), java.util.Set.of(),
                                ProductSalesVisibility.PUBLIC))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(
                        "Creating a capacity package and its initial reviewable price draft "
                                + "requires platform.price_books.create."));

        assertThat(quotaPackageRepository.count()).isEqualTo(packageCount);
        assertThat(productPriceRepository.count()).isEqualTo(priceCount);
        assertThat(quotaPackageRepository.findAll()).noneMatch(item -> name.equals(item.getName()));
    }

    @Test
    void planAndAddOnCreationCannotHidePriceDraftPublicationAuthority() throws Exception {
        LimitedAdmin planOnly = createLimitedAdmin("platform.plans.create");
        LimitedAdmin addOnOnly = createLimitedAdmin("platform.plans.create_add_on");
        long planCount = planRepository.count();
        long addOnCount = addOnRepository.count();
        long priceCount = productPriceRepository.count();

        mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(planOnly.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                "Unauthorized Plan price draft " + UUID.randomUUID(), null,
                                BigDecimal.ONE, "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(
                        "Creating a Plan and its initial reviewable price draft requires "
                                + "platform.price_books.create."));
        mockMvc.perform(post("/api/admin/add-ons")
                        .header("Authorization", bearer(addOnOnly.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAddOnRequest(
                                "Unauthorized AddOn price draft " + UUID.randomUUID(), null,
                                BigDecimal.ONE, "USD", BillingCycle.MONTHLY,
                                java.util.Set.of("FLEX"), java.util.Set.of(), java.util.Set.of(),
                                java.util.Set.of(), ProductSalesVisibility.PUBLIC))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(
                        "Creating an AddOn and its initial reviewable price draft requires "
                                + "platform.price_books.create."));

        assertThat(planRepository.count()).isEqualTo(planCount);
        assertThat(addOnRepository.count()).isEqualTo(addOnCount);
        assertThat(productPriceRepository.count()).isEqualTo(priceCount);
    }

    @Test
    void productDeleteRequiresPriceDraftDeletionAuthorityAndActionTruthMatches() throws Exception {
        String superToken = loginAdminAndGetToken();
        JsonNode created = responseJson(mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                "Delete authority " + UUID.randomUUID(), null,
                                BigDecimal.ONE, "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated()));
        UUID planId = UUID.fromString(created.get("id").asText());
        JsonNode preview = responseJson(mockMvc.perform(
                        get("/api/admin/plans/{id}/deletion-preview", planId)
                                .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk()));

        LimitedAdmin productOnly = createLimitedAdmin(
                "platform.plans.delete",
                "platform.plans.preview_delete",
                "platform.plans.read_plan_operations");
        mockMvc.perform(get("/api/admin/plans/{id}/operations", planId)
                        .header("Authorization", bearer(productOnly.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableActions",
                        not(hasItem("DELETE_DRAFT"))));
        mockMvc.perform(delete("/api/admin/plans/{id}", planId)
                        .header("Authorization", bearer(productOnly.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeletePlanRequest(
                                created.get("name").asText(),
                                preview.get("expectedVersion").asLong(),
                                preview.get("previewToken").asText()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(planRepository.findById(planId)).isPresent();
        assertThat(productPriceRepository.findAllByPlanId(planId)).isNotEmpty();

        LimitedAdmin complete = createLimitedAdmin(
                "platform.plans.delete",
                "platform.plans.preview_delete",
                "platform.price_books.delete_draft");
        JsonNode completePreview = responseJson(mockMvc.perform(
                        get("/api/admin/plans/{id}/deletion-preview", planId)
                                .header("Authorization", bearer(complete.token())))
                .andExpect(status().isOk()));
        mockMvc.perform(delete("/api/admin/plans/{id}", planId)
                        .header("Authorization", bearer(complete.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeletePlanRequest(
                                created.get("name").asText(),
                                completePreview.get("expectedVersion").asLong(),
                                completePreview.get("previewToken").asText()))))
                .andExpect(status().isNoContent());
        assertThat(planRepository.findById(planId)).isEmpty();
        assertThat(productPriceRepository.findAllByPlanId(planId)).isEmpty();
    }

    @Test
    void priceBooksRuntimeShutdownVetoesCompositeProductDeletion() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode created = responseJson(mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                "Runtime delete " + UUID.randomUUID(), null,
                                BigDecimal.ONE, "USD", BillingCycle.MONTHLY))))
                .andExpect(status().isCreated()));
        UUID planId = UUID.fromString(created.get("id").asText());
        JsonNode preview = responseJson(mockMvc.perform(
                        get("/api/admin/plans/{id}/deletion-preview", planId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        var priceBooks = featureRepository.findByCode(PriceBooksFeature.CODE).orElseThrow();
        boolean runtimeEnabled = priceBooks.isRuntimeEnabled();
        try {
            priceBooks.setRuntimeEnabled(false);
            featureRepository.saveAndFlush(priceBooks);
            mockMvc.perform(get("/api/admin/plans/{id}/operations", planId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableActions",
                            not(hasItem("DELETE_DRAFT"))));
            mockMvc.perform(delete("/api/admin/plans/{id}", planId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new DeletePlanRequest(
                                    created.get("name").asText(),
                                    preview.get("expectedVersion").asLong(),
                                    preview.get("previewToken").asText()))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            assertThat(planRepository.findById(planId)).isPresent();
            assertThat(productPriceRepository.findAllByPlanId(planId)).isNotEmpty();
        } finally {
            var restored = featureRepository.findByCode(PriceBooksFeature.CODE).orElseThrow();
            restored.setRuntimeEnabled(runtimeEnabled);
            featureRepository.saveAndFlush(restored);
            productPriceRepository.deleteAllInBatch(productPriceRepository.findAllByPlanId(planId));
            productPriceRepository.flush();
            planRepository.deleteById(planId);
            planRepository.flush();
        }
    }

    @Test
    void priceBooksRuntimeShutdownVetoesPlanAndAddOnPriceDraftCreation() throws Exception {
        String token = loginAdminAndGetToken();
        var priceBooks = featureRepository.findByCode(PriceBooksFeature.CODE).orElseThrow();
        boolean runtimeEnabled = priceBooks.isRuntimeEnabled();
        long planCount = planRepository.count();
        long addOnCount = addOnRepository.count();
        long priceCount = productPriceRepository.count();
        try {
            priceBooks.setRuntimeEnabled(false);
            featureRepository.saveAndFlush(priceBooks);

            mockMvc.perform(post("/api/admin/plans")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                    "Runtime-disabled Plan draft " + UUID.randomUUID(), null,
                                    BigDecimal.ONE, "USD", BillingCycle.MONTHLY))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(post("/api/admin/add-ons")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateAddOnRequest(
                                    "Runtime-disabled AddOn draft " + UUID.randomUUID(), null,
                                    BigDecimal.ONE, "USD", BillingCycle.MONTHLY,
                                    java.util.Set.of("FLEX"), java.util.Set.of(),
                                    java.util.Set.of(), java.util.Set.of(),
                                    ProductSalesVisibility.PUBLIC))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            assertThat(planRepository.count()).isEqualTo(planCount);
            assertThat(addOnRepository.count()).isEqualTo(addOnCount);
            assertThat(productPriceRepository.count()).isEqualTo(priceCount);
        } finally {
            var restored = featureRepository.findByCode(PriceBooksFeature.CODE).orElseThrow();
            restored.setRuntimeEnabled(runtimeEnabled);
            featureRepository.saveAndFlush(restored);
        }
    }

    @Test
    void priceBooksRuntimeShutdownVetoesCompositeQuotaCreation() throws Exception {
        String token = loginAdminAndGetToken();
        var priceBooks = featureRepository.findByCode(PriceBooksFeature.CODE).orElseThrow();
        boolean runtimeEnabled = priceBooks.isRuntimeEnabled();
        String name = "Runtime-disabled price draft " + UUID.randomUUID();
        long packageCount = quotaPackageRepository.count();
        long priceCount = productPriceRepository.count();
        try {
            priceBooks.setRuntimeEnabled(false);
            featureRepository.saveAndFlush(priceBooks);

            mockMvc.perform(post("/api/admin/quota-packages")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateQuotaPackageRequest(
                                    name, null, "platform.staff", "members", 1,
                                    BigDecimal.ONE, "USD", BillingCycle.MONTHLY, true, 10,
                                    java.util.Set.of("FLEX"), java.util.Set.of(),
                                    ProductSalesVisibility.PUBLIC))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            assertThat(quotaPackageRepository.count()).isEqualTo(packageCount);
            assertThat(productPriceRepository.count()).isEqualTo(priceCount);
        } finally {
            var restored = featureRepository.findByCode(PriceBooksFeature.CODE).orElseThrow();
            restored.setRuntimeEnabled(runtimeEnabled);
            featureRepository.saveAndFlush(restored);
        }
    }

    @Test
    void priceBooksRuntimeShutdownVetoesCompositeQuotaActivation() throws Exception {
        String superToken = loginAdminAndGetToken();
        QuotaActivationFixture fixture = createQuotaActivationFixture(superToken);
        var priceBooks = featureRepository.findByCode(PriceBooksFeature.CODE).orElseThrow();
        boolean runtimeEnabled = priceBooks.isRuntimeEnabled();
        try {
            priceBooks.setRuntimeEnabled(false);
            featureRepository.saveAndFlush(priceBooks);

            mockMvc.perform(post("/api/admin/quota-packages/{id}/lifecycle", fixture.packageId())
                            .header("Authorization", bearer(superToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture.lifecycleBody()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            assertQuotaActivationWasAtomic(fixture);
        } finally {
            var restored = featureRepository.findByCode(PriceBooksFeature.CODE).orElseThrow();
            restored.setRuntimeEnabled(runtimeEnabled);
            featureRepository.saveAndFlush(restored);
            removeQuotaActivationFixture(fixture.packageId());
        }
    }

    @Test
    void commercialAvailabilityPermissionsAreFineGrainedAndRejectClientIdentities() throws Exception {
        UUID planId = planRepository.findByCode("FLEX").orElseThrow().getId();
        String clientToken = registerClientAndGetToken();
        LimitedAdmin inspector = createLimitedAdmin(
                "platform.commercial_availability.inspect_compatibility");

        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", planId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", planId)
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", planId)
                        .header("Authorization", bearer(inspector.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(post("/api/admin/plans/{id}/commercial-availability/preview", planId)
                        .header("Authorization", bearer(inspector.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extensionPolicy\":\"CLOSED\",\"salesVisibility\":\"PUBLIC\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        LimitedAdmin previewer = createLimitedAdmin(
                "platform.commercial_availability.preview_plan_policy");
        String preview = mockMvc.perform(
                        post("/api/admin/plans/{id}/commercial-availability/preview", planId)
                                .header("Authorization", bearer(previewer.token()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"extensionPolicy\":\"CLOSED\",\"salesVisibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableActions[0]").value("APPLY_PLAN_AVAILABILITY"))
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(preview);
        mockMvc.perform(patch("/api/admin/plans/{id}/commercial-availability", planId)
                        .header("Authorization", bearer(previewer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "expectedVersion", body.get("expectedVersion").asLong(),
                                "extensionPolicy", "CLOSED",
                                "salesVisibility", "PUBLIC",
                                "reason", "permission boundary test",
                                "previewToken", body.get("previewToken").asText()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        String superToken = loginAdminAndGetToken();
        String history = mockMvc.perform(
                        get("/api/admin/commercial-products/{id}/availability-history", planId)
                                .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(history).path("content")).anySatisfy(entry -> {
            assertThat(entry.path("outcome").asText()).isEqualTo("FAILED");
            assertThat(entry.path("action").asText())
                    .isEqualTo("platform.commercial_availability.update_plan_policy");
            assertThat(entry.path("reason").asText()).isEqualTo("permission boundary test");
        });
    }

    @Test
    void assignablePlanPriceChooserIsBoundedAndDoesNotGrantPriceBookAccess() throws Exception {
        String clientToken = registerClientAndGetToken();
        LimitedAdmin readerOnly = createLimitedAdmin("platform.subscriptions.read");
        LimitedAdmin chooser = createLimitedAdmin("platform.subscriptions.list_assignable_prices");

        mockMvc.perform(get("/api/admin/subscriptions/assignable-plan-prices")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/subscriptions/assignable-plan-prices")
                        .header("Authorization", bearer(readerOnly.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        String chooserBody = mockMvc.perform(get("/api/admin/subscriptions/assignable-plan-prices")
                        .header("Authorization", bearer(chooser.token()))
                        .param("search", "free")
                        .param("currencyCode", "usd")
                        .param("billingCycle", "MONTHLY")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "planName")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].planCode").value("FREE"))
                .andExpect(jsonPath("$.content[0].planRevisionNumber").isNumber())
                .andExpect(jsonPath("$.content[0].priceEntryId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].currencyCode").value("USD"))
                .andExpect(jsonPath("$.content[0].billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode chooserJson = objectMapper.readTree(chooserBody);
        assertThat(chooserJson.path("content").get(0).path("amount").isTextual()).isTrue();

        var workspace = featureRepository.findByCode("platform.workspace").orElseThrow();
        boolean runtimeEnabled = workspace.isRuntimeEnabled();
        try {
            workspace.setRuntimeEnabled(false);
            featureRepository.saveAndFlush(workspace);
            mockMvc.perform(get("/api/admin/subscriptions/assignable-plan-prices")
                            .header("Authorization", bearer(chooser.token()))
                            .param("search", "free"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        } finally {
            workspace.setRuntimeEnabled(runtimeEnabled);
            featureRepository.saveAndFlush(workspace);
        }

        boolean publicVisible = workspace.isPublicVisible();
        try {
            workspace.setPublicVisible(false);
            featureRepository.saveAndFlush(workspace);
            mockMvc.perform(get("/api/admin/subscriptions/assignable-plan-prices")
                            .header("Authorization", bearer(chooser.token()))
                            .param("search", "free"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].planCode").value("FREE"));
        } finally {
            workspace.setPublicVisible(publicVisible);
            featureRepository.saveAndFlush(workspace);
        }

        // This permission exposes only the exact choices needed by subscription creation, not
        // the full commercial price-book control plane or extension owners.
        mockMvc.perform(get("/api/admin/product-prices")
                        .header("Authorization", bearer(chooser.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/subscriptions/assignable-plan-prices")
                        .header("Authorization", bearer(chooser.token()))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/subscriptions/assignable-plan-prices")
                        .header("Authorization", bearer(chooser.token()))
                        .param("currencyCode", "not-a-currency"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/subscriptions/assignable-plan-prices")
                        .header("Authorization", bearer(chooser.token()))
                        .param("billingCycle", "FOREVER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void subscriptionAccountTableChooserAndHydrationHaveSeparatePermissionNodes() throws Exception {
        String clientToken = registerClientAndGetToken();
        LimitedAdmin tableReader = createLimitedAdmin("platform.subscriptions.search_accounts");
        LimitedAdmin ownerIdentityReader = createLimitedAdmin(
                "platform.subscriptions.lookup_account_owner_email");
        LimitedAdmin chooser = createLimitedAdmin("platform.subscriptions.choose_accounts");
        LimitedAdmin resolver = createLimitedAdmin("platform.subscriptions.resolve_account_choices");

        mockMvc.perform(get("/api/admin/subscriptions/accounts/search"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(tableReader.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());
        mockMvc.perform(post("/api/admin/subscriptions/accounts/by-owner-email")
                        .header("Authorization", bearer(tableReader.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerEmail\":\"owner@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/chooser")
                        .header("Authorization", bearer(tableReader.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        mockMvc.perform(get("/api/admin/subscriptions/accounts/chooser")
                        .header("Authorization", bearer(chooser.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(chooser.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/subscriptions/accounts/chooser/selected")
                        .header("Authorization", bearer(chooser.token()))
                        .param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/subscriptions/accounts/chooser/selected")
                        .header("Authorization", bearer(resolver.token()))
                        .param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post("/api/admin/subscriptions/accounts/by-owner-email")
                        .header("Authorization", bearer(ownerIdentityReader.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerEmail\":\"missing@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(ownerIdentityReader.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void subscriptionChangeReviewApplyAndCancelUseSeparatePermissionNodes() throws Exception {
        UUID missingAccountId = UUID.randomUUID();
        UUID missingOperationId = UUID.randomUUID();
        LimitedAdmin previewer = createLimitedAdmin("platform.subscriptions.preview_change");
        LimitedAdmin applier = createLimitedAdmin("platform.subscriptions.apply_change");
        LimitedAdmin canceller = createLimitedAdmin("platform.subscriptions.cancel_change");
        LimitedAdmin changeReader = createLimitedAdmin("platform.subscriptions.read_changes");
        LimitedAdmin optionChooser = createLimitedAdmin(
                "platform.subscriptions.choose_change_options");
        String selection = """
                {"targetPlanCode":"PRO","addOnCodes":[],"quotaPackages":[],"timing":"AT_RENEWAL"}
                """;

        mockMvc.perform(get(
                                "/api/admin/subscriptions/account/{id}/change-catalog",
                                missingAccountId)
                        .header("Authorization", bearer(previewer.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get(
                                "/api/admin/subscriptions/account/{id}/change-catalog",
                                missingAccountId)
                        .header("Authorization", bearer(optionChooser.token())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/subscriptions/account/{id}/changes/preview", missingAccountId)
                        .header("Authorization", bearer(applier.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(post("/api/admin/subscriptions/account/{id}/changes/preview", missingAccountId)
                        .header("Authorization", bearer(previewer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection))
                .andExpect(status().isNotFound());

        String applyBody = """
                {"selection":%s,"previewToken":"not-valid-evidence","reason":"Permission boundary test"}
                """.formatted(selection.strip());
        mockMvc.perform(post("/api/admin/subscriptions/account/{id}/changes/apply", missingAccountId)
                        .header("Authorization", bearer(previewer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(post("/api/admin/subscriptions/account/{id}/changes/apply", missingAccountId)
                        .header("Authorization", bearer(applier.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody))
                .andExpect(status().isNotFound());

        String cancelBody = "{\"reason\":\"Permission boundary test\"}";
        mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{id}/changes/{operationId}/cancel",
                                missingAccountId, missingOperationId)
                        .header("Authorization", bearer(applier.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{id}/changes/{operationId}/cancel",
                                missingAccountId, missingOperationId)
                        .header("Authorization", bearer(canceller.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/subscriptions/account/{id}/changes", missingAccountId)
                        .header("Authorization", bearer(previewer.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/api/admin/subscriptions/account/{id}/changes", missingAccountId)
                        .header("Authorization", bearer(changeReader.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void scheduledReplacementPreviewAndExecutionUseSeparatePermissionNodes() throws Exception {
        UUID currentId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();
        String previewBody = "{\"currentPriceId\":\"" + currentId
                + "\",\"currentVersion\":0,\"successorVersion\":0}";
        String executionBody = "{\"currentPriceId\":\"" + currentId
                + "\",\"currentVersion\":0,\"successorVersion\":0,\"reason\":\"Scheduled change\"}";
        LimitedAdmin previewer = createLimitedAdmin("platform.price_books.preview_replacement");
        LimitedAdmin scheduler = createLimitedAdmin("platform.price_books.schedule_replacement");

        mockMvc.perform(post("/api/admin/product-prices/{id}/replacement-preview", successorId)
                        .header("Authorization", bearer(previewer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/product-prices/{id}/schedule-replacement", successorId)
                        .header("Authorization", bearer(previewer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executionBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/product-prices/{id}/replacement-preview", successorId)
                        .header("Authorization", bearer(scheduler.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/product-prices/{id}/schedule-replacement", successorId)
                        .header("Authorization", bearer(scheduler.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executionBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void registryCatalogEndpointsRequireAdminRegistryPermission() throws Exception {
        String clientToken = registerClientAndGetToken();
        LimitedAdmin admin = createLimitedAdmin("platform.plans.list");

        mockMvc.perform(get("/api/admin/registry/feature-catalog"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/registry/feature-catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/registry/feature-catalog")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/registry/permission-catalog")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void segmentMutationPreviewIdentityAndOwnerSurfacesUseIndependentPermissionNodes()
            throws Exception {
        UUID missingSegmentId = UUID.randomUUID();
        UUID missingActivationId = UUID.randomUUID();
        LimitedAdmin creator = createLimitedAdmin("platform.segments.create");
        LimitedAdmin counter = createLimitedAdmin("platform.segments.count");
        LimitedAdmin previewer = createLimitedAdmin("platform.segments.preview");
        LimitedAdmin identityReader = createLimitedAdmin(
                "platform.segments.read_sample_identities",
                "platform.segments.read_activation_identities");
        LimitedAdmin audienceReader = createLimitedAdmin(
                "platform.segments.read_activation_audience");
        LimitedAdmin detailReader = createLimitedAdmin("platform.segments.read_detail");
        LimitedAdmin ownerReader = createLimitedAdmin("platform.segments.read_owner");
        CommercialSegmentRequests.Create request = new CommercialSegmentRequests.Create(
                "Permission boundary Segment", null,
                CommercialSegmentKind.EXPLICIT_ACCOUNTS, CommercialSegmentSource.MANUAL,
                "Exercise independent Segment nodes", new CommercialSegmentRequests.Definition(
                        Set.of(UUID.randomUUID()), null));

        mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(previewer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(creator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/segments/{id}/preview", missingSegmentId)
                        .header("Authorization", bearer(identityReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/segments/{id}/preview", missingSegmentId)
                        .header("Authorization", bearer(previewer.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/segments/{id}/count", missingSegmentId)
                        .header("Authorization", bearer(previewer.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/segments/{id}/count", missingSegmentId)
                        .header("Authorization", bearer(counter.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/segments/{id}/preview-identities", missingSegmentId)
                        .header("Authorization", bearer(previewer.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/segments/{id}/preview-identities", missingSegmentId)
                        .header("Authorization", bearer(identityReader.token())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(
                                "/api/admin/segments/{id}/activations/{activationId}/accounts",
                                missingSegmentId, missingActivationId)
                        .header("Authorization", bearer(identityReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(
                                "/api/admin/segments/{id}/activations/{activationId}/accounts",
                                missingSegmentId, missingActivationId)
                        .header("Authorization", bearer(audienceReader.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(
                                "/api/admin/segments/{id}/activations/{activationId}/identities",
                                missingSegmentId, missingActivationId)
                        .header("Authorization", bearer(audienceReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(
                                "/api/admin/segments/{id}/activations/{activationId}/identities",
                                missingSegmentId, missingActivationId)
                        .header("Authorization", bearer(identityReader.token())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/segments/{id}", missingSegmentId)
                        .header("Authorization", bearer(ownerReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/segments/{id}", missingSegmentId)
                        .header("Authorization", bearer(detailReader.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/segments/{id}/owner", missingSegmentId)
                        .header("Authorization", bearer(detailReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/segments/{id}/owner", missingSegmentId)
                        .header("Authorization", bearer(ownerReader.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void segmentAccountChooserAndSelectedHydrationUseIndependentPermissionNodes() throws Exception {
        String clientToken = registerClientAndGetToken();
        LimitedAdmin detailReader = createLimitedAdmin("platform.segments.read_detail");
        LimitedAdmin chooser = createLimitedAdmin("platform.segments.choose_accounts");
        LimitedAdmin resolver = createLimitedAdmin("platform.segments.resolve_account_choices");

        mockMvc.perform(get("/api/admin/segments/account-choices"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/segments/account-choices")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/segments/account-choices")
                        .header("Authorization", bearer(detailReader.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/segments/account-choices")
                        .header("Authorization", bearer(chooser.token()))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());
        mockMvc.perform(get("/api/admin/segments/account-choices/selected")
                        .header("Authorization", bearer(chooser.token()))
                        .param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/segments/account-choices/selected")
                        .header("Authorization", bearer(resolver.token()))
                        .param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        UUID duplicate = UUID.randomUUID();
        mockMvc.perform(get("/api/admin/segments/account-choices/selected")
                        .header("Authorization", bearer(resolver.token()))
                        .param("ids", duplicate.toString(), duplicate.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/segments/account-choices")
                        .header("Authorization", bearer(resolver.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void policySegmentChooserAndSelectedHydrationUseIndependentPermissionNodes() throws Exception {
        String clientToken = registerClientAndGetToken();
        LimitedAdmin detailReader = createLimitedAdmin("platform.commercial_policies.read");
        LimitedAdmin chooser = createLimitedAdmin("platform.commercial_policies.choose_segments");
        LimitedAdmin resolver = createLimitedAdmin(
                "platform.commercial_policies.resolve_segment_choices");

        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices")
                        .header("Authorization", bearer(detailReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices")
                        .header("Authorization", bearer(chooser.token()))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(1));
        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices/selected")
                        .header("Authorization", bearer(chooser.token()))
                        .param("references", "MISSING"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices/selected")
                        .header("Authorization", bearer(resolver.token()))
                        .param("references", "MISSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices/selected")
                        .header("Authorization", bearer(resolver.token()))
                        .param("references", "DUPLICATE", "duplicate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices")
                        .header("Authorization", bearer(resolver.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void segmentLifecycleMutationLeavesAndHistoryAreIndependentlyEnforced() throws Exception {
        UUID missing = UUID.randomUUID();
        LimitedAdmin unrelated = createLimitedAdmin("platform.segments.read_detail");
        LimitedAdmin updater = createLimitedAdmin("platform.segments.update_draft");
        LimitedAdmin activator = createLimitedAdmin("platform.segments.activate");
        LimitedAdmin reviser = createLimitedAdmin("platform.segments.revise");
        LimitedAdmin archiver = createLimitedAdmin("platform.segments.archive");
        LimitedAdmin deleter = createLimitedAdmin("platform.segments.delete_draft");
        LimitedAdmin historian = createLimitedAdmin("platform.segments.read_history");
        LimitedAdmin ownerManager = createLimitedAdmin("platform.segments.reassign_owner");
        String versionReason = objectMapper.writeValueAsString(
                new CommercialSegmentRequests.VersionReason(0L, "Permission boundary"));
        String activation = objectMapper.writeValueAsString(
                new CommercialSegmentRequests.Activation(0L, "Permission boundary", "signed-evidence"));
        String update = objectMapper.writeValueAsString(new CommercialSegmentRequests.Update(
                0L, "Missing Segment", null, CommercialSegmentKind.EXPLICIT_ACCOUNTS,
                CommercialSegmentSource.MANUAL, "Permission boundary",
                new CommercialSegmentRequests.Definition(Set.of(UUID.randomUUID()), null)));
        UUID ownerId = UUID.randomUUID();
        String owner = objectMapper.writeValueAsString(
                new CommercialSegmentRequests.ReassignOwner(0L, ownerId, "Permission boundary"));

        assertSegmentMutationPermission("put", missing, update, unrelated, updater,
                "/api/admin/segments/{id}");
        assertSegmentMutationPermission("post", missing, activation, unrelated, activator,
                "/api/admin/segments/{id}/activate");
        assertSegmentMutationPermission("post", missing, versionReason, unrelated, reviser,
                "/api/admin/segments/{id}/revisions");
        assertSegmentMutationPermission("post", missing, versionReason, unrelated, archiver,
                "/api/admin/segments/{id}/archive");
        assertSegmentMutationPermission("delete", missing, versionReason, unrelated, deleter,
                "/api/admin/segments/{id}");
        assertSegmentMutationPermission("put", missing, owner, unrelated, ownerManager,
                "/api/admin/segments/{id}/owner");

        mockMvc.perform(get("/api/admin/segments/{id}/history", missing)
                        .header("Authorization", bearer(unrelated.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/segments/{id}/history", missing)
                        .header("Authorization", bearer(historian.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void campaignOpaqueIdentityAndMutationSurfacesStaySeparatelyAuthorized() throws Exception {
        UUID missing = UUID.randomUUID();
        LimitedAdmin opaqueReader = createLimitedAdmin(
                "platform.campaigns.list", "platform.campaigns.read",
                "platform.campaigns.compare", "platform.campaigns.revisions",
                "platform.campaigns.history", "platform.campaigns.preview_schedule",
                "platform.campaigns.read_audience");
        LimitedAdmin identityReader = createLimitedAdmin(
                "platform.campaigns.owner", "platform.campaigns.read_audience_identities");
        LimitedAdmin operationsReader = createLimitedAdmin("platform.campaigns.read_operations");
        LimitedAdmin editableDefinitionReader = createLimitedAdmin(
                "platform.campaigns.read_editable_definition");
        LimitedAdmin mutator = createLimitedAdmin(
                "platform.campaigns.create", "platform.campaigns.update",
                "platform.campaigns.duplicate", "platform.campaigns.revise",
                "platform.campaigns.schedule", "platform.campaigns.pause",
                "platform.campaigns.resume", "platform.campaigns.end",
                "platform.campaigns.archive", "platform.campaigns.delete_draft",
                "platform.campaigns.reassign_owner");

        mockMvc.perform(get("/api/admin/campaigns/{id}", missing)
                        .header("Authorization", bearer(identityReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/{id}", missing)
                        .header("Authorization", bearer(opaqueReader.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/campaigns/{id}/operations", missing)
                        .header("Authorization", bearer(opaqueReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/{id}/operations", missing)
                        .header("Authorization", bearer(operationsReader.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/campaigns/{id}/editable-definition", missing)
                        .header("Authorization", bearer(mutator.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/{id}/editable-definition", missing)
                        .header("Authorization", bearer(editableDefinitionReader.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/campaigns/{id}/owner", missing)
                        .header("Authorization", bearer(opaqueReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/{id}/owner", missing)
                        .header("Authorization", bearer(identityReader.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/campaigns/{id}/audience-identities", missing)
                        .header("Authorization", bearer(opaqueReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/{id}/audience-identities", missing)
                        .header("Authorization", bearer(identityReader.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/campaigns/{id}/audience", missing)
                        .header("Authorization", bearer(identityReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/{id}/audience", missing)
                        .header("Authorization", bearer(opaqueReader.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/campaigns/{id}/schedule-preview", missing)
                        .header("Authorization", bearer(mutator.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/{id}/schedule-preview", missing)
                        .header("Authorization", bearer(opaqueReader.token())))
                .andExpect(status().isNotFound());

        String lifecycleBody = objectMapper.writeValueAsString(
                new CommercialCampaignRequests.VersionReason(0L, "Permission boundary"));
        mockMvc.perform(post("/api/admin/campaigns/{id}/pause", missing)
                        .header("Authorization", bearer(opaqueReader.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(lifecycleBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/campaigns/{id}/pause", missing)
                        .header("Authorization", bearer(mutator.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(lifecycleBody))
                .andExpect(status().isNotFound());

        Instant start = Instant.now().plusSeconds(3600);
        var request = new CommercialCampaignRequests.Create(
                "Permission boundary Campaign " + UUID.randomUUID(), null,
                start, start.plusSeconds(3600), CommercialCampaignSource.MARKETING,
                "Exercise the Campaign create node",
                new CommercialCampaignRequests.Audience(
                        CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null));
        mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer(opaqueReader.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer(mutator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void campaignAccountSegmentAndOwnerChoosersAreNarrowAndIndependentlyAuthorized() throws Exception {
        String clientToken = registerClientAndGetToken();
        LimitedAdmin reader = createLimitedAdmin("platform.campaigns.read");
        LimitedAdmin accountChooser = createLimitedAdmin(
                "platform.campaigns.choose_accounts",
                "platform.campaigns.resolve_account_choices");
        LimitedAdmin segmentChooser = createLimitedAdmin(
                "platform.campaigns.choose_segments",
                "platform.campaigns.resolve_segment_choices");
        LimitedAdmin ownerChooser = createLimitedAdmin(
                "platform.campaigns.choose_owners",
                "platform.campaigns.resolve_owner_choices");

        mockMvc.perform(get("/api/admin/campaigns/account-choices"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/campaigns/account-choices")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/account-choices")
                        .header("Authorization", bearer(reader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/account-choices")
                        .header("Authorization", bearer(accountChooser.token())).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());
        mockMvc.perform(get("/api/admin/campaigns/account-choices/selected")
                        .header("Authorization", bearer(segmentChooser.token()))
                        .param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/account-choices/selected")
                        .header("Authorization", bearer(accountChooser.token()))
                        .param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/admin/campaigns/segment-choices")
                        .header("Authorization", bearer(accountChooser.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/segment-choices")
                        .header("Authorization", bearer(segmentChooser.token())).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/api/admin/campaigns/segment-choices/selected")
                        .header("Authorization", bearer(accountChooser.token()))
                        .param("segmentId", UUID.randomUUID().toString())
                        .param("activationId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/segment-choices/selected")
                        .header("Authorization", bearer(segmentChooser.token()))
                        .param("segmentId", UUID.randomUUID().toString())
                        .param("activationId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/campaigns/owner-choices")
                        .header("Authorization", bearer(reader.token())))
                .andExpect(status().isForbidden());
        JsonNode firstOwnerPage = responseJson(mockMvc.perform(get("/api/admin/campaigns/owner-choices")
                        .header("Authorization", bearer(ownerChooser.token()))
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").isString())
                .andExpect(jsonPath("$.content[0].roles").doesNotExist())
                .andExpect(jsonPath("$.content[0].superAdmin").doesNotExist()));
        JsonNode repeatedOwnerPage = responseJson(mockMvc.perform(
                        get("/api/admin/campaigns/owner-choices")
                                .header("Authorization", bearer(ownerChooser.token()))
                                .param("page", "0").param("size", "2"))
                .andExpect(status().isOk()));
        assertThat(repeatedOwnerPage.get("content")).isEqualTo(firstOwnerPage.get("content"));
        mockMvc.perform(get("/api/admin/campaigns/owner-choices/selected")
                        .header("Authorization", bearer(segmentChooser.token()))
                        .param("ids", ownerChooser.adminUserId().toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/owner-choices/selected")
                        .header("Authorization", bearer(ownerChooser.token()))
                        .param("ids", ownerChooser.adminUserId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].adminUserId")
                        .value(ownerChooser.adminUserId().toString()));
    }

    @Test
    void campaignOwnerManagementIsComposableWithoutLeakingCampaignDetail() throws Exception {
        String superToken = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3600);
        var create = new CommercialCampaignRequests.Create(
                "Owner-only Campaign " + UUID.randomUUID(), "Sensitive description",
                start, start.plusSeconds(3600), CommercialCampaignSource.MARKETING,
                "Sensitive commercial reason",
                new CommercialCampaignRequests.Audience(
                        CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null));
        JsonNode campaign = responseJson(mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated()));
        UUID campaignId = UUID.fromString(campaign.get("campaignId").asText());
        LimitedAdmin ownerManager = createLimitedAdmin(
                "platform.campaigns.owner",
                "platform.campaigns.reassign_owner",
                "platform.campaigns.choose_owners",
                "platform.campaigns.resolve_owner_choices");

        mockMvc.perform(get("/api/admin/campaigns/{id}", campaignId)
                        .header("Authorization", bearer(ownerManager.token())))
                .andExpect(status().isForbidden());

        JsonNode owner = responseJson(mockMvc.perform(get("/api/admin/campaigns/{id}/owner", campaignId)
                        .header("Authorization", bearer(ownerManager.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").isNumber())
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist()));

        var reassign = new CommercialCampaignRequests.ReassignOwner(
                owner.get("version").asLong(), ownerManager.adminUserId(), "Transfer ownership");
        mockMvc.perform(put("/api/admin/campaigns/{id}/owner", campaignId)
                        .header("Authorization", bearer(ownerManager.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(owner.get("version").asLong() + 1))
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist());

        mockMvc.perform(get("/api/admin/campaigns/{id}/owner", campaignId)
                        .header("Authorization", bearer(ownerManager.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminUserId").value(ownerManager.adminUserId().toString()));
    }

    @Test
    void campaignOperationsAndEditingUseNarrowContractsWithoutLeakingDetail() throws Exception {
        String superToken = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3600);
        var create = new CommercialCampaignRequests.Create(
                "Narrow Campaign " + UUID.randomUUID(), "Sensitive description",
                start, start.plusSeconds(3600), CommercialCampaignSource.MARKETING,
                "Sensitive commercial reason",
                new CommercialCampaignRequests.Audience(
                        CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null));
        JsonNode created = responseJson(mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaignId").isString())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").isNumber())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.audience").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist()));
        UUID campaignId = UUID.fromString(created.get("campaignId").asText());

        LimitedAdmin operator = createLimitedAdmin(
                "platform.campaigns.read_operations", "platform.campaigns.update");
        mockMvc.perform(get("/api/admin/campaigns/{id}/operations", campaignId)
                        .header("Authorization", bearer(operator.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campaignId.toString()))
                .andExpect(jsonPath("$.availableActions", hasItem("UPDATE")))
                .andExpect(jsonPath("$.audience").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.owner").doesNotExist());
        mockMvc.perform(get("/api/admin/campaigns/{id}", campaignId)
                        .header("Authorization", bearer(operator.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/{id}/editable-definition", campaignId)
                        .header("Authorization", bearer(operator.token())))
                .andExpect(status().isForbidden());

        LimitedAdmin editor = createLimitedAdmin(
                "platform.campaigns.read_editable_definition", "platform.campaigns.update");
        JsonNode editable = responseJson(mockMvc.perform(
                        get("/api/admin/campaigns/{id}/editable-definition", campaignId)
                                .header("Authorization", bearer(editor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.description").value("Sensitive description"))
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.availableActions").doesNotExist()));
        var update = new CommercialCampaignRequests.Update(
                editable.get("version").asLong(), editable.get("name").asText(),
                "Updated without broad detail", start, start.plusSeconds(5400),
                CommercialCampaignSource.MARKETING, "Narrow update",
                new CommercialCampaignRequests.Audience(
                        CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null));
        mockMvc.perform(put("/api/admin/campaigns/{id}", campaignId)
                        .header("Authorization", bearer(editor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(editable.get("version").asLong() + 1))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.audience").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist());
        mockMvc.perform(get("/api/admin/campaigns/{id}", campaignId)
                        .header("Authorization", bearer(editor.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void campaignScheduleActionRequiresBothPreviewAndApplyPermissions() throws Exception {
        String superToken = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3600);
        var request = new CommercialCampaignRequests.Create(
                "Composable Campaign " + UUID.randomUUID(), null,
                start, start.plusSeconds(3600), CommercialCampaignSource.MARKETING,
                "Verify action composition",
                new CommercialCampaignRequests.Audience(
                        CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null));
        JsonNode campaign = responseJson(mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
        LimitedAdmin scheduleOnly = createLimitedAdmin(
                "platform.campaigns.read", "platform.campaigns.schedule");

        mockMvc.perform(get("/api/admin/campaigns/{id}",
                        campaign.get("campaignId").asText())
                        .header("Authorization", bearer(scheduleOnly.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.availableActions",
                        not(hasItem("SCHEDULE"))));

        LimitedAdmin reviewerAndScheduler = createLimitedAdmin(
                "platform.campaigns.read", "platform.campaigns.preview_schedule",
                "platform.campaigns.schedule");
        JsonNode preview = responseJson(mockMvc.perform(
                        get("/api/admin/campaigns/{id}/schedule-preview",
                                campaign.get("campaignId").asText())
                                .header("Authorization", bearer(reviewerAndScheduler.token())))
                .andExpect(status().isOk()));
        List<UUID> remainingPermissionIds = List.of(
                permissionRepository.findByCode("platform.campaigns.read").orElseThrow().getId(),
                permissionRepository.findByCode("platform.campaigns.schedule").orElseThrow().getId());
        JsonNode rolePreview = responseJson(mockMvc.perform(
                        post("/api/admin/roles/{id}/impact-preview", reviewerAndScheduler.roleId())
                                .header("Authorization", bearer(superToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new AdminRoleImpactRequest(remainingPermissionIds, null))))
                .andExpect(status().isOk()));
        mockMvc.perform(put("/api/admin/roles/{id}/permissions", reviewerAndScheduler.roleId())
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminRolePermissionSetRequest(
                                remainingPermissionIds,
                                rolePreview.get("version").asLong(),
                                rolePreview.get("assignmentCount").asLong()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/campaigns/{id}/schedule",
                        campaign.get("campaignId").asText())
                        .header("Authorization", bearer(reviewerAndScheduler.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialCampaignRequests.Schedule(
                                campaign.get("version").asLong(),
                                "Reauthorize both reviewed operations",
                                preview.get("previewToken").asText()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void latestRegistrySynchronizationIsPermissionProtectedAndInspectable() throws Exception {
        LimitedAdmin unrelated = createLimitedAdmin("platform.plans.list");
        LimitedAdmin registryOperator = createLimitedAdmin("platform.registry.sync_status");

        mockMvc.perform(get("/api/admin/registry/synchronization/latest")
                        .header("Authorization", bearer(unrelated.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/registry/synchronization/latest")
                        .header("Authorization", bearer(registryOperator.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.snapshotHash").isNotEmpty())
                .andExpect(jsonPath("$.discoveredFeatures").isNumber())
                .andExpect(jsonPath("$.discoveredPermissions").isNumber())
                .andExpect(jsonPath("$.details").value(
                        "Authoritative registry snapshot synchronized"));
    }

    @Test
    void planAssignableFeatureCatalogDoesNotExposeControlPlaneFeatures() throws Exception {
        LimitedAdmin admin = createLimitedAdmin("platform.registry.feature_catalog");

        mockMvc.perform(get("/api/admin/registry/feature-catalog")
                        .param("audience", "PLAN_ASSIGNABLE")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..code", hasItem("platform.company")))
                .andExpect(jsonPath("$..code", not(hasItem("platform.plans"))))
                .andExpect(jsonPath("$..code", not(hasItem("platform.registry"))))
                .andExpect(jsonPath("$..planAssignable", everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    void b2bPermissionCatalogExposesOnlyExplicitDelegatableActions() throws Exception {
        LimitedAdmin admin = createLimitedAdmin("platform.registry.permission_catalog");

        mockMvc.perform(get("/api/admin/registry/permission-catalog")
                        .param("audience", "B2B_DELEGATABLE")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..permissions[*].code", hasItem("platform.company.read_single")))
                .andExpect(jsonPath("$..permissions[*].code", not(hasItem("platform.company.create"))))
                .andExpect(jsonPath("$..permissions[*].code", not(hasItem("platform.company.delete"))))
                .andExpect(jsonPath("$..permissions[*].code", not(hasItem("platform.registry.read"))));
    }

    @Test
    void platformAdminPermissionCatalogExcludesClientWorkspacePermissions() throws Exception {
        LimitedAdmin admin = createLimitedAdmin("platform.registry.permission_catalog");

        mockMvc.perform(get("/api/admin/registry/permission-catalog")
                        .param("audience", "PLATFORM_ADMIN_ROLE_GRANTABLE")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..permissions[*].code", hasItem("platform.registry.read")))
                .andExpect(jsonPath("$..permissions[*].code", hasItem("platform.plans.create")))
                .andExpect(jsonPath("$..permissions[*].code", everyItem(startsWith("platform."))))
                .andExpect(jsonPath("$..permissions[*].code", not(hasItem("platform.company.create"))))
                .andExpect(jsonPath("$..permissions[*].code", not(hasItem("platform.staff.read"))));
    }

    @Test
    void nonSuperAdminCannotGrantPermissionTheyDoNotHold() throws Exception {
        LimitedAdmin grantor = createLimitedAdmin("platform.roles.grant_permission");
        UUID targetRoleId = createAdminRole(loginAdminAndGetToken(), "Restricted target");

        grantPermission(grantor.token(), targetRoleId, "platform.registry.read")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A platform administrator cannot grant a permission they do not hold."));
    }

    @Test
    void nonSuperAdminCannotAssignRoleContainingPermissionTheyDoNotHold() throws Exception {
        LimitedAdmin assigner = createLimitedAdmin("platform.admin_users.assign_role");
        String superToken = loginAdminAndGetToken();
        UUID elevatedRoleId = createAdminRole(superToken, "Elevated target");
        grantPermission(superToken, elevatedRoleId, "platform.registry.read")
                .andExpect(status().isNoContent());

        assignRole(assigner.token(), assigner.adminUserId(), elevatedRoleId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A platform administrator cannot assign a role containing permissions they do not hold."));
    }

    @Test
    void nonSuperAdminCannotCreateAnotherSuperAdmin() throws Exception {
        LimitedAdmin creator = createLimitedAdmin("platform.admin_users.create");

        createAdminUser(creator.token(), operatorEmail(), true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only a SuperAdmin can create another SuperAdmin."));
    }

    @Test
    void adminCannotDeactivateSelfAndDeactivatedAdminTokenStopsWorking() throws Exception {
        LimitedAdmin admin = createLimitedAdmin("platform.admin_users.toggle_active");

        toggleAdmin(admin.token(), admin.adminUserId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An administrator cannot deactivate their own account."));

        toggleAdmin(loginAdminAndGetToken(), admin.adminUserId())
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonSuperAdminCannotDeactivateSuperAdmin() throws Exception {
        String superToken = loginAdminAndGetToken();
        UUID superAdminId = currentAdminId(superToken);
        LimitedAdmin admin = createLimitedAdmin("platform.admin_users.toggle_active");

        toggleAdmin(admin.token(), superAdminId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only a SuperAdmin can modify another SuperAdmin."));

        mockMvc.perform(get("/api/admin/me")
                .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void lastActiveSuperAdminCannotDeactivateSelf() throws Exception {
        String superToken = loginAdminAndGetToken();

        toggleAdmin(superToken, currentAdminId(superToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("An administrator cannot deactivate their own account."));
    }

    @Test
    void nonSuperAdminCannotAssignOrRemoveRolesOnSuperAdmin() throws Exception {
        String superToken = loginAdminAndGetToken();
        UUID superAdminId = currentAdminId(superToken);
        UUID assignedRoleId = createAdminRole(superToken, "Existing super role " + UUID.randomUUID());
        UUID unassignedRoleId = createAdminRole(superToken, "New super role " + UUID.randomUUID());
        assignRole(superToken, superAdminId, assignedRoleId).andExpect(status().isNoContent());
        LimitedAdmin admin = createLimitedAdmin(
                "platform.admin_users.assign_role",
                "platform.admin_users.remove_role");

        assignRole(admin.token(), superAdminId, unassignedRoleId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only a SuperAdmin can modify another SuperAdmin."));

        removeRole(admin.token(), superAdminId, assignedRoleId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only a SuperAdmin can modify another SuperAdmin."));
    }

    @Test
    void nonSuperAdminCannotModifyTheirOwnRoleAssignments() throws Exception {
        String superToken = loginAdminAndGetToken();
        // Empty permission set, so the actor's grant ceiling cannot be the reason for refusal.
        UUID assignableRoleId = createAdminRole(superToken, "Self assignment target " + UUID.randomUUID());
        LimitedAdmin admin = createLimitedAdmin(
                "platform.admin_users.assign_role",
                "platform.admin_users.remove_role");

        assignRole(admin.token(), admin.adminUserId(), assignableRoleId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("A platform administrator cannot modify their own role assignments."));

        removeRole(admin.token(), admin.adminUserId(), admin.roleId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("A platform administrator cannot modify their own role assignments."));

        // The rule blocks the actor, not the change: another authorized operator makes it normally.
        assignRole(superToken, admin.adminUserId(), assignableRoleId)
                .andExpect(status().isNoContent());
    }

    @Test
    void replacingAnOperatorRoleSetIsAtomic() throws Exception {
        String token = loginAdminAndGetToken();
        UUID operatorId = createdOperatorId(createAdminUser(token, operatorEmail(), false)
                .andExpect(status().isCreated()));
        UUID existingRoleId = createAdminRole(token, "Atomic existing " + UUID.randomUUID());
        UUID newRoleId = createAdminRole(token, "Atomic new " + UUID.randomUUID());
        assignRole(token, operatorId, existingRoleId).andExpect(status().isNoContent());

        UUID missingRoleId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        mockMvc.perform(put("/api/admin/users/{id}/roles", operatorId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.hiveapp.platform.admin.dto.ReplaceAdminRolesRequest(
                                        java.util.Set.of(newRoleId, missingRoleId)))))
                .andExpect(status().isNotFound());

        // The valid addition happened before the missing id failed. The outer transaction must
        // roll it back, and must also preserve the role that the requested set would remove.
        mockMvc.perform(get("/api/admin/users/{id}", operatorId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[*].id", hasItem(existingRoleId.toString())))
                .andExpect(jsonPath("$.roles[*].id", not(hasItem(newRoleId.toString()))));
    }

    @Test
    void nonSuperAdminCannotDeactivateRoleAboveTheirPermissionCeiling() throws Exception {
        String superToken = loginAdminAndGetToken();
        UUID targetRoleId = createAdminRole(superToken, "Elevated toggle target " + UUID.randomUUID());
        grantPermission(superToken, targetRoleId, "platform.registry.read").andExpect(status().isNoContent());
        LimitedAdmin admin = createLimitedAdmin("platform.roles.toggle_active");

        toggleRole(admin.token(), targetRoleId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A platform administrator cannot activate or deactivate a role containing permissions they do not hold."));
    }

    @Test
    void permissionRevocationObeysActorCeilingButAllowsOwnedPermission() throws Exception {
        String superToken = loginAdminAndGetToken();
        UUID permissionId = permissionRepository.findByCode("platform.registry.read").orElseThrow().getId();

        UUID elevatedRoleId = createAdminRole(superToken, "Elevated revoke target " + UUID.randomUUID());
        grantPermission(superToken, elevatedRoleId, "platform.registry.read").andExpect(status().isNoContent());
        LimitedAdmin restricted = createLimitedAdmin("platform.roles.revoke_permission");

        revokePermission(restricted.token(), elevatedRoleId, permissionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A platform administrator cannot modify a role containing permissions they do not hold."));

        UUID allowedRoleId = createAdminRole(superToken, "Allowed revoke target " + UUID.randomUUID());
        grantPermission(superToken, allowedRoleId, "platform.registry.read").andExpect(status().isNoContent());
        LimitedAdmin allowed = createLimitedAdmin(
                "platform.roles.revoke_permission",
                "platform.registry.read");

        revokePermission(allowed.token(), allowedRoleId, permissionId)
                .andExpect(status().isNoContent());
    }

    @Test
    void controlPlaneFeatureCannotExposeEmergencyRuntimeControl() throws Exception {
        String superToken = loginAdminAndGetToken();
        UUID featureId = featureRepository.findByCode("platform.plans").orElseThrow().getId();

        updateEmergencyRuntime(superToken, featureId, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Feature platform.plans does not expose the EMERGENCY_RUNTIME operator control."));
    }

    @Test
    void publicVisibilityCanChangeWithoutChangingOtherOperationalControls() throws Exception {
        String superToken = loginAdminAndGetToken();
        UUID featureId = featureRepository.findByCode("platform.company").orElseThrow().getId();

        updateFeatureControl(superToken, featureId, "public-visibility", false)
                .andExpect(status().isNoContent());
        var feature = featureRepository.findById(featureId).orElseThrow();
        assertThat(feature.isPublicVisible()).isFalse();
        assertThat(feature.isNewSalesEnabled()).isTrue();
        assertThat(feature.isNewGrantsEnabled()).isTrue();
        assertThat(feature.isRuntimeEnabled()).isTrue();

        updateFeatureControl(superToken, featureId, "public-visibility", true)
                .andExpect(status().isNoContent());
    }

    @Test
    void featureControlRequiresItsDedicatedPermission() throws Exception {
        LimitedAdmin admin = createLimitedAdmin("platform.registry.feature_catalog");
        UUID featureId = featureRepository.findByCode("platform.b2b").orElseThrow().getId();

        updateFeatureControl(admin.token(), featureId, "new-grants", false)
                .andExpect(status().isForbidden());
    }

    private QuotaActivationFixture createQuotaActivationFixture(String token) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        CreateQuotaPackageRequest request = new CreateQuotaPackageRequest(
                "Composite activation " + suffix, null, "platform.staff", "members", 1,
                new BigDecimal("4.2500"), "USD", BillingCycle.MONTHLY,
                true, 3, java.util.Set.of("FLEX"), java.util.Set.of(),
                ProductSalesVisibility.PUBLIC);
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/admin/quota-packages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID packageId = UUID.fromString(created.get("id").asText());
        JsonNode preview = objectMapper.readTree(mockMvc.perform(
                        get("/api/admin/quota-packages/{id}/activation-preview", packageId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activatable").value(true))
                .andExpect(jsonPath("$.reviewedPrices.length()").value(1))
                .andReturn().getResponse().getContentAsString());
        String lifecycleBody = objectMapper.writeValueAsString(java.util.Map.of(
                "action", "ACTIVATE",
                "expectedVersion", preview.get("expectedVersion").asLong(),
                "reason", "Composite activation security boundary",
                "activationPreviewToken", preview.get("previewToken").asText()));
        return new QuotaActivationFixture(packageId, lifecycleBody);
    }

    private void assertQuotaActivationWasAtomic(QuotaActivationFixture fixture) {
        assertThat(quotaPackageRepository.findById(fixture.packageId()).orElseThrow().getStatus())
                .isEqualTo(com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.DRAFT);
        assertThat(productPriceRepository.findAllByQuotaPackageId(fixture.packageId()))
                .isNotEmpty()
                .allMatch(price -> price.getStatus() == ProductPriceStatus.DRAFT);
    }

    private void removeQuotaActivationFixture(UUID packageId) {
        var prices = productPriceRepository.findAllByQuotaPackageId(packageId);
        if (!prices.isEmpty()) {
            productPriceRepository.deleteAllInBatch(prices);
            productPriceRepository.flush();
        }
        quotaPackageRepository.findById(packageId).ifPresent(quotaPackageRepository::delete);
        quotaPackageRepository.flush();
    }

    private record QuotaActivationFixture(UUID packageId, String lifecycleBody) {}

    @Test
    void emergencyRuntimeCanBeCutOffAndRestoredWithExplicitConfirmation() throws Exception {
        String superToken = loginAdminAndGetToken();
        UUID featureId = featureRepository.findByCode("platform.company").orElseThrow().getId();

        updateEmergencyRuntime(superToken, featureId, false).andExpect(status().isNoContent());
        assertThat(featureRepository.findById(featureId).orElseThrow().isRuntimeEnabled()).isFalse();

        updateEmergencyRuntime(superToken, featureId, true).andExpect(status().isNoContent());
        assertThat(featureRepository.findById(featureId).orElseThrow().isRuntimeEnabled()).isTrue();
    }

    @Test
    void commercialPolicyOwnerIdentityAndOrdinaryDetailHaveIndependentAuthorities() throws Exception {
        String superToken = loginAdminAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID targetAccountId = UUID.fromString(objectMapper.readTree(mockMvc.perform(
                                get("/api/v1/accounts/me")
                                        .header("Authorization", bearer(clientToken)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("id").asText());
        CommercialPolicyRequests.Create request = new CommercialPolicyRequests.Create(
                "Owner privacy policy " + UUID.randomUUID(), null, Instant.now(),
                Instant.now().plusSeconds(3600), CommercialPolicySource.SUPPORT, 5,
                "Verify separate owner authority", null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.ACCOUNT, targetAccountId, Set.of(), null, null),
                List.of(new CommercialPolicyRequests.Effect(
                        CommercialPolicyEffectType.BLOCK_FEATURE, null, null,
                        "platform.staff", null, null, null, null,
                        null, null, null, null)));
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/admin/commercial-policies")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID policyId = UUID.fromString(created.get("summary").get("id").asText());

        LimitedAdmin detailReader = createLimitedAdmin("platform.commercial_policies.read");
        LimitedAdmin ownerReader = createLimitedAdmin("platform.commercial_policies.read_owner");

        mockMvc.perform(get("/api/admin/commercial-policies/{id}", policyId)
                        .header("Authorization", bearer(detailReader.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerAdminUserId").doesNotExist())
                .andExpect(jsonPath("$.summary.ownerAdminUserId").doesNotExist());
        mockMvc.perform(get("/api/admin/commercial-policies/{id}/owner", policyId)
                        .header("Authorization", bearer(detailReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/commercial-policies/{id}/owner", policyId)
                        .header("Authorization", bearer(ownerReader.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
        mockMvc.perform(get("/api/admin/commercial-policies/{id}", policyId)
                        .header("Authorization", bearer(ownerReader.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/commercial-policies/{id}", policyId)
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void offerOperationAndDefinitionReadsAreIndependentAndCheckPermissionBeforeExistence()
            throws Exception {
        UUID absent = UUID.randomUUID();
        LimitedAdmin operations = createLimitedAdmin("platform.offers.read_operations");
        LimitedAdmin definition = createLimitedAdmin("platform.offers.read_editable_definition");

        mockMvc.perform(get("/api/admin/offers/{id}/operations", absent)
                        .header("Authorization", bearer(operations.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/offers/{id}/editable-definition", absent)
                        .header("Authorization", bearer(operations.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        mockMvc.perform(get("/api/admin/offers/{id}/editable-definition", absent)
                        .header("Authorization", bearer(definition.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/offers/{id}/operations", absent)
                        .header("Authorization", bearer(definition.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void operatorOfferApplyRequiresBothOfferAndSubscriptionAuthoritiesBeforeExistence()
            throws Exception {
        UUID absentOffer = UUID.randomUUID();
        UUID absentAccount = UUID.randomUUID();
        CommercialOfferRequests.Accept request =
                new CommercialOfferRequests.Accept("review-token", "Authority matrix");
        LimitedAdmin missingOfferPreview = createLimitedAdmin(
                "platform.offers.apply_for_account",
                "platform.subscriptions.preview_change",
                "platform.subscriptions.apply_change");
        LimitedAdmin missingSubscriptionApply = createLimitedAdmin(
                "platform.offers.apply_for_account",
                "platform.offers.preview_for_account",
                "platform.subscriptions.preview_change");
        LimitedAdmin complete = createLimitedAdmin(
                "platform.offers.apply_for_account",
                "platform.offers.preview_for_account",
                "platform.subscriptions.preview_change",
                "platform.subscriptions.apply_change");

        applyAbsentOffer(missingOfferPreview.token(), absentOffer, absentAccount, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERMISSION_GRANT"));
        applyAbsentOffer(missingSubscriptionApply.token(), absentOffer, absentAccount, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERMISSION_GRANT"));
        applyAbsentOffer(complete.token(), absentOffer, absentAccount, request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_AVAILABLE"));
    }

    @Test
    void offerAggregateStatsAndRedemptionIdentitiesHaveIndependentAuthorities() throws Exception {
        UUID absent = UUID.randomUUID();
        LimitedAdmin stats = createLimitedAdmin("platform.offers.read_stats");
        LimitedAdmin identities =
                createLimitedAdmin("platform.offers.read_redemption_identities");

        mockMvc.perform(get("/api/admin/offers/{id}/stats", absent)
                        .header("Authorization", bearer(stats.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/offers/{id}/redemption-identities", absent)
                        .header("Authorization", bearer(stats.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        mockMvc.perform(get("/api/admin/offers/{id}/redemption-identities", absent)
                        .header("Authorization", bearer(identities.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/offers/{id}/stats", absent)
                        .header("Authorization", bearer(identities.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    private ResultActions applyAbsentOffer(
            String token,
            UUID offerId,
            UUID accountId,
            CommercialOfferRequests.Accept request) throws Exception {
        return mockMvc.perform(post(
                        "/api/admin/offers/{id}/accounts/{accountId}/apply", offerId, accountId)
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", "authority-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private LimitedAdmin createLimitedAdmin(String... permissionCodes) throws Exception {
        String superToken = loginAdminAndGetToken();
        String email = operatorEmail();
        ResultActions created = createAdminUser(superToken, email, false)
                .andExpect(status().isCreated());
        UUID adminUserId = createdOperatorId(created);
        // Creation now emails an activation link and issues no password, so a test that needs a
        // usable session takes the explicit temporary-access fallback — a real supported path
        // rather than a shortcut around the credential flow.
        String temporaryPassword = issueTemporaryAccess(superToken, adminUserId);
        UUID roleId = createAdminRole(superToken, "Limited " + UUID.randomUUID());
        for (String permissionCode : permissionCodes) {
            grantPermission(superToken, roleId, permissionCode)
                    .andExpect(status().isNoContent());
        }
        assignRole(superToken, adminUserId, roleId)
                .andExpect(status().isNoContent());
        return new LimitedAdmin(signInWithTemporaryPassword(email, temporaryPassword), adminUserId, roleId);
    }

    /**
     * The bulk route must apply the same ceiling as the single-target one. Without this a
     * delegated administrator could switch off a role holding permissions they do not hold, just
     * by choosing the bulk endpoint.
     */
    @Test
    void bulkRoleActivationObeysTheActorPermissionCeiling() throws Exception {
        String superToken = loginAdminAndGetToken();
        LimitedAdmin limited = createLimitedAdmin("platform.roles.bulk_set_active");
        UUID beyondCeiling = createAdminRole(superToken, "Beyond " + UUID.randomUUID());
        grantPermission(superToken, beyondCeiling, "platform.registry.read")
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/roles/bulk/active")
                        .header("Authorization", bearer(limited.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.hiveapp.platform.admin.dto.BulkSetActiveRequest(
                                        java.util.List.of(beyondCeiling), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.failures[0].code").value("INVALID_PERMISSION_GRANT"));

        // The role is untouched.
        mockMvc.perform(get("/api/admin/roles/{id}", beyondCeiling)
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    /** Duplicates are collapsed before anything runs, so counts describe targets, not attempts. */
    @Test
    void bulkOperationsCountUniqueTargets() throws Exception {
        String superToken = loginAdminAndGetToken();
        UUID roleId = createAdminRole(superToken, "Dupes " + UUID.randomUUID());

        mockMvc.perform(post("/api/admin/roles/bulk/active")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.hiveapp.platform.admin.dto.BulkSetActiveRequest(
                                        java.util.List.of(roleId, roleId, roleId), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(1))
                .andExpect(jsonPath("$.succeeded").value(1));
    }

    @Test
    void remainingOperatorBulkRoutesApplyEveryTargetAndReportDuplicates() throws Exception {
        String token = loginAdminAndGetToken();
        UUID first = createdOperatorId(createAdminUser(token, operatorEmail(), false)
                .andExpect(status().isCreated()));
        UUID second = createdOperatorId(createAdminUser(token, operatorEmail(), false)
                .andExpect(status().isCreated()));
        UUID roleId = createAdminRole(token, "Bulk target " + UUID.randomUUID());
        var ids = java.util.List.of(first, second);

        mockMvc.perform(post("/api/admin/users/bulk/access/resend")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.hiveapp.platform.admin.dto.BulkOperatorIdsRequest(ids))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.succeeded").value(2))
                .andExpect(jsonPath("$.failures").isEmpty());

        var roleRequest = new com.hiveapp.platform.admin.dto.BulkAssignRoleRequest(ids, roleId);
        mockMvc.perform(post("/api/admin/users/bulk/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(2));
        mockMvc.perform(post("/api/admin/users/bulk/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.failures.length()").value(2))
                .andExpect(jsonPath("$.failures[*].code", everyItem(
                        org.hamcrest.Matchers.is("RESOURCE_ALREADY_EXISTS"))));

        mockMvc.perform(post("/api/admin/users/bulk/active")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.hiveapp.platform.admin.dto.BulkSetActiveRequest(ids, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(2));
        mockMvc.perform(get("/api/admin/users/{id}", first)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        mockMvc.perform(get("/api/admin/users/{id}", second)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    private void assertSegmentMutationPermission(
            String method,
            UUID segmentId,
            String body,
            LimitedAdmin denied,
            LimitedAdmin allowed,
            String path
    ) throws Exception {
        var deniedRequest = switch (method) {
            case "put" -> put(path, segmentId);
            case "post" -> post(path, segmentId);
            case "delete" -> delete(path, segmentId);
            default -> throw new IllegalArgumentException("Unsupported test method " + method);
        };
        mockMvc.perform(deniedRequest.header("Authorization", bearer(denied.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        var allowedRequest = switch (method) {
            case "put" -> put(path, segmentId);
            case "post" -> post(path, segmentId);
            case "delete" -> delete(path, segmentId);
            default -> throw new IllegalArgumentException("Unsupported test method " + method);
        };
        mockMvc.perform(allowedRequest.header("Authorization", bearer(allowed.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    private ClientIdentity registerClient() throws Exception {
        String email = "admin-candidate-" + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest(email, CLIENT_PASSWORD, "Admin", "Candidate", null);
        String token = accessToken(mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))));
        UUID userId = UUID.fromString(listMembers(token).get(0).get("userId").asText());
        return new ClientIdentity(email, userId);
    }

    private static String operatorEmail() {
        return "op-" + UUID.randomUUID() + "@hiveapp.test";
    }

    private String loginAdmin(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        return accessToken(mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))));
    }

    private UUID createAdminRole(String token, String name) throws Exception {
        CreateAdminRoleRequest request = new CreateAdminRoleRequest(name, "Security test role");
        UUID roleId = responseId(mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
        // Product roles deliberately start inactive; most security cases below need an assignable
        // fixture, so the helper activates it explicitly instead of weakening the creation rule.
        toggleRole(token, roleId).andExpect(status().isNoContent());
        return roleId;
    }

    private ResultActions createAdminUser(String token, String email, boolean superAdmin) throws Exception {
        // Operators are created outright, never promoted from the client user pool.
        CreateAdminUserRequest request = new CreateAdminUserRequest(
                "Op", "Erator", email, InitialAccessMethod.EMAIL_LINK, superAdmin);
        return mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions grantPermission(String token, UUID roleId, String permissionCode) throws Exception {
        UUID permissionId = permissionRepository.findByCode(permissionCode).orElseThrow().getId();
        GrantAdminPermissionRequest request = new GrantAdminPermissionRequest(permissionId);
        return mockMvc.perform(post("/api/admin/roles/{id}/permissions", roleId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions assignRole(String token, UUID adminUserId, UUID roleId) throws Exception {
        AssignAdminRoleRequest request = new AssignAdminRoleRequest(roleId);
        return mockMvc.perform(post("/api/admin/users/{id}/roles", adminUserId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions removeRole(String token, UUID adminUserId, UUID roleId) throws Exception {
        return mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleId}", adminUserId, roleId)
                .header("Authorization", bearer(token)));
    }

    private ResultActions revokePermission(String token, UUID roleId, UUID permissionId) throws Exception {
        return mockMvc.perform(delete("/api/admin/roles/{id}/permissions/{permissionId}", roleId, permissionId)
                .header("Authorization", bearer(token)));
    }

    private ResultActions toggleRole(String token, UUID roleId) throws Exception {
        return mockMvc.perform(post("/api/admin/roles/{id}/toggle-active", roleId)
                .header("Authorization", bearer(token)));
    }

    private ResultActions toggleAdmin(String token, UUID adminUserId) throws Exception {
        return mockMvc.perform(post("/api/admin/users/{id}/toggle-active", adminUserId)
                .header("Authorization", bearer(token)));
    }

    private UUID currentAdminId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private ResultActions updateFeatureControl(
            String token, UUID featureId, String control, boolean enabled) throws Exception {
        return mockMvc.perform(patch("/api/admin/registry/features/{id}/{control}", featureId, control)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + ",\"reason\":\"integration test\"}"));
    }

    private ResultActions updateEmergencyRuntime(String token, UUID featureId, boolean enabled) throws Exception {
        return mockMvc.perform(patch(
                        "/api/admin/registry/features/{id}/emergency-runtime", featureId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled
                        + ",\"reason\":\"integration incident test\""
                        + ",\"impactConfirmed\":true,\"communicationConfirmed\":true}"));
    }

    /** Creation returns the operator nested alongside their one-time temporary password. */
    private UUID createdOperatorId(ResultActions action) throws Exception {
        JsonNode response = objectMapper.readTree(action.andReturn().getResponse().getContentAsString());
        return UUID.fromString(response.get("operator").get("id").asText());
    }

    /** The temporary password is returned once, by the action that creates it, and never again. */
    private String issueTemporaryAccess(String token, UUID adminUserId) throws Exception {
        ResultActions action = mockMvc.perform(post("/api/admin/users/{id}/access/temporary", adminUserId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        JsonNode response = objectMapper.readTree(action.andReturn().getResponse().getContentAsString());
        return response.get("temporaryPassword").asText();
    }

    /**
     * A temporary password buys only a restricted session whose one purpose is choosing a real
     * password, so a test that needs a working operator completes that change first.
     */
    private String signInWithTemporaryPassword(String email, String temporaryPassword) throws Exception {
        String restricted = loginAdmin(email, temporaryPassword);
        return accessToken(mockMvc.perform(post("/api/admin/auth/initial-password/change")
                        .header("Authorization", bearer(restricted))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.hiveapp.identity.dto.InitialPasswordChangeRequest(
                                        "chosen-password-" + UUID.randomUUID()))))
                .andExpect(status().isOk()));
    }

    private UUID responseId(ResultActions action) throws Exception {
        JsonNode response = objectMapper.readTree(action.andReturn().getResponse().getContentAsString());
        return UUID.fromString(response.get("id").asText());
    }

    private JsonNode responseJson(ResultActions action) throws Exception {
        return objectMapper.readTree(action.andReturn().getResponse().getContentAsString());
    }

    private String accessToken(ResultActions action) throws Exception {
        JsonNode response = objectMapper.readTree(action.andReturn().getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private record ClientIdentity(String email, UUID userId) {
    }

    private record LimitedAdmin(String token, UUID adminUserId, UUID roleId) {
    }
}
