package com.hiveapp.shared.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.role.dto.CreateRoleRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
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
            mockMvc.perform(post("/api/v1/roles/{id}/permissions", roleId)
                            .header("Authorization", bearer(token))
                            .param("permissionCode", permissionCode)
                            .param("registryVersion", registryCatalogVersionService.currentVersion()))
                    .andExpect(status().isOk());
        }
    }

    private JsonNode listPlanFeatures(String adminToken, UUID planId) throws Exception {
        String response = mockMvc.perform(get("/api/admin/plans/{planId}/features", planId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createDraftPlan(String adminToken) throws Exception {
        CreatePlanRequest request = new CreatePlanRequest(
                "QC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(),
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
