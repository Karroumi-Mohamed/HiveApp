package com.hiveapp.shared.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope;
import com.hiveapp.platform.client.member.dto.AssignRoleRequest;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.CreateProductPriceRequest;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.role.dto.CreateRoleRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks the `MAPPER-001` contract: list read models must resolve their relationships with
 * a bounded number of statements, so adding rows cannot multiply database round trips.
 *
 * <p>The assertions deliberately compare a small population against a larger one instead of
 * pinning an absolute count. Authentication, tenant resolution, and authorization each issue
 * their own statements, and those are legitimate; the defect being guarded against is growth
 * proportional to the number of returned rows.</p>
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class LazyMappingQueryCountIntegrationTest extends PlatformShellIntegrationTestSupport {

    private static final List<String> PERMISSION_CODES = List.of(
            "platform.company.read_single",
            "platform.company.create",
            "platform.company.delete");

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Test
    void roleListStatementCountDoesNotGrowWithRolesOrTheirPermissions() throws Exception {
        String token = registerClientAndGetToken();
        createRoleWithEveryPermission(token, "Baseline Manager");

        long oneRole = statementsFor(() -> listRoles(token));

        createRoleWithEveryPermission(token, "Second Manager");
        createRoleWithEveryPermission(token, "Third Manager");
        createRoleWithEveryPermission(token, "Fourth Manager");

        long fourRoles = statementsFor(() -> listRoles(token));

        assertThat(listRoles(token).size()).isEqualTo(4);
        assertThat(fourRoles)
                .as("role list must not issue extra statements per role or per role permission")
                .isEqualTo(oneRole);
    }

    /**
     * The seeded FREE provisioning plan allows three members and the owner consumes one seat,
     * so this compares the owner alone against the full three-member workspace.
     */
    @Test
    void memberListStatementCountDoesNotGrowWithMembers() throws Exception {
        String token = registerClientAndGetToken();

        long oneMember = statementsFor(() -> listMembers(token));

        createOrdinaryMember(token);
        createOrdinaryMember(token);

        long threeMembers = statementsFor(() -> listMembers(token));

        assertThat(listMembers(token).size()).isEqualTo(3);
        assertThat(threeMembers)
                .as("member list must not issue an extra user statement per member")
                .isEqualTo(oneMember);
    }

    @Test
    void planFeatureListStatementCountDoesNotGrowWithFeatures() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createDraftPlan(adminToken);
        assignFeature(adminToken, planId, "platform.company");

        long oneFeature = statementsFor(() -> listPlanFeatures(adminToken, planId));

        assignFeature(adminToken, planId, "platform.staff");
        assignFeature(adminToken, planId, "platform.b2b");
        assignFeature(adminToken, planId, "platform.organization");

        long fourFeatures = statementsFor(() -> listPlanFeatures(adminToken, planId));

        assertThat(listPlanFeatures(adminToken, planId).size()).isEqualTo(4);
        assertThat(fourFeatures)
                .as("plan feature list must not issue an extra feature statement per plan feature")
                .isEqualTo(oneFeature);
    }

    @Test
    void productPricePageStatementCountDoesNotGrowWithRows() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = planRepository.findByCode("PRO").orElseThrow().getId();
        createDraftPrice(adminToken, planId, new BigDecimal("100.00"));

        long baseline = statementsFor(() -> listProductPrices(adminToken, planId));
        int baselineSize = listProductPrices(adminToken, planId).size();

        createDraftPrice(adminToken, planId, new BigDecimal("101.00"));
        createDraftPrice(adminToken, planId, new BigDecimal("102.00"));
        createDraftPrice(adminToken, planId, new BigDecimal("103.00"));

        long expanded = statementsFor(() -> listProductPrices(adminToken, planId));

        assertThat(listProductPrices(adminToken, planId).size()).isEqualTo(baselineSize + 3);
        assertThat(expanded)
                .as("price pages bulk-load owner and overlap facts with a constant statement count")
                .isEqualTo(baseline);
    }

    @Test
    void productPricePageDoesNotMaterializeUnrelatedActivePriceBooks() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID requestedPlanId = planRepository.findByCode("PRO").orElseThrow().getId();
        var unrelatedPlan = planRepository.findByCode("FREE").orElseThrow();

        long baseline = entityLoadsFor(() -> listProductPrices(adminToken, requestedPlanId));
        List<ProductPrice> unrelatedPrices = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> {
                    ProductPrice price = ProductPrice.draft(
                            unrelatedPlan, Money.of(BigDecimal.valueOf(index), "EUR"),
                            BillingCycle.YEARLY, Instant.EPOCH, null);
                    price.activate();
                    return price;
                })
                .toList();
        productPriceRepository.saveAllAndFlush(unrelatedPrices);
        try {
            long expanded = entityLoadsFor(() -> listProductPrices(adminToken, requestedPlanId));
            assertThat(expanded)
                    .as("a price page must load overlap facts only for tuples represented on that page")
                    .isEqualTo(baseline);
        } finally {
            productPriceRepository.deleteAll(unrelatedPrices);
            productPriceRepository.flush();
        }
    }

    @Test
    void assignablePlanPricePageStatementCountDoesNotGrowWithRows() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var plan = planRepository.findByCode("PRO").orElseThrow();
        long baseline = statementsFor(() -> listAssignablePlanPrices(adminToken));

        List<ProductPrice> additionalPrices = List.of("GBP", "CAD", "AUD").stream()
                .map(currency -> {
                    ProductPrice price = ProductPrice.draft(
                            plan, Money.of(new BigDecimal("99.00"), currency),
                            BillingCycle.YEARLY, Instant.EPOCH, null);
                    price.activate();
                    return price;
                })
                .toList();
        productPriceRepository.saveAllAndFlush(additionalPrices);
        try {
            long expanded = statementsFor(() -> listAssignablePlanPrices(adminToken));
            assertThat(expanded)
                    .as("the subscription price chooser must not query each Plan revision separately")
                    .isEqualTo(baseline);
        } finally {
            productPriceRepository.deleteAll(additionalPrices);
            productPriceRepository.flush();
        }
    }

    @Test
    void subscriptionAccountOperationalPageStatementCountDoesNotGrowWithRows() throws Exception {
        String adminToken = loginAdminAndGetToken();
        registerClientAndGetToken();
        registerClientAndGetToken();
        registerClientAndGetToken();
        registerClientAndGetToken();

        long oneRow = statementsFor(() -> operationalPage(
                adminToken, "/api/admin/subscriptions/accounts/search", 1));
        long fullPage = statementsFor(() -> operationalPage(
                adminToken, "/api/admin/subscriptions/accounts/search", 100));

        assertThat(operationalPage(
                adminToken, "/api/admin/subscriptions/accounts/search", 100).size())
                .isGreaterThan(1);
        assertThat(Math.abs(fullPage - oneRow))
                .as("the account table must bulk-load latest subscriptions and plans")
                .isLessThanOrEqualTo(1L);
    }

    @Test
    void commercialProductOperationalPagesUseConstantStatementCounts() throws Exception {
        String adminToken = loginAdminAndGetToken();
        assertConstantOperationalPage(adminToken, "/api/admin/plans");
        assertConstantOperationalPage(adminToken, "/api/admin/add-ons");
        assertConstantOperationalPage(adminToken, "/api/admin/quota-packages");
    }

    /**
     * Role DETAIL, not just the list. This surface is what a role screen opens, and it was
     * left relying on open-in-view when the list paths were fixed.
     */
    @Test
    void roleDetailStatementCountDoesNotGrowWithThatRolesPermissions() throws Exception {
        String token = registerClientAndGetToken();
        UUID sparse = createRole(token, "Sparse Manager");
        addPermission(token, sparse, PERMISSION_CODES.get(0));
        UUID dense = createRole(token, "Dense Manager");
        for (String code : PERMISSION_CODES) {
            addPermission(token, dense, code);
        }

        long onePermission = statementsFor(() -> readRole(token, sparse));
        long threePermissions = statementsFor(() -> readRole(token, dense));

        assertThat(threePermissions)
                .as("role detail must not issue an extra statement per permission on the role")
                .isEqualTo(onePermission);
    }

    /**
     * Role impact preview walks every assignment's member. Adding assignments must not add
     * statements. The owner is a protected target for role assignment and the FREE plan allows
     * three members, so this compares one ordinary member against two.
     */
    @Test
    void roleImpactStatementCountDoesNotGrowWithAssignments() throws Exception {
        String token = registerClientAndGetToken();
        UUID roleId = createRole(token, "Impact Manager");
        addPermission(token, roleId, PERMISSION_CODES.get(0));
        activateRole(token, roleId);   // roles are created INACTIVE per ROLE-FLOW-001
        assignRoleToMember(token, createOrdinaryMember(token), roleId);

        long oneAssignment = statementsFor(() -> readImpact(token, roleId));

        assignRoleToMember(token, createOrdinaryMember(token), roleId);

        long twoAssignments = statementsFor(() -> readImpact(token, roleId));

        assertThat(twoAssignments)
                .as("role impact preview must not issue an extra member statement per assignment")
                .isEqualTo(oneAssignment);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private interface RequestBlock {
        void run() throws Exception;
    }

    /**
     * Statistics are owned by the SessionFactory, so clearing them is a global operation.
     * That is safe under sequential execution but would make these assertions flaky if
     * parallel test execution is ever enabled.
     */
    private long statementsFor(RequestBlock block) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        block.run();
        return statistics.getPrepareStatementCount();
    }

    private long entityLoadsFor(RequestBlock block) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        block.run();
        return statistics.getEntityLoadCount();
    }

    private JsonNode listRoles(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void createRoleWithEveryPermission(String token, String name) throws Exception {
        UUID roleId = createRole(token, name);
        for (String permissionCode : PERMISSION_CODES) {
            addPermission(token, roleId, permissionCode);
        }
    }

    private void addPermission(String token, UUID roleId, String permissionCode) throws Exception {
        mockMvc.perform(post("/api/v1/roles/{id}/permissions", roleId)
                        .header("Authorization", bearer(token))
                        .param("permissionCode", permissionCode)
                        .param("registryVersion", registryCatalogVersionService.currentVersion()))
                .andExpect(status().isOk());
    }

    private void activateRole(String token, UUID roleId) throws Exception {
        mockMvc.perform(post("/api/v1/roles/{id}/activate", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private void assignRoleToMember(String token, UUID memberId, UUID roleId) throws Exception {
        mockMvc.perform(post("/api/v1/members/{id}/roles", memberId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignRoleRequest(roleId, RoleAssignmentScope.ACCOUNT, null))))
                .andExpect(status().isNoContent());
    }

    private JsonNode readImpact(String token, UUID roleId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/roles/{id}/impact", roleId)
                        .header("Authorization", bearer(token))
                        .param("changeType", "DEACTIVATE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode readRole(String token, UUID roleId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/roles/{id}", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode listPlanFeatures(String adminToken, UUID planId) throws Exception {
        String response = mockMvc.perform(get("/api/admin/plans/{planId}/features", planId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode listProductPrices(String adminToken, UUID planId) throws Exception {
        String response = mockMvc.perform(get("/api/admin/product-prices")
                        .header("Authorization", bearer(adminToken))
                        .param("ownerType", "PLAN")
                        .param("ownerId", planId.toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("content");
    }

    private JsonNode listAssignablePlanPrices(String adminToken) throws Exception {
        String response = mockMvc.perform(get("/api/admin/subscriptions/assignable-plan-prices")
                        .header("Authorization", bearer(adminToken))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("content");
    }

    private void assertConstantOperationalPage(String token, String path) throws Exception {
        long oneRow = statementsFor(() -> operationalPage(token, path, 1));
        long fullPage = statementsFor(() -> operationalPage(token, path, 100));
        assertThat(operationalPage(token, path, 100).size()).isGreaterThan(1);
        // Spring Data may omit the count query when the content proves this is the last page.
        assertThat(Math.abs(fullPage - oneRow))
                .as("%s must aggregate page facts without per-row statements", path)
                .isLessThanOrEqualTo(1L);
    }

    private JsonNode operationalPage(String token, String path, int size) throws Exception {
        String response = mockMvc.perform(get(path)
                        .header("Authorization", bearer(token))
                        .param("page", "0")
                        .param("size", Integer.toString(size)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("content");
    }

    private void createDraftPrice(String adminToken, UUID planId, BigDecimal amount) throws Exception {
        mockMvc.perform(post("/api/admin/product-prices")
                        .header("Authorization", bearer(adminToken))
                        .param("ownerType", "PLAN")
                        .param("ownerId", planId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductPriceRequest(
                                amount, "USD", BillingCycle.YEARLY, Instant.now(), null))))
                .andExpect(status().isCreated());
    }

    private UUID createDraftPlan(String adminToken) throws Exception {
        CreatePlanRequest request = new CreatePlanRequest(
                "Query Count Plan",
                "Fixture for statement-count assertions",
                new BigDecimal("10.00"),
                "USD",
                BillingCycle.MONTHLY);
        String response = mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void assignFeature(String adminToken, UUID planId, String featureCode) throws Exception {
        AssignPlanFeatureRequest request =
                new AssignPlanFeatureRequest(featureCode, PlanFeatureMode.INCLUDED, List.of());
        mockMvc.perform(post("/api/admin/plans/{planId}/features", planId)
                        .param("expectedVersion", String.valueOf(
                                planRepository.findById(planId).orElseThrow().getVersion()))
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private UUID createRole(String token, String name) throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(null, name, name + " description");
        String response = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
