package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.InitialPasswordChangeRequest;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.PasswordCompletionRequest;
import com.hiveapp.identity.dto.PasswordResetRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.shared.email.EmailService;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberCredentialLifecycleIntegrationTest extends PlatformShellIntegrationTestSupport {

    private static final String MEMBER_PASSWORD = "member-password-123";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;

    @Test
    void temporaryPasswordIsShownOnceAndForcesARestrictedOneUsePasswordChange() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String username = unique("temp");
        String employeeNumber = unique("EMP");
        JsonNode created = createMember(ownerToken, username, null, employeeNumber);
        String temporaryPassword = created.get("temporaryPassword").asText();

        assertThat(temporaryPassword).isNotBlank();
        assertThat(created.get("credentialState").asText()).isEqualTo("TEMPORARY_PASSWORD");
        var storedUser = userRepository.findByUsername(username).orElseThrow();
        assertThat(storedUser.getPasswordHash()).isNotEqualTo(temporaryPassword);
        assertThat(passwordEncoder.matches(temporaryPassword, storedUser.getPasswordHash())).isTrue();

        String accountCode = currentAccountCode(ownerToken);
        LoginRequest employeeLogin = new LoginRequest(null, temporaryPassword, accountCode, employeeNumber);
        JsonNode restricted = login(employeeLogin, status().isOk());
        String restrictedToken = restricted.get("accessToken").asText();
        assertThat(restricted.get("passwordChangeRequired").asBoolean()).isTrue();
        assertThat(restricted.get("refreshToken").isNull()).isTrue();

        mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", bearer(restrictedToken)))
                .andExpect(status().isForbidden());
        login(employeeLogin, status().isUnauthorized());

        JsonNode activated = changeInitialPassword(restrictedToken, MEMBER_PASSWORD, status().isOk());
        assertThat(activated.get("passwordChangeRequired").asBoolean()).isFalse();
        assertThat(activated.get("refreshToken").asText()).isNotBlank();
        changeInitialPassword(restrictedToken, "different-password-123", status().isUnauthorized());

        login(new LoginRequest(username, MEMBER_PASSWORD), status().isOk());
    }

    @Test
    void managerCanRegenerateUnusedAccessAndResetActivatedAccess() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String username = unique("managed");
        JsonNode created = createMember(ownerToken, username, null, null);
        UUID memberId = UUID.fromString(created.get("member").get("id").asText());
        String firstTemporaryPassword = created.get("temporaryPassword").asText();

        mockMvc.perform(post("/api/v1/members/{id}/access/reset", memberId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict());

        JsonNode regenerated = responseJson(mockMvc.perform(post(
                                "/api/v1/members/{id}/access/regenerate", memberId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()));
        String regeneratedPassword = regenerated.get("temporaryPassword").asText();
        assertThat(regeneratedPassword).isNotEqualTo(firstTemporaryPassword);
        login(new LoginRequest(username, firstTemporaryPassword), status().isUnauthorized());

        JsonNode restricted = login(
                new LoginRequest(username, regeneratedPassword), status().isOk());
        JsonNode activated = changeInitialPassword(
                restricted.get("accessToken").asText(), MEMBER_PASSWORD, status().isOk());
        String oldAccessToken = activated.get("accessToken").asText();
        String oldRefreshToken = activated.get("refreshToken").asText();

        JsonNode reset = responseJson(mockMvc.perform(post(
                                "/api/v1/members/{id}/access/reset", memberId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()));
        String resetPassword = reset.get("temporaryPassword").asText();

        mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", bearer(oldAccessToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefreshToken))))
                .andExpect(status().isUnauthorized());
        login(new LoginRequest(username, MEMBER_PASSWORD), status().isUnauthorized());
        login(new LoginRequest(username, resetPassword), status().isOk());
    }

    @Test
    void repeatedTemporaryPasswordFailuresLockAccessUntilManagerUnlocksIt() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String username = unique("locked");
        JsonNode created = createMember(ownerToken, username, null, null);
        UUID memberId = UUID.fromString(created.get("member").get("id").asText());
        String temporaryPassword = created.get("temporaryPassword").asText();

        for (int attempt = 0; attempt < 5; attempt++) {
            login(new LoginRequest(username, "wrong-password"), status().isUnauthorized());
        }
        assertThat(userRepository.findByUsername(username).orElseThrow().isInitialAccessLocked()).isTrue();
        login(new LoginRequest(username, temporaryPassword), status().isUnauthorized());

        mockMvc.perform(post("/api/v1/members/{id}/access/unlock", memberId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findByUsername(username).orElseThrow().isInitialAccessLocked()).isFalse();
        login(new LoginRequest(username, temporaryPassword), status().isOk());
    }

    @Test
    void emailActivationLinkIsOneTimeAndCompletesVerifiedAccess() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String username = unique("email");
        String email = username + "@example.com";
        JsonNode created = createMember(ownerToken, username, email, null);

        assertThat(created.get("temporaryPassword").isNull()).isTrue();
        assertThat(created.get("credentialState").asText()).isEqualTo("EMAIL_ACTIVATION_PENDING");
        assertThat(created.get("activationLinkExpiresAt").asText()).isNotBlank();
        login(new LoginRequest(email, MEMBER_PASSWORD), status().isUnauthorized());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> expiryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(emailService).sendCredentialLink(
                eq(email), eq("Email Member"), anyString(), urlCaptor.capture(),
                eq(CredentialTokenPurpose.ACTIVATION), expiryCaptor.capture());
        assertThat(expiryCaptor.getValue()).isAfter(Instant.now());
        String actionUrl = urlCaptor.getValue();
        String rawToken = actionUrl.substring(actionUrl.indexOf("token=") + 6);
        var pendingUser = userRepository.findByUsername(username).orElseThrow();
        assertThat(pendingUser.getCredentialTokenHash()).isNotEqualTo(rawToken);

        PasswordCompletionRequest completion = new PasswordCompletionRequest(rawToken, MEMBER_PASSWORD);
        JsonNode activation = responseJson(mockMvc.perform(post("/api/v1/auth/activation/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false)));
        String activationRefreshToken = activation.get("refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/activation/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completion)))
                .andExpect(status().isConflict());

        var activatedUser = userRepository.findByUsername(username).orElseThrow();
        assertThat(activatedUser.getCredentialState()).isEqualTo(CredentialState.ACTIVE);
        assertThat(activatedUser.isEmailVerified()).isTrue();
        assertThat(activatedUser.getCredentialTokenHash()).isNull();

        clearInvocations(emailService);
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isNoContent());
        ArgumentCaptor<String> resetUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendCredentialLink(
                eq(email), eq("Email Member"), anyString(), resetUrlCaptor.capture(),
                eq(CredentialTokenPurpose.PASSWORD_RESET), org.mockito.ArgumentMatchers.any());
        String resetUrl = resetUrlCaptor.getValue();
        String resetToken = resetUrl.substring(resetUrl.indexOf("token=") + 6);
        String resetPassword = "reset-member-password-456";
        var resetCompletion = new PasswordCompletionRequest(resetToken, resetPassword);
        mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetCompletion)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetCompletion)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshTokenRequest(activationRefreshToken))))
                .andExpect(status().isUnauthorized());
        login(new LoginRequest(email, MEMBER_PASSWORD), status().isUnauthorized());
        login(new LoginRequest(email, resetPassword), status().isOk());
    }

    private JsonNode createMember(
            String ownerToken,
            String username,
            String email,
            String employeeNumber
    ) throws Exception {
        var request = new CreateMemberRequest(
                username, email, "Email", "Member", "Email Member",
                null, employeeNumber, List.of());
        return responseJson(mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
    }

    private String currentAccountCode(String token) throws Exception {
        JsonNode account = responseJson(mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        return account.get("slug").asText();
    }

    private JsonNode login(
            LoginRequest request,
            org.springframework.test.web.servlet.ResultMatcher statusMatcher
    ) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(statusMatcher);
        return responseJson(result);
    }

    private JsonNode changeInitialPassword(
            String token,
            String password,
            org.springframework.test.web.servlet.ResultMatcher statusMatcher
    ) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/initial-password/change")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitialPasswordChangeRequest(password))))
                .andExpect(statusMatcher);
        return responseJson(result);
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
