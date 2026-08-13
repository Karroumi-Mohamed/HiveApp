package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.constant.IdentityKind;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.admin.dto.CreateAdminUserRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform operators own their identity. They are created outright and are never selected from,
 * promoted out of, or otherwise imported from the client user pool.
 */
class OperatorIdentityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    void creatingAnOperatorCreatesAPlatformIdentityAndItsGrantTogether() throws Exception {
        String token = loginAdminAndGetToken();
        String email = operatorEmail();

        JsonNode response = json(createOperator(token, email, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operator.email").value(email))
                // The activation link is the credential; no password is minted alongside it.
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.credentialState").value("EMAIL_ACTIVATION_PENDING")));

        UUID operatorUserId = UUID.fromString(response.get("operator").get("userId").asText());
        assertThat(userRepository.findById(operatorUserId).orElseThrow().getKind())
                .isEqualTo(IdentityKind.PLATFORM);
        assertThat(adminUserRepository.findByUserId(operatorUserId)).isPresent();
    }

    @Test
    void aNewOperatorHoldsNoUsableCredentialUntilTheyActivate() throws Exception {
        String token = loginAdminAndGetToken();
        String email = operatorEmail();
        createOperator(token, email, false).andExpect(status().isCreated());

        // Nothing was handed out that could be used to sign in.
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "any-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theTemporaryAccessFallbackIssuesAWorkingPassword() throws Exception {
        String token = loginAdminAndGetToken();
        String email = operatorEmail();
        UUID adminUserId = UUID.fromString(json(createOperator(token, email, false)
                .andExpect(status().isCreated()))
                .get("operator").get("id").asText());

        String temporaryPassword = json(mockMvc.perform(
                        post("/api/admin/users/{id}/access/temporary", adminUserId)
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk()))
                .get("temporaryPassword").asText();

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, temporaryPassword))))
                .andExpect(status().isOk());
    }

    @Test
    void anEmailAlreadyUsedByAClientMemberIsRejectedAndLeavesNoGrantBehind() throws Exception {
        String token = loginAdminAndGetToken();
        String clientEmail = "client-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                clientEmail, "ClientPassword123!", "Client", "Person", null))))
                .andExpect(status().isCreated());
        long grantsBefore = adminUserRepository.count();

        createOperator(token, clientEmail, false).andExpect(status().isConflict());

        assertThat(adminUserRepository.count()).isEqualTo(grantsBefore);
    }

    @Test
    void memberCreationStillProducesAClientIdentity() throws Exception {
        String clientEmail = "client-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                clientEmail, "ClientPassword123!", "Client", "Person", null))))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail(clientEmail).orElseThrow().getKind())
                .isEqualTo(IdentityKind.CLIENT);
    }

    @Test
    void theBootstrapAdministratorIsAPlatformIdentity() {
        var bootstrapAdmin = adminUserRepository.findAll().stream()
                .filter(admin -> admin.isSuperAdmin())
                .findFirst()
                .orElseThrow();

        // Reading the id off the lazy proxy is free; touching any other field would need a
        // session, and open-in-view is deliberately off.
        UUID bootstrapUserId = bootstrapAdmin.getUser().getId();

        assertThat(userRepository.findById(bootstrapUserId).orElseThrow().getKind())
                .isEqualTo(IdentityKind.PLATFORM);
    }

    /**
     * Asserted against the permission registry rather than the route: with the endpoint gone,
     * "/users/candidates" is now just a malformed "/users/{id}", so its HTTP status says more
     * about UUID parsing than about the deletion.
     */
    @Test
    void theCandidateSearchPermissionNoLongerExists() {
        assertThat(permissionRepository.findByCode("platform.admin_users.search_candidates"))
                .isEmpty();
    }

    @Test
    void anOperatorHoldingNoRoleCanStillLoadTheirOwnProfile() throws Exception {
        String token = loginAdminAndGetToken();
        String email = operatorEmail();
        UUID adminUserId = UUID.fromString(json(createOperator(token, email, false)
                .andExpect(status().isCreated()))
                .get("operator").get("id").asText());
        String temporaryPassword = json(mockMvc.perform(
                        post("/api/admin/users/{id}/access/temporary", adminUserId)
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk()))
                .get("temporaryPassword").asText();
        String operatorToken = json(mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, temporaryPassword))))
                        .andExpect(status().isOk()))
                .get("accessToken").asText();

        // The call that reports permissions must not itself require one, or an operator would
        // sign in and immediately be refused their own profile.
        mockMvc.perform(get("/api/admin/me").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());

        // And exempting it must not have weakened anything that is genuinely guarded.
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isForbidden());
    }

    private ResultActions createOperator(String token, String email, boolean superAdmin) throws Exception {
        return mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateAdminUserRequest("Op", "Erator", email, superAdmin))));
    }

    private JsonNode json(ResultActions action) throws Exception {
        return objectMapper.readTree(action.andReturn().getResponse().getContentAsString());
    }

    private static String operatorEmail() {
        return "op-" + UUID.randomUUID() + "@hiveapp.test";
    }
}
