package com.hiveapp.platform.security;

import com.hiveapp.platform.client.member.dto.AssignRoleRequest;
import com.hiveapp.platform.client.role.dto.CreateRoleRequest;
import com.hiveapp.platform.client.role.dto.UpdateRoleRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleIsolationIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Test
    void roleListOnlyReturnsCurrentWorkspaceRoles() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerRoleId = createRole(ownerToken, null, "Owner Manager");
        UUID otherRoleId = createRole(otherToken, null, "Other Manager");

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(not(ownerRoleId.toString()))))
                .andExpect(jsonPath("$[0].id").value(otherRoleId.toString()));
    }

    @Test
    void clientCannotReadRoleFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerRoleId = createRole(ownerToken, null, "Owner Manager");

        mockMvc.perform(get("/api/v1/roles/{id}", ownerRoleId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotUpdateRoleFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerRoleId = createRole(ownerToken, null, "Owner Manager");
        UpdateRoleRequest request = new UpdateRoleRequest("Changed", "Changed description");

        mockMvc.perform(put("/api/v1/roles/{id}", ownerRoleId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotDeleteRoleFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerRoleId = createRole(ownerToken, null, "Owner Manager");

        mockMvc.perform(delete("/api/v1/roles/{id}", ownerRoleId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotCreateCompanyScopedRoleForAnotherWorkspaceCompany() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());
        CreateRoleRequest request = new CreateRoleRequest(ownerCompanyId, "Cross Company Manager", null);

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotAssignRoleFromAnotherWorkspaceToCurrentMember() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerRoleId = createRole(ownerToken, null, "Owner Manager");
        UUID otherMemberId = currentMemberId(otherToken);
        AssignRoleRequest request = new AssignRoleRequest(ownerRoleId, null);

        mockMvc.perform(post("/api/v1/members/{id}/roles", otherMemberId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotAssignCurrentRoleUsingAnotherWorkspaceCompanyScope() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());
        UUID otherRoleId = createRole(otherToken, null, "Other Manager");
        UUID otherMemberId = currentMemberId(otherToken);
        AssignRoleRequest request = new AssignRoleRequest(otherRoleId, ownerCompanyId);

        mockMvc.perform(post("/api/v1/members/{id}/roles", otherMemberId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientRoleCannotReceivePlatformControlPermission() throws Exception {
        String token = registerClientAndGetToken();
        UUID roleId = createRole(token, null, "Manager");

        mockMvc.perform(post("/api/v1/roles/{id}/permissions", roleId)
                        .header("Authorization", bearer(token))
                .param("permissionCode", "platform.plans.create"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolePermissionCatalogOnlyShowsClientRoleGrantableEntitledPermissions() throws Exception {
        String token = registerClientAndGetToken();

        mockMvc.perform(get("/api/v1/roles/permission-catalog")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].features[*].permissions[*].code", hasItem("platform.company.create")))
                .andExpect(jsonPath("$[*].features[*].permissions[*].code",
                        everyItem(not("platform.plans.create"))))
                .andExpect(jsonPath("$[*].features[*].permissions[*].code",
                        everyItem(not("platform.registry.read"))));
    }

    private UUID createRole(String token, UUID companyId, String name) throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(companyId, name, name + " description");
        String response = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
