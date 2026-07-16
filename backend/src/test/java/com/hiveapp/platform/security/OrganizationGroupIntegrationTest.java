package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.company.domain.constant.GroupTemplateScope;
import com.hiveapp.platform.client.company.dto.*;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationGroupIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Test
    void companyStartsWithDeletableDepartmentsRootAndSiblingNamesAreNormalized() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Organization Company").get("id").asText());

        JsonNode groups = listGroups(token, companyId);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).get("name").asText()).isEqualTo("Departments");
        UUID departmentsId = UUID.fromString(groups.get(0).get("id").asText());

        createGroupRequest(token, companyId, null, "  departments  ", null, List.of())
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/organization/groups/{groupId}", departmentsId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(listGroups(token, companyId)).isEmpty();
    }

    @Test
    void hierarchySupportsReorderArchiveRestoreAndRejectsCyclesAndCrossCompanyMoves() throws Exception {
        String token = registerClientAndGetToken();
        UUID firstCompanyId = UUID.fromString(createCompany(token, "First Organization").get("id").asText());
        UUID departmentsId = groupIdByName(listGroups(token, firstCompanyId), "Departments");
        UUID operationsId = id(createGroup(token, firstCompanyId, null, "Operations"));
        UUID financeId = id(createGroup(token, firstCompanyId, null, "Finance"));
        UUID payrollId = id(createGroup(token, firstCompanyId, operationsId, "Payroll"));

        mockMvc.perform(post("/api/v1/organization/groups/{groupId}/move", operationsId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveOrganizationGroupRequest(payrollId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A Group cannot be moved inside its own subtree"));

        mockMvc.perform(put("/api/v1/organization/groups/reorder")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReorderOrganizationGroupsRequest(
                                firstCompanyId, null, List.of(financeId, operationsId, departmentsId)))))
                .andExpect(status().isNoContent());
        assertThat(rootIds(listGroups(token, firstCompanyId)))
                .containsExactly(financeId, operationsId, departmentsId);

        mockMvc.perform(post("/api/v1/organization/groups/{groupId}/archive", operationsId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(statusById(listGroups(token, firstCompanyId), operationsId)).isEqualTo("ARCHIVED");
        assertThat(statusById(listGroups(token, firstCompanyId), payrollId)).isEqualTo("ARCHIVED");
        createGroupRequest(token, firstCompanyId, operationsId, "Blocked Child", null, List.of())
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/organization/groups/{groupId}/restore", operationsId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(statusById(listGroups(token, firstCompanyId), payrollId)).isEqualTo("ACTIVE");

        mockMvc.perform(delete("/api/v1/companies/{id}", firstCompanyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        UUID secondCompanyId = UUID.fromString(createCompany(token, "Second Organization").get("id").asText());
        UUID secondGroupId = id(createGroup(token, secondCompanyId, null, "Second Root"));

        mockMvc.perform(post("/api/v1/organization/groups/{groupId}/move", secondGroupId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveOrganizationGroupRequest(operationsId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Group belongs to a different Company"));
    }

    @Test
    void membershipsAreExplicitMultiPlacementsAndDeletionReturnsBlockers() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Membership Company").get("id").asText());
        UUID rootId = id(createGroup(token, companyId, null, "Engineering"));
        UUID childId = id(createGroup(token, companyId, rootId, "Platform Team"));
        UUID memberId = createMemberWithoutRoles(token);

        putMembership(token, rootId, memberId, "Engineer")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionTitle").value("Engineer"));
        putMembership(token, childId, memberId, "Team Lead")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionTitle").value("Team Lead"));

        mockMvc.perform(get("/api/v1/organization/groups/{groupId}/members", rootId)
                        .header("Authorization", bearer(token))
                        .param("includeDescendants", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].positionTitle", containsInAnyOrder("Engineer", "Team Lead")));

        mockMvc.perform(delete("/api/v1/organization/groups/{groupId}", rootId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details", hasItem("childGroups: 1")))
                .andExpect(jsonPath("$.details", hasItem("memberPlacements: 1")));

        String otherToken = registerClientAndGetToken();
        UUID otherMemberId = currentMemberId(otherToken);
        putMembership(token, rootId, otherMemberId, "Outsider")
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/organization/groups/{groupId}/members/{memberId}", childId, memberId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/organization/groups/{groupId}", childId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/organization/groups/{groupId}/members/{memberId}", rootId, memberId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/organization/groups/{groupId}", rootId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void templatesPreviewConflictsInstantiateAtomicallyAndExcludeMembers() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Template Company").get("id").asText());
        UUID sourceId = id(createGroup(token, companyId, null, "Divisions", List.of("Director")));
        id(createGroup(token, companyId, sourceId, "Technology", List.of("VP Technology")));
        UUID ownerId = currentMemberId(token);
        putMembership(token, sourceId, ownerId, "Director").andExpect(status().isOk());

        CreateGroupTemplateRequest request = new CreateGroupTemplateRequest(
                companyId, sourceId, GroupTemplateScope.ACCOUNT, "Division Structure");
        String response = mockMvc.perform(post("/api/v1/organization/templates")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeCount").value(2))
                .andReturn().getResponse().getContentAsString();
        UUID templateId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(get("/api/v1/organization/templates/{templateId}/preview", templateId)
                        .header("Authorization", bearer(token))
                        .param("companyId", companyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canInstantiate").value(false))
                .andExpect(jsonPath("$.conflicts[0]").value("Sibling name already exists: Divisions"));
        int beforeBlockedInstantiation = listGroups(token, companyId).size();
        mockMvc.perform(post("/api/v1/organization/templates/{templateId}/instantiate", templateId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InstantiateGroupTemplateRequest(companyId, null))))
                .andExpect(status().isConflict());
        assertThat(listGroups(token, companyId)).hasSize(beforeBlockedInstantiation);

        UUID containerId = id(createGroup(token, companyId, null, "Imported Structures"));
        String instantiateResponse = mockMvc.perform(post("/api/v1/organization/templates/{templateId}/instantiate", templateId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InstantiateGroupTemplateRequest(companyId, containerId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].name").value("Divisions"))
                .andExpect(jsonPath("$[0].directMemberCount").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID importedRootId = UUID.fromString(objectMapper.readTree(instantiateResponse).get(0).get("id").asText());
        mockMvc.perform(get("/api/v1/organization/groups/{groupId}/members", importedRootId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(delete("/api/v1/organization/templates/{templateId}", templateId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/organization/templates")
                        .header("Authorization", bearer(token))
                        .param("companyId", companyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void membershipPositionRenameAndReparentDoNotChangeEffectivePermissions() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Authorization Independence").get("id").asText());
        UUID memberId = currentMemberId(token);
        UUID firstRootId = id(createGroup(token, companyId, null, "First Root"));
        UUID secondRootId = id(createGroup(token, companyId, null, "Second Root"));
        UUID childId = id(createGroup(token, companyId, firstRootId, "Movable"));
        Set<String> baseline = mePermissions(token);

        putMembership(token, childId, memberId, "Analyst").andExpect(status().isOk());
        putMembership(token, childId, memberId, "Senior Analyst").andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/organization/groups/{groupId}", childId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateOrganizationGroupRequest("Renamed", null, null))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/organization/groups/{groupId}/move", childId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveOrganizationGroupRequest(secondRootId))))
                .andExpect(status().isOk());

        assertThat(mePermissions(token)).isEqualTo(baseline);
    }

    private JsonNode createGroup(String token, UUID companyId, UUID parentId, String name) throws Exception {
        return createGroup(token, companyId, parentId, name, List.of());
    }

    private JsonNode createGroup(
            String token, UUID companyId, UUID parentId, String name, List<String> positions) throws Exception {
        String response = createGroupRequest(token, companyId, parentId, name, null, positions)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private org.springframework.test.web.servlet.ResultActions createGroupRequest(
            String token, UUID companyId, UUID parentId, String name, String description, List<String> positions)
            throws Exception {
        return mockMvc.perform(post("/api/v1/organization/groups")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateOrganizationGroupRequest(companyId, parentId, name, description, positions))));
    }

    private org.springframework.test.web.servlet.ResultActions putMembership(
            String token, UUID groupId, UUID memberId, String position) throws Exception {
        return mockMvc.perform(put("/api/v1/organization/groups/{groupId}/members/{memberId}", groupId, memberId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GroupMembershipRequest(position))));
    }

    private JsonNode listGroups(String token, UUID companyId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/organization/groups")
                        .header("Authorization", bearer(token))
                        .param("companyId", companyId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createMemberWithoutRoles(String token) throws Exception {
        CreateMemberRequest request = new CreateMemberRequest(
                "member-" + UUID.randomUUID().toString().substring(0, 8), null,
                "No", "Role", "No Role", null, null, List.of());
        String response = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("member").get("id").asText());
    }

    private Set<String> mePermissions(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/me/permissions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Set<String> result = new HashSet<>();
        objectMapper.readTree(response).get("permissions").forEach(node -> result.add(node.asText()));
        return result;
    }

    private UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    private UUID groupIdByName(JsonNode groups, String name) {
        for (JsonNode group : groups) {
            if (name.equals(group.get("name").asText())) return id(group);
        }
        throw new AssertionError("Missing Group: " + name);
    }

    private List<UUID> rootIds(JsonNode groups) {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode group : groups) {
            if (group.get("parentId").isNull()) ids.add(id(group));
        }
        return ids;
    }

    private String statusById(JsonNode groups, UUID id) {
        for (JsonNode group : groups) {
            if (id.toString().equals(group.get("id").asText())) return group.get("status").asText();
        }
        throw new AssertionError("Missing Group: " + id);
    }
}
