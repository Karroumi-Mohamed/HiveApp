package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.service.PlanPublicSelection;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.quota.*;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.transaction.annotation.Transactional
class PlanVersionManagementIntegrationTest extends PlatformShellIntegrationTestSupport {
    @Autowired PlanRepository plans;
    @Autowired ProductPriceRepository prices;
    @Autowired PlanFeatureRepository features;
    @Autowired FeatureRepository registry;
    @Autowired PlanPublicSelection publicSelection;

    @Test
    void publishingSuccessorKeepsV1PublicUntilExplicitSelectionAndStaleSelectionIsRejected() throws Exception {
        String token = loginAdminAndGetToken();
        Plan first = plan(null, 1, PlanStatus.ACTIVE);
        Plan second = plan(first, 2, PlanStatus.ACTIVE);
        assertThat(publicSelection.isPublicChoice(first)).isTrue();
        assertThat(publicSelection.isPublicChoice(second)).isFalse();
        var versions = versions(token, first);
        assertThat(versions.path("publicPlanId").asText()).isEqualTo(first.getId().toString());
        assertThat(versions.path("versions").path("totalElements").asInt()).isEqualTo(2);
        var request = Map.of("expectedCatalogRevision", versions.path("catalogRevision").asLong(),
                "expectedVersion", second.getVersion(), "reason", "Sell the new version");
        mockMvc.perform(post("/api/admin/plans/{id}/public-version", second.getId())
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productVersionNumber").value(2));
        assertThat(publicSelection.isPublicChoice(first)).isFalse();
        assertThat(publicSelection.isPublicChoice(second)).isTrue();
        assertThat(plans.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(PlanStatus.ACTIVE);
        mockMvc.perform(post("/api/admin/plans/{id}/public-version", first.getId())
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void publicVersionMustBePublishedPublicAndHaveACurrentPrice() throws Exception {
        String token = loginAdminAndGetToken();
        Plan first = plan(null, 1, PlanStatus.ACTIVE);
        Plan second = plan(first, 2, PlanStatus.DRAFT);
        select(token, second).andExpect(status().isConflict());
        second.setStatus(PlanStatus.ACTIVE);
        second.setSalesVisibility(ProductSalesVisibility.DIRECT_ONLY);
        second = plans.saveAndFlush(second);
        select(token, second).andExpect(status().isConflict());
        second.setSalesVisibility(ProductSalesVisibility.PUBLIC);
        second = plans.saveAndFlush(second);
        prices.deleteAll(prices.findAllByPlanId(second.getId()));
        select(token, second).andExpect(status().isConflict());
        assertThat(publicSelection.isPublicChoice(first)).isTrue();
    }

    @Test
    void ordinaryClientCatalogueOnlyShowsSelectedVersionAndKeepsSelectionAfterSalesArePaused() throws Exception {
        String admin = loginAdminAndGetToken();
        String client = registerClientAndGetToken();
        Plan first = plan(null, 1, PlanStatus.ACTIVE);
        Plan second = plan(first, 2, PlanStatus.ACTIVE);
        String cataloguePath = "/api/v1/subscriptions/catalog";
        var before = mockMvc.perform(get(cataloguePath).header("Authorization", bearer(client)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(catalogueCodes(before)).contains(first.getCode()).doesNotContain(second.getCode());
        select(admin, second).andExpect(status().isOk());
        var after = mockMvc.perform(get(cataloguePath).header("Authorization", bearer(client)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(catalogueCodes(after)).contains(second.getCode()).doesNotContain(first.getCode());
        second = plans.findById(second.getId()).orElseThrow();
        lifecycle(admin, second, "DEACTIVATE").andExpect(status().isOk());
        assertThat(publicSelection.isPublicChoice(first)).isFalse();
        assertThat(publicSelection.isPublicChoice(second)).isTrue();
        var paused = mockMvc.perform(get(cataloguePath).header("Authorization", bearer(client)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(catalogueCodes(paused)).doesNotContain(first.getCode(), second.getCode());
    }

    @Test
    void familyListReturnsOneCardWithDraftAndCountsAndIndependentDuplicateFamily() throws Exception {
        String token = loginAdminAndGetToken();
        Plan first = plan(null, 1, PlanStatus.ACTIVE);
        Plan draft = plan(first, 2, PlanStatus.DRAFT);
        String response = mockMvc.perform(get("/api/admin/plans/families")
                        .param("search", first.getName()).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var result = objectMapper.readTree(response).path("content");
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).path("versionCount").asInt()).isEqualTo(2);
        assertThat(result.get(0).path("draft").path("id").asText()).isEqualTo(draft.getId().toString());
        assertThat(result.get(0).path("currentSubscriberCount").asLong()).isZero();
        assertThat(result.get(0).path("currentPrices").size()).isEqualTo(1);
    }

    @Test
    void comparisonIgnoresMappingIdentityAndQuotaOrderButFindsRealChanges() throws Exception {
        String token = loginAdminAndGetToken();
        Plan first = plan(null, 1, PlanStatus.ACTIVE);
        Plan second = plan(first, 2, PlanStatus.DRAFT);
        feature(first, "platform.workspace", PlanFeatureMode.INCLUDED, List.of());
        feature(second, "platform.workspace", PlanFeatureMode.INCLUDED, List.of());
        feature(first, "platform.staff", PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitEntry("members", QuotaLimitMode.FINITE, 5L)));
        feature(second, "platform.staff", PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitEntry("members", QuotaLimitMode.FINITE, 10L)));
        var comparison = mockMvc.perform(get("/api/admin/plans/{source}/compare/{target}", first.getId(), second.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var rows = objectMapper.readTree(comparison).path("features");
        assertThat(rows.get(0).path("featureCode").asText()).isEqualTo("platform.staff");
        assertThat(rows.get(0).path("changed").asBoolean()).isTrue();
        assertThat(rows.get(1).path("changed").asBoolean()).isFalse();
        Plan unrelated = plan(null, 1, PlanStatus.ACTIVE);
        mockMvc.perform(get("/api/admin/plans/{source}/compare/{target}", first.getId(), unrelated.getId())
                        .header("Authorization", bearer(token))).andExpect(status().isBadRequest());
    }

    @Test
    void metadataEditCannotChangePublishedCompositionMoneyOrBusinessVersion() throws Exception {
        String token = loginAdminAndGetToken();
        Plan plan = plan(null, 1, PlanStatus.ACTIVE);
        long priorVersion = plan.getVersion();
        mockMvc.perform(patch("/api/admin/plans/{id}/metadata", plan.getId())
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedVersion", plan.getVersion(),
                                "name", "Readable name", "description", "Updated description",
                                "reason", "Correct catalogue wording", "price", "1"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Readable name"))
                .andExpect(jsonPath("$.productVersionNumber").value(1));
        Plan updated = plans.findById(plan.getId()).orElseThrow();
        assertThat(updated.getPrice()).isEqualByComparingTo("100");
        assertThat(updated.getVersion()).isGreaterThan(priorVersion);
        mockMvc.perform(patch("/api/admin/plans/{id}/metadata", plan.getId())
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedVersion", priorVersion,
                                "name", "Stale edit", "reason", "stale"))))
                .andExpect(status().isConflict());
    }

    @Test
    void activeCannotBeArchivedDirectlyAndArchivedMetadataIsImmutable() throws Exception {
        String token = loginAdminAndGetToken();
        Plan plan = plan(null, 1, PlanStatus.ACTIVE);
        lifecycle(token, plan, "ARCHIVE").andExpect(status().isConflict());
        lifecycle(token, plan, "DEACTIVATE").andExpect(status().isOk());
        plan = plans.findById(plan.getId()).orElseThrow();
        lifecycle(token, plan, "ARCHIVE").andExpect(status().isOk());
        plan = plans.findById(plan.getId()).orElseThrow();
        mockMvc.perform(patch("/api/admin/plans/{id}/metadata", plan.getId())
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedVersion", plan.getVersion(),
                                "name", "Cannot edit", "reason", "archived"))))
                .andExpect(status().isConflict());
    }

    @Test
    void clientAndUnauthenticatedActorCannotReadOrMutateAdminVersionSurfaces() throws Exception {
        Plan plan = plan(null, 1, PlanStatus.ACTIVE);
        mockMvc.perform(get("/api/admin/plans/{id}/versions", plan.getId())).andExpect(status().isUnauthorized());
        String client = registerClientAndGetToken();
        mockMvc.perform(get("/api/admin/plans/{id}/versions", plan.getId())
                        .header("Authorization", bearer(client))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/plans/{id}/public-version", plan.getId())
                        .header("Authorization", bearer(client)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"expectedCatalogRevision\":0,\"reason\":\"not admin\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void versionsCreationAliasRetainsLegacyGuardAndSingleDraftInvariant() throws Exception {
        String token = loginAdminAndGetToken();
        Plan first = plan(null, 1, PlanStatus.ACTIVE);
        String body = objectMapper.writeValueAsString(Map.of("name", first.getName(), "price", "100",
                "currencyCode", "MAD", "billingCycle", "MONTHLY"));
        mockMvc.perform(post("/api/admin/plans/{id}/versions", first.getId())
                        .param("expectedVersion", Long.toString(first.getVersion()))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.productVersionNumber").value(2))
                .andExpect(jsonPath("$.lineageId").value(first.getLineageId().toString()));
        mockMvc.perform(post("/api/admin/plans/{id}/revisions", first.getId())
                        .param("expectedVersion", Long.toString(first.getVersion()))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        assertThat(publicSelection.isPublicChoice(first)).isTrue();
    }

    @Test
    void catalogueComparisonSupportsThreeIndependentPlansAndSchedulesWithoutMutation() throws Exception {
        String token = loginAdminAndGetToken();
        Plan first = plan(null, 1, PlanStatus.ACTIVE);
        Plan second = plan(null, 1, PlanStatus.DRAFT);
        Plan third = plan(null, 1, PlanStatus.INACTIVE);
        feature(first, "platform.staff", PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitEntry("members", QuotaLimitMode.FINITE, 0L)));
        feature(second, "platform.staff", PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitEntry("members", QuotaLimitMode.UNLIMITED, null)));
        feature(third, "platform.staff", PlanFeatureMode.OPTIONAL_ADD_ON, List.of());
        var future = ProductPrice.draft(first, Money.of(new BigDecimal("900"), "MAD"),
                BillingCycle.YEARLY, Instant.now().plusSeconds(86400), null);
        future.activate();
        prices.saveAndFlush(future);
        long count = plans.count();
        long version = first.getVersion();
        mockMvc.perform(get("/api/admin/plans/comparison").header("Authorization", bearer(token))
                        .param("ids", third.getId().toString(), first.getId().toString(), second.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plans.length()").value(3))
                .andExpect(jsonPath("$.plans[0].plan.id").value(third.getId().toString()))
                .andExpect(jsonPath("$.plans[0].features[0].mode").value("OPTIONAL_ADD_ON"))
                .andExpect(jsonPath("$.plans[1].features[0].quotaConfigs[0].limit").value(0))
                .andExpect(jsonPath("$.plans[1].currentPrices.length()").value(1))
                .andExpect(jsonPath("$.plans[1].scheduledPrices[0].billingCycle").value("YEARLY"))
                .andExpect(jsonPath("$.plans[2].features[0].quotaConfigs[0].mode").value("UNLIMITED"))
                .andExpect(jsonPath("$.pricesVisible").value(true));
        assertThat(plans.count()).isEqualTo(count);
        assertThat(plans.findById(first.getId()).orElseThrow().getVersion()).isEqualTo(version);
    }

    @Test
    void catalogueComparisonRejectsInvalidSelectionsAndSupportsTwoPlans() throws Exception {
        String token = loginAdminAndGetToken();
        String a = plan(null, 1, PlanStatus.ACTIVE).getId().toString();
        String b = plan(null, 1, PlanStatus.ACTIVE).getId().toString();
        for (String[] ids : List.of(new String[]{a}, new String[]{a, a},
                new String[]{a, b, UUID.randomUUID().toString(), UUID.randomUUID().toString()},
                new String[]{a, "invalid"})) {
            mockMvc.perform(get("/api/admin/plans/comparison").header("Authorization", bearer(token))
                    .param("ids", ids)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/admin/plans/comparison").header("Authorization", bearer(token))
                .param("ids", a, b)).andExpect(status().isOk()).andExpect(jsonPath("$.plans.length()").value(2));
        mockMvc.perform(get("/api/admin/plans/comparison").header("Authorization", bearer(token))
                .param("ids", a, UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/plans/comparison").param("ids", a, b))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/plans/comparison").param("ids", a, b)
                .header("Authorization", bearer(registerClientAndGetToken()))).andExpect(status().isForbidden());
    }

    @Test
    void featureCompatibilityFilterRunsBeforePaginationAndIncludesCapacityPacks() throws Exception {
        String token = loginAdminAndGetToken();
        Plan flex = plans.findByCode("FLEX").orElseThrow();
        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                        .header("Authorization", bearer(token)).param("featureCode", "platform.staff")
                        .param("type", "QUOTA_PACKAGE").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].quotaFeatureCode").value("platform.staff"));
        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                        .header("Authorization", bearer(token)).param("featureCode", "platform.rbac")
                        .param("type", "ADD_ON").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name").value("Custom Roles"));
        mockMvc.perform(get("/api/admin/plans/{id}/extensions/compatibility", flex.getId())
                        .header("Authorization", bearer(token)).param("featureCode", "unknown"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions lifecycle(String token, Plan plan, String action) throws Exception {
        return mockMvc.perform(post("/api/admin/plans/{id}/lifecycle", plan.getId())
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("expectedVersion", plan.getVersion(),
                        "action", action, "reason", "Testing lifecycle prerequisites"))));
    }
    private org.springframework.test.web.servlet.ResultActions select(String token, Plan plan) throws Exception {
        return mockMvc.perform(post("/api/admin/plans/{id}/public-version", plan.getId())
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("expectedVersion", plan.getVersion(),
                        "expectedCatalogRevision", versions(token, plan).path("catalogRevision").asLong(),
                        "reason", "Choose public version"))));
    }
    private JsonNode versions(String token, Plan plan) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/admin/plans/{id}/versions", plan.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private List<String> catalogueCodes(String response) throws Exception {
        List<String> codes = new ArrayList<>();
        objectMapper.readTree(response).path("plans").forEach(plan -> codes.add(plan.path("code").asText()));
        return codes;
    }
    private Plan plan(Plan source, int number, PlanStatus status) {
        Plan plan = new Plan();
        String name = "VERSION_TEST_" + UUID.randomUUID().toString().replace("-", "");
        plan.setCode(name);
        plan.setName(source == null ? name : source.getName());
        plan.setMoney(Money.of(new BigDecimal("100"), "MAD"));
        plan.setBillingCycle(BillingCycle.MONTHLY);
        plan.setStatus(status);
        plan.setRevisionNumber(number);
        if (source != null) { plan.setLineageId(source.getLineageId()); plan.setSourcePlan(source); }
        plan = plans.saveAndFlush(plan);
        var price = ProductPrice.draft(plan, plan.money(), BillingCycle.MONTHLY, Instant.EPOCH, null);
        price.activate();
        prices.saveAndFlush(price);
        return plan;
    }
    private void feature(Plan plan, String code, PlanFeatureMode mode, List<QuotaLimitEntry> limits) {
        var row = new PlanFeature();
        row.setPlan(plan);
        row.setFeature(registry.findByCode(code).orElseThrow());
        row.setMode(mode);
        row.setQuotaConfigs(limits);
        features.saveAndFlush(row);
    }
}
