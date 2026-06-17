package com.hiveapp.platform.security;

import com.hiveapp.platform.client.collaboration.dto.B2BPermissionRequest;
import com.hiveapp.platform.client.collaboration.dto.InitiateCollaborationRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class B2bCollaborationSecurityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Test
    void clientCannotRequestCollaborationWithOwnCompany() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Own Company").get("id").asText());

        mockMvc.perform(post("/api/v1/collaborations/initiate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InitiateCollaborationRequest(companyId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyProviderCanAcceptIncomingCollaboration() throws Exception {
        B2bSetup setup = setupPendingCollaboration();

        mockMvc.perform(patch("/api/v1/collaborations/{id}/accept", setup.collaborationId())
                        .header("Authorization", bearer(setup.clientToken())))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/collaborations/{id}/accept", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void pendingCollaborationCannotBeUsedForB2bAccessOrGrantedPermissions() throws Exception {
        B2bSetup setup = setupPendingCollaboration();

        mockMvc.perform(get("/api/v1/companies/{id}", setup.companyId())
                        .header("Authorization", bearer(setup.clientToken()))
                        .header("X-Company-ID", setup.companyId().toString())
                        .header("X-Is-B2B", "true"))
                .andExpect(status().isForbidden());

        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isConflict());
    }

    @Test
    void providerCannotDelegatePlatformControlPermission() throws Exception {
        B2bSetup setup = setupActiveCollaboration();

        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.plans.create")
                .andExpect(status().isBadRequest());
    }

    @Test
    void providerCannotDelegateCompanyActionsThatAreNotB2bActions() throws Exception {
        B2bSetup setup = setupActiveCollaboration();

        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.create")
                .andExpect(status().isBadRequest());
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_all")
                .andExpect(status().isBadRequest());
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.update")
                .andExpect(status().isBadRequest());
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.delete")
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientCannotGrantPermissionsToCollaboration() throws Exception {
        B2bSetup setup = setupActiveCollaboration();

        grantPermission(setup.clientToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isForbidden());
    }

    @Test
    void activeDelegatedPermissionAllowsOnlyThenDeniesAfterRevocation() throws Exception {
        B2bSetup setup = setupActiveCollaboration();
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNoContent());

        b2bCompanyRead(setup)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(setup.companyId().toString()));

        mockMvc.perform(delete("/api/v1/collaborations/{id}", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken())))
                .andExpect(status().isNoContent());

        b2bCompanyRead(setup)
                .andExpect(status().isForbidden());
    }

    @Test
    void b2bPermissionIsScopedToTheExactCollaborationCompany() throws Exception {
        String providerToken = registerClientAndGetToken();
        String clientToken = registerClientAndGetToken();
        assignPlan(providerToken, "PRO");

        UUID companyOneId = UUID.fromString(createCompany(providerToken, "Provider Company One").get("id").asText());
        UUID companyTwoId = UUID.fromString(createCompany(providerToken, "Provider Company Two").get("id").asText());

        B2bSetup companyOne = setupActiveCollaboration(providerToken, clientToken, companyOneId);
        B2bSetup companyTwo = setupActiveCollaboration(providerToken, clientToken, companyTwoId);

        grantPermission(providerToken, companyOne.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNoContent());

        b2bCompanyRead(companyOne)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyOneId.toString()));

        b2bCompanyRead(companyTwo)
                .andExpect(status().isForbidden());

        b2bCompanyRead(clientToken, companyOneId, companyTwoId)
                .andExpect(status().isForbidden());

        grantPermission(providerToken, companyTwo.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNoContent());

        b2bCompanyRead(companyTwo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyTwoId.toString()));
    }

    @Test
    void nonParticipantCannotRevokeCollaboration() throws Exception {
        B2bSetup setup = setupActiveCollaboration();
        String otherToken = registerClientAndGetToken();

        mockMvc.perform(delete("/api/v1/collaborations/{id}", setup.collaborationId())
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    private B2bSetup setupPendingCollaboration() throws Exception {
        String providerToken = registerClientAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(providerToken, "Provider Company").get("id").asText());
        return setupPendingCollaboration(providerToken, clientToken, companyId);
    }

    private B2bSetup setupPendingCollaboration(String providerToken, String clientToken, UUID companyId) throws Exception {
        InitiateCollaborationRequest request = new InitiateCollaborationRequest(companyId);

        String response = mockMvc.perform(post("/api/v1/collaborations/initiate")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID collaborationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        return new B2bSetup(providerToken, clientToken, companyId, collaborationId);
    }

    private B2bSetup setupActiveCollaboration() throws Exception {
        B2bSetup setup = setupPendingCollaboration();
        acceptCollaboration(setup);
        return setup;
    }

    private B2bSetup setupActiveCollaboration(String providerToken, String clientToken, UUID companyId) throws Exception {
        B2bSetup setup = setupPendingCollaboration(providerToken, clientToken, companyId);
        acceptCollaboration(setup);
        return setup;
    }

    private void acceptCollaboration(B2bSetup setup) throws Exception {
        mockMvc.perform(patch("/api/v1/collaborations/{id}/accept", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken())))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions grantPermission(
            String token, UUID collaborationId, String permissionCode) throws Exception {
        return mockMvc.perform(post("/api/v1/collaborations/{id}/permissions", collaborationId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new B2BPermissionRequest(permissionCode))));
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

    private org.springframework.test.web.servlet.ResultActions b2bCompanyRead(B2bSetup setup) throws Exception {
        return b2bCompanyRead(setup.clientToken(), setup.companyId(), setup.companyId());
    }

    private org.springframework.test.web.servlet.ResultActions b2bCompanyRead(
            String clientToken, UUID contextCompanyId, UUID requestedCompanyId) throws Exception {
        return mockMvc.perform(get("/api/v1/companies/{id}", requestedCompanyId)
                .header("Authorization", bearer(clientToken))
                .header("X-Company-ID", contextCompanyId.toString())
                .header("X-Is-B2B", "true"));
    }

    private record B2bSetup(
            String providerToken,
            String clientToken,
            UUID companyId,
            UUID collaborationId
    ) {
    }
}
