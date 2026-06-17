package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.admin.dto.AssignAdminRoleRequest;
import com.hiveapp.platform.admin.dto.CreateAdminRoleRequest;
import com.hiveapp.platform.admin.dto.CreateAdminUserRequest;
import com.hiveapp.platform.admin.dto.GrantAdminPermissionRequest;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControlPlaneSecurityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private PlanRepository planRepository;

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
        ClientIdentity candidate = registerClient();

        createAdminUser(creator.token(), candidate.userId(), true)
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
    void publishingAControlPlaneFeatureDoesNotMakeItAssignableToClientPlans() throws Exception {
        String superToken = loginAdminAndGetToken();
        UUID featureId = featureRepository.findByCode("platform.plans").orElseThrow().getId();
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();

        updateFeatureStatus(superToken, featureId, FeatureStatus.PUBLIC)
                .andExpect(status().isNoContent());
        try {
            AssignPlanFeatureRequest request = new AssignPlanFeatureRequest("platform.plans", null, List.of());
            mockMvc.perform(post("/api/admin/plans/{planId}/features", freePlanId)
                            .header("Authorization", bearer(superToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Feature platform.plans cannot be assigned to billing configuration."));
        } finally {
            updateFeatureStatus(superToken, featureId, FeatureStatus.INTERNAL)
                    .andExpect(status().isNoContent());
        }
    }

    private LimitedAdmin createLimitedAdmin(String... permissionCodes) throws Exception {
        String superToken = loginAdminAndGetToken();
        ClientIdentity identity = registerClient();
        UUID adminUserId = responseId(createAdminUser(superToken, identity.userId(), false)
                .andExpect(status().isCreated()));
        UUID roleId = createAdminRole(superToken, "Limited " + UUID.randomUUID());
        for (String permissionCode : permissionCodes) {
            grantPermission(superToken, roleId, permissionCode)
                    .andExpect(status().isNoContent());
        }
        assignRole(superToken, adminUserId, roleId)
                .andExpect(status().isNoContent());
        return new LimitedAdmin(loginAdmin(identity), adminUserId, roleId);
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

    private String loginAdmin(ClientIdentity identity) throws Exception {
        LoginRequest request = new LoginRequest(identity.email(), CLIENT_PASSWORD);
        return accessToken(mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))));
    }

    private UUID createAdminRole(String token, String name) throws Exception {
        CreateAdminRoleRequest request = new CreateAdminRoleRequest(name, "Security test role");
        return responseId(mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
    }

    private ResultActions createAdminUser(String token, UUID userId, boolean superAdmin) throws Exception {
        CreateAdminUserRequest request = new CreateAdminUserRequest(userId, superAdmin);
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

    private ResultActions toggleAdmin(String token, UUID adminUserId) throws Exception {
        return mockMvc.perform(post("/api/admin/users/{id}/toggle-active", adminUserId)
                .header("Authorization", bearer(token)));
    }

    private ResultActions updateFeatureStatus(String token, UUID featureId, FeatureStatus status) throws Exception {
        return mockMvc.perform(patch("/api/admin/registry/features/{id}/status", featureId)
                .header("Authorization", bearer(token))
                .param("status", status.name()));
    }

    private UUID responseId(ResultActions action) throws Exception {
        JsonNode response = objectMapper.readTree(action.andReturn().getResponse().getContentAsString());
        return UUID.fromString(response.get("id").asText());
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
