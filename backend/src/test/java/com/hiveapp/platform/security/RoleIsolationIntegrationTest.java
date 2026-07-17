package com.hiveapp.platform.security;

import com.hiveapp.platform.client.member.dto.AssignRoleRequest;
import com.hiveapp.platform.client.role.dto.CreateRoleRequest;
import com.hiveapp.platform.client.role.dto.UpdateRoleRequest;
import com.hiveapp.platform.client.role.dto.RoleImpactConfirmationRequest;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired private RoleRepository roleRepository;

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
                .andExpect(status().isNotFound());
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
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCannotDeleteRoleFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerRoleId = createRole(ownerToken, null, "Owner Manager");

        mockMvc.perform(delete("/api/v1/roles/{id}", ownerRoleId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
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
                .andExpect(status().isNotFound());
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
                .andExpect(status().isNotFound());
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
                .andExpect(status().isNotFound());
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

    @Test
    void assignedRoleLifecycleRequiresFreshImpactConfirmationAndRetainsHistory() throws Exception {
        String token = registerClientAndGetToken();
        UUID roleId = createRole(token, null, "Lifecycle Manager");
        UUID memberId = currentMemberId(token);

        mockMvc.perform(post("/api/v1/roles/{id}/permissions", roleId)
                        .header("Authorization", bearer(token))
                        .param("permissionCode", "platform.company.read_single"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(post("/api/v1/roles/{id}/activate", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/v1/members/{id}/roles", memberId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRoleRequest(roleId, null))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/roles/{id}/deactivate", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/roles/{id}", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateRoleRequest("Updated Manager", "Shared edit"))))
                .andExpect(status().isConflict());
        String updateImpactJson = mockMvc.perform(get("/api/v1/roles/{id}/impact", roleId)
                        .header("Authorization", bearer(token))
                        .param("changeType", "UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentCount").value(1))
                .andReturn().getResponse().getContentAsString();
        var updateImpact = objectMapper.readTree(updateImpactJson);
        mockMvc.perform(put("/api/v1/roles/{id}", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(
                                "Updated Manager", "Shared edit",
                                updateImpact.get("version").asLong(),
                                updateImpact.get("assignmentCount").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Manager"));

        String impactJson = mockMvc.perform(get("/api/v1/roles/{id}/impact", roleId)
                        .header("Authorization", bearer(token))
                        .param("changeType", "DEACTIVATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentCount").value(1))
                .andExpect(jsonPath("$.affectedMemberCount").value(1))
                .andExpect(jsonPath("$.confirmationRequired").value(true))
                .andExpect(jsonPath("$.permissionsLost[0]").value("platform.company.read_single"))
                .andReturn().getResponse().getContentAsString();
        var impact = objectMapper.readTree(impactJson);
        var confirmation = new RoleImpactConfirmationRequest(
                impact.get("version").asLong(), impact.get("assignmentCount").asLong());

        mockMvc.perform(post("/api/v1/roles/{id}/deactivate", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(delete("/api/v1/members/{id}/roles/{roleId}", memberId, roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/roles/{id}", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/roles/{id}/archive", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(put("/api/v1/roles/{id}", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateRoleRequest("Changed", "Cannot change archived role"))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/roles/{id}/activate", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void systemRoleIsReadOnlyButCanBeDuplicatedAsInactiveCustomRole() throws Exception {
        String token = registerClientAndGetToken();
        UUID roleId = createRole(token, null, "Platform Template");
        var systemRole = roleRepository.findById(roleId).orElseThrow();
        systemRole.setSystemRole(true);
        roleRepository.saveAndFlush(systemRole);

        mockMvc.perform(put("/api/v1/roles/{id}", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateRoleRequest("Changed", "Changed"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/roles/{id}/permissions", roleId)
                        .header("Authorization", bearer(token))
                        .param("permissionCode", "platform.company.read_single"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/roles/{id}/archive", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/roles/{id}", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/roles/{id}/duplicate", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Adopted Template\",\"description\":\"Tenant copy\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.isSystemRole").value(false))
                .andExpect(jsonPath("$.everAssigned").value(false));
    }

    @Test
    void duplicateCopiesPermissionsWithoutSharingLifecycleAndUnknownPermissionIsNotFound() throws Exception {
        String token = registerClientAndGetToken();
        UUID roleId = createRole(token, null, "Source Role");
        mockMvc.perform(post("/api/v1/roles/{id}/permissions", roleId)
                        .header("Authorization", bearer(token))
                        .param("permissionCode", "platform.company.read_single"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/roles/{id}/duplicate", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Staged Role\",\"description\":\"Rollout copy\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.definitionRevision").value(1))
                .andExpect(jsonPath("$.permissionCodes[0]").value("platform.company.read_single"));

        mockMvc.perform(post("/api/v1/roles/{id}/permissions", roleId)
                        .header("Authorization", bearer(token))
                        .param("permissionCode", "platform.unknown.action"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/roles/{id}/impact", roleId)
                        .header("Authorization", bearer(token))
                        .param("changeType", "ADD_PERMISSION")
                        .param("permissionCode", "platform.unknown.action"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unusedCustomRoleCanBeHardDeleted() throws Exception {
        String token = registerClientAndGetToken();
        UUID roleId = createRole(token, null, "Disposable Role");

        mockMvc.perform(delete("/api/v1/roles/{id}", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/roles/{id}", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
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
