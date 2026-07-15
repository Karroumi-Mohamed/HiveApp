package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationSecurityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void clientEmailIsCanonicalAndLoginIsCaseInsensitive() throws Exception {
        String localPart = "Mixed-" + UUID.randomUUID();
        String mixedCaseEmail = localPart + "@Example.COM";
        register(mixedCaseEmail);

        String canonicalEmail = mixedCaseEmail.toLowerCase();
        assertThat(userRepository.findByEmail(canonicalEmail)).isPresent();

        LoginRequest login = new LoginRequest(canonicalEmail.toUpperCase(), CLIENT_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void clientRefreshRotatesOnceAndLogoutRevokesReplacement() throws Exception {
        JsonNode registration = register("refresh-" + UUID.randomUUID() + "@example.com");
        String oldRefreshToken = registration.get("refreshToken").asText();

        JsonNode rotated = refresh("/api/v1/auth/refresh", oldRefreshToken, 200);
        String replacementRefreshToken = rotated.get("refreshToken").asText();
        assertThat(replacementRefreshToken).isNotEqualTo(oldRefreshToken);

        refresh("/api/v1/auth/refresh", oldRefreshToken, 401);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(replacementRefreshToken))))
                .andExpect(status().isNoContent());

        refresh("/api/v1/auth/refresh", replacementRefreshToken, 401);
    }

    @Test
    void accessAndRefreshTokensCannotExchangePurposes() throws Exception {
        JsonNode registration = register("purpose-" + UUID.randomUUID() + "@example.com");
        String accessToken = registration.get("accessToken").asText();
        String refreshToken = registration.get("refreshToken").asText();

        refresh("/api/v1/auth/refresh", accessToken, 401);

        mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", bearer(refreshToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRefreshPreservesAudienceAndCannotUseClientRefreshToken() throws Exception {
        JsonNode adminLogin = loginAdmin();
        String oldAdminRefresh = adminLogin.get("refreshToken").asText();

        JsonNode rotated = refresh("/api/admin/auth/refresh", oldAdminRefresh, 200);
        String adminAccess = rotated.get("accessToken").asText();

        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", bearer(adminAccess)))
                .andExpect(status().isOk());
        refresh("/api/admin/auth/refresh", oldAdminRefresh, 401);

        JsonNode clientRegistration = register("audience-" + UUID.randomUUID() + "@example.com");
        refresh("/api/admin/auth/refresh", clientRegistration.get("refreshToken").asText(), 401);
    }

    @Test
    void adminLoginPayloadIsValidated() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void suspendedWorkspaceIsRejectedDuringContextResolution() throws Exception {
        String email = "suspended-" + UUID.randomUUID() + "@example.com";
        JsonNode registration = register(email);
        String accessToken = registration.get("accessToken").asText();
        var user = userRepository.findByEmail(email).orElseThrow();
        var account = accountRepository.findByOwner_Id(user.getId()).orElseThrow();
        account.setActive(false);
        accountRepository.saveAndFlush(account);

        mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied: Workspace account is suspended"));
    }

    private JsonNode register(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(email, CLIENT_PASSWORD, "Auth", "User", null);
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode loginAdmin() throws Exception {
        LoginRequest request = new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD);
        String response = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode refresh(String path, String token, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(token))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }
}
