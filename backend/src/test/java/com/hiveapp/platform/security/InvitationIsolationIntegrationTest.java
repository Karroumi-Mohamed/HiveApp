package com.hiveapp.platform.security;

import com.hiveapp.platform.client.invitation.domain.repository.InvitationRepository;
import com.hiveapp.platform.client.invitation.dto.AcceptInvitationRequest;
import com.hiveapp.platform.client.invitation.dto.SendInvitationRequest;
import com.hiveapp.platform.client.role.dto.CreateRoleRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvitationIsolationIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private InvitationRepository invitationRepository;

    @Test
    void invitationListOnlyReturnsCurrentWorkspaceInvitations() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerInvitationId = sendInvitation(ownerToken, "owner-invite-" + UUID.randomUUID() + "@example.com", null, null);
        UUID otherInvitationId = sendInvitation(otherToken, "other-invite-" + UUID.randomUUID() + "@example.com", null, null);

        mockMvc.perform(get("/api/v1/invitations")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(not(ownerInvitationId.toString()))))
                .andExpect(jsonPath("$[0].id").value(otherInvitationId.toString()));
    }

    @Test
    void clientCannotRevokeInvitationFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerInvitationId = sendInvitation(ownerToken, "owner-invite-" + UUID.randomUUID() + "@example.com", null, null);

        mockMvc.perform(delete("/api/v1/invitations/{id}", ownerInvitationId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotInviteWithRoleFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerRoleId = createRole(ownerToken, null, "Owner Manager");
        SendInvitationRequest request = new SendInvitationRequest(
                "other-invite-" + UUID.randomUUID() + "@example.com",
                ownerRoleId,
                null
        );

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotInviteWithCompanyFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());
        SendInvitationRequest request = new SendInvitationRequest(
                "other-invite-" + UUID.randomUUID() + "@example.com",
                null,
                ownerCompanyId
        );

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void companyScopedRoleInviteRequiresMatchingCompanyScope() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Owner Company").get("id").asText());
        UUID roleId = createRole(token, companyId, "Company Manager");
        SendInvitationRequest request = new SendInvitationRequest(
                "company-invite-" + UUID.randomUUID() + "@example.com",
                roleId,
                null
        );

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void companyScopedRoleInviteCanUseMatchingCompanyScope() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Owner Company").get("id").asText());
        UUID roleId = createRole(token, companyId, "Company Manager");

        sendInvitation(token, "company-invite-" + UUID.randomUUID() + "@example.com", roleId, companyId);
    }

    @Test
    void duplicatePendingInvitationIsAConflict() throws Exception {
        String token = registerClientAndGetToken();
        String email = "pending-invite-" + UUID.randomUUID() + "@example.com";
        sendInvitation(token, email, null, null);
        SendInvitationRequest request = new SendInvitationRequest(email, null, null);

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void alreadyRevokedInvitationCannotBeRevokedAgain() throws Exception {
        String token = registerClientAndGetToken();
        UUID invitationId = sendInvitation(token, "revoke-invite-" + UUID.randomUUID() + "@example.com", null, null);

        mockMvc.perform(delete("/api/v1/invitations/{id}", invitationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/invitations/{id}", invitationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void revokedInvitationCannotBeAccepted() throws Exception {
        String token = registerClientAndGetToken();
        UUID invitationId = sendInvitation(token, "accept-revoked-" + UUID.randomUUID() + "@example.com", null, null);
        String invitationToken = invitationRepository.findById(invitationId).orElseThrow().getToken();

        mockMvc.perform(delete("/api/v1/invitations/{id}", invitationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        AcceptInvitationRequest request = new AcceptInvitationRequest(
                invitationToken, "Invited", "Member", CLIENT_PASSWORD);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void newInviteeRegistrationRequiresNamesAsBadRequest() throws Exception {
        String token = registerClientAndGetToken();
        UUID invitationId = sendInvitation(token, "invalid-register-" + UUID.randomUUID() + "@example.com", null, null);
        String invitationToken = invitationRepository.findById(invitationId).orElseThrow().getToken();
        AcceptInvitationRequest request = new AcceptInvitationRequest(invitationToken, null, null, CLIENT_PASSWORD);

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptedInvitationPreservesCompanyScopedRoleAssignment() throws Exception {
        String ownerToken = registerClientAndGetToken();
        assignPlan(ownerToken, "PRO");
        UUID companyOneId = UUID.fromString(createCompany(ownerToken, "Company One").get("id").asText());
        UUID companyTwoId = UUID.fromString(createCompany(ownerToken, "Company Two").get("id").asText());
        UUID roleId = createRole(ownerToken, companyOneId, "Company One Viewer");
        grantRolePermission(ownerToken, roleId, "platform.company.read_single");

        UUID invitationId = sendInvitation(
                ownerToken,
                "scoped-invite-" + UUID.randomUUID() + "@example.com",
                roleId,
                companyOneId);
        String invitationToken = invitationRepository.findById(invitationId).orElseThrow().getToken();

        AcceptInvitationRequest request = new AcceptInvitationRequest(
                invitationToken, "Scoped", "Member", CLIENT_PASSWORD);
        String response = mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String invitedToken = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/companies/{id}", companyOneId)
                        .header("Authorization", bearer(invitedToken))
                        .header("X-Company-ID", companyOneId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyOneId.toString()));

        mockMvc.perform(get("/api/v1/companies/{id}", companyTwoId)
                        .header("Authorization", bearer(invitedToken))
                        .header("X-Company-ID", companyTwoId.toString()))
                .andExpect(status().isForbidden());
    }

    private UUID sendInvitation(String token, String email, UUID roleId, UUID companyId) throws Exception {
        SendInvitationRequest request = new SendInvitationRequest(email, roleId, companyId);
        String response = mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
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

    private void grantRolePermission(String token, UUID roleId, String permissionCode) throws Exception {
        mockMvc.perform(post("/api/v1/roles/{id}/permissions", roleId)
                        .header("Authorization", bearer(token))
                        .param("permissionCode", permissionCode))
                .andExpect(status().isNoContent());
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
