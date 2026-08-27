package com.hiveapp.platform.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.platform.admin.dto.AdminRoleImpactRequest;
import com.hiveapp.platform.admin.dto.AdminRolePermissionSetRequest;
import com.hiveapp.platform.admin.dto.AdminRoleStatusRequest;
import com.hiveapp.platform.admin.dto.CreateAdminRoleFromPresetRequest;
import com.hiveapp.platform.admin.dto.CreateAdminRoleRequest;
import com.hiveapp.platform.admin.dto.CreateAdminUserRequest;
import com.hiveapp.platform.admin.dto.DuplicateAdminRoleRequest;
import com.hiveapp.platform.admin.dto.UpdateAdminRoleRequest;
import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminRoleManagementIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Test
    void creationNormalizesNamesAndReturnsStableConflict() throws Exception {
        String token = loginAdminAndGetToken();
        String marker = UUID.randomUUID().toString().substring(0, 8);

        JsonNode created = createRole(token, "  Support   " + marker + "  ", List.of());
        assertThat(created.get("name").asText()).isEqualTo("Support " + marker);
        assertThat(created.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(created.get("isActive").asBoolean()).isFalse();
        assertThat(created.get("assignedOperatorCount").asLong()).isZero();
        assertThat(created.get("deletable").asBoolean()).isTrue();
        assertThat(created.get("availableActions").toString()).contains("EDIT_METADATA");

        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAdminRoleRequest("support " + marker, null, List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NAME_CONFLICT"));

        JsonNode other = createRole(token, "Other " + marker, List.of());
        UUID otherId = UUID.fromString(other.get("id").asText());
        mockMvc.perform(patch("/api/admin/roles/{id}/metadata", otherId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAdminRoleRequest(
                                " support   " + marker,
                                null,
                                other.get("version").asLong()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NAME_CONFLICT"));
        mockMvc.perform(post("/api/admin/roles/from-preset")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAdminRoleFromPresetRequest(
                                "PLATFORM_OBSERVER", "SUPPORT " + marker, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NAME_CONFLICT"));
        mockMvc.perform(post("/api/admin/roles/{id}/duplicate", otherId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DuplicateAdminRoleRequest("Support " + marker, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NAME_CONFLICT"));
    }

    @Test
    void concurrentNormalizedNameCollisionReturnsOneCreatedAndOneConflict() throws Exception {
        String token = loginAdminAndGetToken();
        String name = "Concurrent " + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new CreateAdminRoleRequest(name, null, List.of()));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/admin/roles")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/admin/roles")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void presetAndDuplicateProduceIndependentInactiveRoles() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode presets = responseJson(mockMvc.perform(get("/api/admin/role-presets")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        JsonNode preset = findByCode(presets, "PLATFORM_OBSERVER");
        Set<String> expectedCodes = permissionCodes(preset.get("permissions"));
        String marker = UUID.randomUUID().toString().substring(0, 8);

        JsonNode source = responseJson(mockMvc.perform(post("/api/admin/roles/from-preset")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAdminRoleFromPresetRequest(
                                "PLATFORM_OBSERVER", "Observer " + marker, null, null))))
                .andExpect(status().isCreated()));
        assertThat(permissionCodes(source.get("permissions"))).isEqualTo(expectedCodes);

        UUID sourceId = UUID.fromString(source.get("id").asText());
        transition(token, sourceId, AdminRoleStatus.ACTIVE);
        JsonNode duplicate = responseJson(mockMvc.perform(post("/api/admin/roles/{id}/duplicate", sourceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DuplicateAdminRoleRequest("Observer copy " + marker, null))))
                .andExpect(status().isCreated()));

        assertThat(duplicate.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(duplicate.get("assignedOperatorCount").asLong()).isZero();
        assertThat(permissionCodes(duplicate.get("permissions"))).isEqualTo(expectedCodes);
    }

    @Test
    void segmentPermissionsRespectOperationalPresetSensitivity() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode presets = responseJson(mockMvc.perform(get("/api/admin/role-presets")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));

        Set<String> observerCodes = permissionCodes(
                findByCode(presets, "PLATFORM_OBSERVER").get("permissions"));
        Set<String> commercialCodes = permissionCodes(
                findByCode(presets, "COMMERCIAL_OPERATIONS").get("permissions"));
        Set<String> allSegmentCodes = permissionRepository.findAll().stream()
                .map(permission -> permission.getCode())
                .filter(code -> code.startsWith("platform.segments."))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> observerSegmentCodes = Set.of(
                "platform.segments.list",
                "platform.segments.read_detail",
                "platform.segments.compare",
                "platform.segments.read_revisions",
                "platform.segments.read_history",
                "platform.segments.count",
                "platform.segments.preview",
                "platform.segments.read_activations",
                "platform.segments.read_activation_audience");

        assertThat(allSegmentCodes).hasSize(22);
        assertThat(commercialCodes).containsAll(allSegmentCodes);
        assertThat(observerCodes).containsAll(observerSegmentCodes);
        assertThat(observerCodes).doesNotContain(
                "platform.segments.read_sample_identities",
                "platform.segments.read_activation_identities",
                "platform.segments.read_owner");
        assertThat(observerCodes.stream()
                .filter(allSegmentCodes::contains)
                .collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(observerSegmentCodes);
    }

    @Test
    void campaignPermissionsRespectOperationalPresetSensitivity() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode presets = responseJson(mockMvc.perform(get("/api/admin/role-presets")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));

        Set<String> observerCodes = permissionCodes(
                findByCode(presets, "PLATFORM_OBSERVER").get("permissions"));
        Set<String> commercialCodes = permissionCodes(
                findByCode(presets, "COMMERCIAL_OPERATIONS").get("permissions"));
        Set<String> allCampaignCodes = permissionRepository.findAll().stream()
                .map(permission -> permission.getCode())
                .filter(code -> code.startsWith("platform.campaigns."))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> observerCampaignCodes = Set.of(
                "platform.campaigns.list",
                "platform.campaigns.read",
                "platform.campaigns.compare",
                "platform.campaigns.revisions",
                "platform.campaigns.history",
                "platform.campaigns.preview_schedule",
                "platform.campaigns.read_audience");

        assertThat(allCampaignCodes).hasSize(26);
        assertThat(commercialCodes).containsAll(allCampaignCodes);
        assertThat(observerCodes.stream().filter(allCampaignCodes::contains)
                .collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(observerCampaignCodes);
        assertThat(observerCodes).doesNotContain(
                "platform.campaigns.owner",
                "platform.campaigns.read_audience_identities",
                "platform.campaigns.choose_owners",
                "platform.campaigns.resolve_owner_choices",
                "platform.campaigns.choose_accounts",
                "platform.campaigns.choose_segments");
    }

    @Test
    void permissionReplacementRequiresCurrentImpactPreview() throws Exception {
        String token = loginAdminAndGetToken();
        UUID permissionId = permissionRepository.findByCode("platform.registry.read").orElseThrow().getId();
        JsonNode created = createRole(token, "Preview " + UUID.randomUUID(), List.of());
        UUID roleId = UUID.fromString(created.get("id").asText());

        JsonNode stalePreview = previewPermissions(token, roleId, List.of(permissionId));
        mockMvc.perform(patch("/api/admin/roles/{id}/metadata", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAdminRoleRequest(
                                created.get("name").asText(), "changed", created.get("version").asLong()))))
                .andExpect(status().isOk());

        replacePermissions(token, roleId, permissionId, stalePreview)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_IMPACT_PREVIEW"));

        JsonNode freshPreview = previewPermissions(token, roleId, List.of(permissionId));
        replacePermissions(token, roleId, permissionId, freshPreview)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0].code").value("platform.registry.read"));
    }

    @Test
    void roleTableCanSortByAssignedOperatorCount() throws Exception {
        String token = loginAdminAndGetToken();
        String marker = "sort-" + UUID.randomUUID();
        JsonNode unassigned = createRole(token, marker + " empty", List.of());
        JsonNode assigned = createRole(token, marker + " assigned", List.of());
        UUID assignedRoleId = UUID.fromString(assigned.get("id").asText());
        transition(token, assignedRoleId, AdminRoleStatus.ACTIVE);

        JsonNode operator = responseJson(mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAdminUserRequest(
                                "Sorted", "Holder", "sorted-holder-" + UUID.randomUUID() + "@hiveapp.test",
                                InitialAccessMethod.EMAIL_LINK, false))))
                .andExpect(status().isCreated()));
        UUID operatorId = UUID.fromString(operator.get("operator").get("id").asText());
        mockMvc.perform(post("/api/admin/users/{id}/roles/{roleId}", operatorId, assignedRoleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/roles")
                        .header("Authorization", bearer(token))
                        .param("search", marker)
                        .param("sort", "assignedOperatorCount")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(assignedRoleId.toString()))
                .andExpect(jsonPath("$.content[1].id").value(unassigned.get("id").asText()));
    }

    @Test
    void aPreviouslyAssignedRoleCannotBePermanentlyDeleted() throws Exception {
        String token = loginAdminAndGetToken();
        UUID permissionId = permissionRepository.findByCode("platform.registry.read").orElseThrow().getId();
        JsonNode role = createRole(token, "Historical " + UUID.randomUUID(), List.of(permissionId));
        UUID roleId = UUID.fromString(role.get("id").asText());
        transition(token, roleId, AdminRoleStatus.ACTIVE);

        JsonNode operator = responseJson(mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAdminUserRequest(
                                "Role", "Holder", "role-holder-" + UUID.randomUUID() + "@hiveapp.test",
                                InitialAccessMethod.EMAIL_LINK, false))))
                .andExpect(status().isCreated()));
        UUID operatorId = UUID.fromString(operator.get("operator").get("id").asText());

        mockMvc.perform(post("/api/admin/users/{id}/roles/{roleId}", operatorId, roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        JsonNode deactivationImpact = responseJson(mockMvc.perform(
                        post("/api/admin/roles/{id}/impact-preview", roleId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new AdminRoleImpactRequest(null, AdminRoleStatus.INACTIVE))))
                .andExpect(status().isOk()));
        assertThat(deactivationImpact.get("assignmentCount").asLong()).isEqualTo(1);
        assertThat(deactivationImpact.get("operatorsLosingLastPermissionSource").asLong()).isEqualTo(1);

        mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleId}", operatorId, roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        transition(token, roleId, AdminRoleStatus.INACTIVE);

        mockMvc.perform(delete("/api/admin/roles/{id}", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_BLOCKED"));
        mockMvc.perform(get("/api/admin/roles/{id}/history", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actorEmail").isNotEmpty())
                .andExpect(jsonPath("$[*].action", hasItem("platform.roles.create")))
                .andExpect(jsonPath("$[*].action", hasItem("platform.roles.assign_operator")))
                .andExpect(jsonPath("$[*].action", hasItem("platform.roles.remove_operator")));
    }

    @Test
    void assignmentChangesInvalidatePreviewEvenWhenTheHolderCountIsUnchanged() throws Exception {
        String token = loginAdminAndGetToken();
        UUID permissionId = permissionRepository.findByCode("platform.registry.read").orElseThrow().getId();
        JsonNode role = createRole(token, "Assignment race " + UUID.randomUUID(), List.of(permissionId));
        UUID roleId = UUID.fromString(role.get("id").asText());
        transition(token, roleId, AdminRoleStatus.ACTIVE);
        UUID firstOperatorId = createOperator(token, "first");
        UUID secondOperatorId = createOperator(token, "second");
        mockMvc.perform(post("/api/admin/users/{id}/roles/{roleId}", firstOperatorId, roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        JsonNode preview = responseJson(mockMvc.perform(post("/api/admin/roles/{id}/impact-preview", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminRoleImpactRequest(null, AdminRoleStatus.INACTIVE))))
                .andExpect(status().isOk()));

        mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleId}", firstOperatorId, roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/users/{id}/roles/{roleId}", secondOperatorId, roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/roles/{id}/status", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminRoleStatusRequest(
                                AdminRoleStatus.INACTIVE,
                                preview.get("version").asLong(),
                                preview.get("assignmentCount").asLong()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_IMPACT_PREVIEW"));
    }

    @Test
    void concurrentAssignmentsBothSucceedAndAdvanceTheRoleRevision() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode role = createRole(token, "Concurrent assignment " + UUID.randomUUID(), List.of());
        UUID roleId = UUID.fromString(role.get("id").asText());
        JsonNode activeRole = transition(token, roleId, AdminRoleStatus.ACTIVE);
        assertThat(activeRole.get("availableActions").toString()).contains("ASSIGN_TO_OPERATOR");
        long versionBeforeAssignments = activeRole.get("version").asLong();
        UUID firstOperatorId = createOperator(token, "concurrent-first");
        UUID secondOperatorId = createOperator(token, "concurrent-second");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/admin/users/{id}/roles/{roleId}", firstOperatorId, roleId)
                                .header("Authorization", bearer(token)))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/admin/users/{id}/roles/{roleId}", secondOperatorId, roleId)
                                .header("Authorization", bearer(token)))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactly(204, 204);
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/admin/roles/{id}", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedOperatorCount").value(2))
                .andExpect(jsonPath("$.version").value(versionBeforeAssignments + 2));
    }

    @Test
    void roleHistoryNamesTheAssignedOperator() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode role = createRole(token, "History subject " + UUID.randomUUID(), List.of());
        UUID roleId = UUID.fromString(role.get("id").asText());
        transition(token, roleId, AdminRoleStatus.ACTIVE);
        UUID operatorId = createOperator(token, "history-subject");
        String operatorEmail =
                adminUserRepository.findWithUserById(operatorId).orElseThrow().getUser().getEmail();

        mockMvc.perform(post("/api/admin/users/{id}/roles/{roleId}", operatorId, roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleId}", operatorId, roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        // The history line answers "to whom", not only "what": both directions carry the
        // operator's email as the entry subject.
        mockMvc.perform(get("/api/admin/roles/{id}/history", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.action == 'platform.roles.assign_operator')].subject",
                        hasItem(operatorEmail)))
                .andExpect(jsonPath(
                        "$[?(@.action == 'platform.roles.remove_operator')].subject",
                        hasItem(operatorEmail)));
    }

    @Test
    void inactiveAndArchivedRolesGrantNoEffectivePermissions() throws Exception {
        String token = loginAdminAndGetToken();
        String permissionCode = "platform.registry.read";
        UUID permissionId = permissionRepository.findByCode(permissionCode).orElseThrow().getId();
        JsonNode role = createRole(token, "Lifecycle grant " + UUID.randomUUID(), List.of(permissionId));
        UUID roleId = UUID.fromString(role.get("id").asText());
        UUID operatorId = createOperator(token, "lifecycle");
        transition(token, roleId, AdminRoleStatus.ACTIVE);
        mockMvc.perform(post("/api/admin/users/{id}/roles/{roleId}", operatorId, roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(adminUserRepository.hasPermission(operatorId, permissionCode)).isTrue();

        transition(token, roleId, AdminRoleStatus.INACTIVE);
        assertThat(adminUserRepository.hasPermission(operatorId, permissionCode)).isFalse();
        transition(token, roleId, AdminRoleStatus.ARCHIVED);
        assertThat(adminUserRepository.hasPermission(operatorId, permissionCode)).isFalse();
    }

    private JsonNode createRole(String token, String name, List<UUID> permissionIds) throws Exception {
        return responseJson(mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAdminRoleRequest(name, "Integration role", permissionIds))))
                .andExpect(status().isCreated()));
    }

    private UUID createOperator(String token, String marker) throws Exception {
        JsonNode operator = responseJson(mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAdminUserRequest(
                                "Role",
                                "Holder",
                                marker + "-" + UUID.randomUUID() + "@hiveapp.test",
                                InitialAccessMethod.EMAIL_LINK,
                                false))))
                .andExpect(status().isCreated()));
        return UUID.fromString(operator.get("operator").get("id").asText());
    }

    private JsonNode previewPermissions(String token, UUID roleId, List<UUID> permissionIds) throws Exception {
        return responseJson(mockMvc.perform(post("/api/admin/roles/{id}/impact-preview", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminRoleImpactRequest(permissionIds, null))))
                .andExpect(status().isOk()));
    }

    private ResultActions replacePermissions(String token, UUID roleId, UUID permissionId, JsonNode preview)
            throws Exception {
        return mockMvc.perform(put("/api/admin/roles/{id}/permissions", roleId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdminRolePermissionSetRequest(
                        List.of(permissionId),
                        preview.get("version").asLong(),
                        preview.get("assignmentCount").asLong()))));
    }

    private JsonNode transition(String token, UUID roleId, AdminRoleStatus statusValue) throws Exception {
        JsonNode preview = responseJson(mockMvc.perform(post("/api/admin/roles/{id}/impact-preview", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminRoleImpactRequest(null, statusValue))))
                .andExpect(status().isOk()));
        return responseJson(mockMvc.perform(post("/api/admin/roles/{id}/status", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminRoleStatusRequest(
                                statusValue,
                                preview.get("version").asLong(),
                                preview.get("assignmentCount").asLong()))))
                .andExpect(status().isOk()));
    }

    private JsonNode responseJson(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static JsonNode findByCode(JsonNode values, String code) {
        for (JsonNode value : values) {
            if (code.equals(value.get("code").asText())) {
                return value;
            }
        }
        throw new AssertionError("Missing preset " + code);
    }

    private static Set<String> permissionCodes(JsonNode permissions) {
        Set<String> codes = new HashSet<>();
        permissions.forEach(permission -> codes.add(permission.get("code").asText()));
        return codes;
    }
}
