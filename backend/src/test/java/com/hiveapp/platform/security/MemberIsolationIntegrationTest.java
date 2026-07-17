package com.hiveapp.platform.security;

import com.hiveapp.platform.client.member.dto.AssignRoleRequest;
import com.hiveapp.platform.client.member.dto.UpdateMemberRequest;
import com.hiveapp.platform.client.role.dto.CreateRoleRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberIsolationIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Test
    void memberListOnlyReturnsCurrentWorkspaceMembers() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerMemberId = currentMemberId(ownerToken);
        UUID otherMemberId = currentMemberId(otherToken);

        mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(not(ownerMemberId.toString()))))
                .andExpect(jsonPath("$[0].id").value(otherMemberId.toString()));
    }

    @Test
    void clientCannotUpdateMemberFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerMemberId = currentMemberId(ownerToken);

        UpdateMemberRequest request = new UpdateMemberRequest("Changed");

        mockMvc.perform(patch("/api/v1/members/{id}", ownerMemberId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCannotDeleteMemberFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerMemberId = currentMemberId(ownerToken);

        mockMvc.perform(delete("/api/v1/members/{id}", ownerMemberId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void companyScopedRoleCanOnlyBeAssignedInsideItsCompany() throws Exception {
        String token = registerClientAndGetToken();
        assignPlan(token, "PRO");
        UUID memberId = currentMemberId(token);
        UUID companyOneId = UUID.fromString(createCompany(token, "Company One").get("id").asText());
        UUID companyTwoId = UUID.fromString(createCompany(token, "Company Two").get("id").asText());
        UUID companyRoleId = createRole(token, companyOneId, "Company One Manager");
        addPermissionAndActivate(token, companyRoleId, "platform.company.read_single");

        assignRole(token, memberId, companyRoleId, null)
                .andExpect(status().isForbidden());

        assignRole(token, memberId, companyRoleId, companyTwoId)
                .andExpect(status().isForbidden());

        assignRole(token, memberId, companyRoleId, companyOneId)
                .andExpect(status().isNoContent());
    }

    @Test
    void clientCannotRemoveRoleFromMemberUsingRoleFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID otherMemberId = currentMemberId(otherToken);
        UUID ownerRoleId = createRole(ownerToken, null, "Owner Manager");

        mockMvc.perform(delete("/api/v1/members/{id}/roles/{roleId}", otherMemberId, ownerRoleId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions assignRole(
            String token, UUID memberId, UUID roleId, UUID companyId) throws Exception {
        AssignRoleRequest request = new AssignRoleRequest(roleId, companyId);
        return mockMvc.perform(post("/api/v1/members/{id}/roles", memberId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
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

    private void addPermissionAndActivate(String token, UUID roleId, String permissionCode) throws Exception {
        mockMvc.perform(post("/api/v1/roles/{id}/permissions", roleId)
                        .header("Authorization", bearer(token))
                        .param("permissionCode", permissionCode))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/roles/{id}/activate", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private void assignPlan(String clientToken, String planCode) throws Exception {
        String adminToken = loginAdminAndGetToken();
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}", currentAccountId(clientToken))
                        .param("planCode", planCode)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan.code").value(planCode));
    }

    private UUID currentAccountId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
