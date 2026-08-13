package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.constant.IdentityKind;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.InitialPasswordChangeRequest;
import com.hiveapp.identity.dto.PasswordCompletionRequest;
import com.hiveapp.identity.dto.PasswordResetRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.admin.dto.CreateAdminUserRequest;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.shared.email.EmailDispatchOutcome;
import com.hiveapp.shared.email.EmailService;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void successfulEmailTransportByDefault() {
        doReturn(EmailDispatchOutcome.SENT).when(emailService).sendCredentialLink(
                anyString(), anyString(), anyString(), anyString(),
                any(CredentialTokenPurpose.class), any(Instant.class));
    }

    @Test
    void creatingAnOperatorCreatesAPlatformIdentityAndItsGrantTogether() throws Exception {
        String token = loginAdminAndGetToken();
        String email = operatorEmail();

        JsonNode response = json(createOperator(token, email, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operator.email").value(email))
                .andExpect(jsonPath("$.initialAccessMethod").value("EMAIL_LINK"))
                // The activation link is the credential; no password is minted alongside it.
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.linkExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.credentialState").value("EMAIL_ACTIVATION_PENDING")));

        UUID operatorUserId = UUID.fromString(response.get("operator").get("userId").asText());
        assertThat(userRepository.findById(operatorUserId).orElseThrow().getKind())
                .isEqualTo(IdentityKind.PLATFORM);
        assertThat(adminUserRepository.findByUserId(operatorUserId)).isPresent();
    }

    @Test
    void creationCanExplicitlyIssueTemporaryAccessWithoutEmailingOrVerifyingTheAddress() throws Exception {
        String token = loginAdminAndGetToken();
        String email = operatorEmail();

        JsonNode response = json(createOperator(
                        token, email, false, InitialAccessMethod.TEMPORARY_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.initialAccessMethod").value("TEMPORARY_PASSWORD"))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.linkExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.credentialState").value("TEMPORARY_PASSWORD")));

        verify(emailService, never()).sendCredentialLink(
                anyString(), anyString(), anyString(), anyString(), any(), any());
        User operator = userRepository.findByEmail(email).orElseThrow();
        assertThat(operator.isEmailVerified()).isFalse();

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, response.get("temporaryPassword").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
    }

    @Test
    void operatorActivationCompletesOnceWithoutIssuingASessionAndEnablesNormalLogin() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String email = operatorEmail();
        String password = "activated-operator-password";
        createOperator(adminToken, email, false).andExpect(status().isCreated());
        String activationToken = capturedCredentialToken(email, CredentialTokenPurpose.ACTIVATION);
        PasswordCompletionRequest completion = new PasswordCompletionRequest(activationToken, password);

        mockMvc.perform(post("/api/admin/auth/activation/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completion)))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
        mockMvc.perform(post("/api/admin/auth/activation/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completion)))
                .andExpect(status().isConflict());

        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
    }

    @Test
    void verifiedOperatorCanCompleteAOneTimePasswordReset() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String email = operatorEmail();
        String originalPassword = "original-operator-password";
        createOperator(adminToken, email, false).andExpect(status().isCreated());
        PasswordCompletionRequest activation = new PasswordCompletionRequest(
                capturedCredentialToken(email, CredentialTokenPurpose.ACTIVATION), originalPassword);
        mockMvc.perform(post("/api/admin/auth/activation/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activation)))
                .andExpect(status().isNoContent());

        clearInvocations(emailService);
        mockMvc.perform(post("/api/admin/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isNoContent());
        String resetToken = capturedCredentialToken(email, CredentialTokenPurpose.PASSWORD_RESET);
        String replacementPassword = "replacement-operator-password";
        PasswordCompletionRequest reset = new PasswordCompletionRequest(resetToken, replacementPassword);
        mockMvc.perform(post("/api/admin/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reset)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reset)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, originalPassword))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, replacementPassword))))
                .andExpect(status().isOk());
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

    /**
     * A handed-over password must not operate the platform. It buys exactly one restricted
     * session whose only purpose is choosing a real password.
     */
    @Test
    void aTemporaryPasswordGrantsOnlyARestrictedSessionUntilItIsChanged() throws Exception {
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

        JsonNode login = json(mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, temporaryPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true)));
        String restricted = login.get("accessToken").asText();

        // The restricted session cannot operate the platform. It is an ADMIN-audience token, but
        // its use is INITIAL_ACCESS rather than ACCESS, so the filter authenticates nothing and
        // denies it everywhere except the two endpoints that finish or abandon the change.
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(restricted)))
                .andExpect(status().isForbidden());

        // The temporary password is consumed: it cannot be replayed.
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, temporaryPassword))))
                .andExpect(status().isUnauthorized());

        String chosen = json(mockMvc.perform(post("/api/admin/auth/initial-password/change")
                        .header("Authorization", bearer(restricted))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitialPasswordChangeRequest("chosen-password-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false)))
                .get("accessToken").asText();

        mockMvc.perform(get("/api/admin/me").header("Authorization", bearer(chosen)))
                .andExpect(status().isOk());

        // Handing over a password proves nothing about the mailbox, so it stays unverified.
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();
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
        String restricted = json(mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, temporaryPassword))))
                        .andExpect(status().isOk()))
                .get("accessToken").asText();
        // A temporary password buys only the change itself, so the operator chooses their own
        // password before holding anything that can read the portal.
        String operatorToken = json(mockMvc.perform(post("/api/admin/auth/initial-password/change")
                        .header("Authorization", bearer(restricted))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitialPasswordChangeRequest("chosen-password-2"))))
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

    /**
     * A restricted token belongs to one surface. Presenting it to the other must be rejected
     * *without consuming it* — validating after removal would let a misdirected request destroy
     * a pending change the operator is in the middle of.
     */
    @Test
    void anAdminRestrictedTokenIsRejectedByTheClientEndpointWithoutBeingConsumed() throws Exception {
        String restricted = restrictedOperatorSession(operatorEmail());

        mockMvc.perform(post("/api/v1/auth/initial-password/change")
                        .header("Authorization", bearer(restricted))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitialPasswordChangeRequest("chosen-password-3"))))
                .andExpect(status().isUnauthorized());

        // Still usable on its own surface, which proves the rejection did not burn it.
        mockMvc.perform(post("/api/admin/auth/initial-password/change")
                        .header("Authorization", bearer(restricted))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitialPasswordChangeRequest("chosen-password-3"))))
                .andExpect(status().isOk());
    }

    @Test
    void loggingOutConsumesTheRestrictedSession() throws Exception {
        String restricted = restrictedOperatorSession(operatorEmail());

        mockMvc.perform(post("/api/admin/auth/initial-password/logout")
                        .header("Authorization", bearer(restricted)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/auth/initial-password/change")
                        .header("Authorization", bearer(restricted))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitialPasswordChangeRequest("chosen-password-4"))))
                .andExpect(status().isUnauthorized());
    }

    /** The reverse direction: a client restricted token must not be spendable on the admin side. */
    @Test
    void aClientRestrictedTokenIsRejectedByTheAdminEndpointWithoutBeingConsumed() throws Exception {
        String clientRestricted = restrictedClientSession();

        mockMvc.perform(post("/api/admin/auth/initial-password/change")
                        .header("Authorization", bearer(clientRestricted))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitialPasswordChangeRequest("chosen-password-5"))))
                // 401, symmetric with the other direction: the audience is checked before the
                // session is removed, so the credential is refused rather than spent.
                .andExpect(status().isUnauthorized());

        // Still usable on its own surface, which proves the rejection did not burn it.
        mockMvc.perform(post("/api/v1/auth/initial-password/change")
                        .header("Authorization", bearer(clientRestricted))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitialPasswordChangeRequest("chosen-password-5"))))
                .andExpect(status().isOk());
    }

    /**
     * A member created without an email gets a temporary password, and using it yields a
     * CLIENT-audience restricted session — the counterpart to the operator one.
     */
    private String restrictedClientSession() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String username = "member-" + UUID.randomUUID();
        String employeeNumber = "EMP-" + UUID.randomUUID().toString().substring(0, 8);
        String temporaryPassword = json(mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMemberRequest(
                                username, null, "Temp", "Member", "Temp Member",
                                null, employeeNumber, java.util.List.of()))))
                        .andExpect(status().isCreated()))
                .get("temporaryPassword").asText();
        String accountCode = json(mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(ownerToken)))
                        .andExpect(status().isOk()))
                .get("slug").asText();

        return json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(null, temporaryPassword, accountCode, employeeNumber))))
                        .andExpect(status().isOk()))
                .get("accessToken").asText();
    }

    /** Creates an operator, hands it temporary access, and returns its restricted session. */
    private String restrictedOperatorSession(String email) throws Exception {
        String token = loginAdminAndGetToken();
        UUID adminUserId = UUID.fromString(json(createOperator(token, email, false)
                .andExpect(status().isCreated()))
                .get("operator").get("id").asText());
        String temporaryPassword = json(mockMvc.perform(
                        post("/api/admin/users/{id}/access/temporary", adminUserId)
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk()))
                .get("temporaryPassword").asText();
        return json(mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, temporaryPassword))))
                        .andExpect(status().isOk()))
                .get("accessToken").asText();
    }

    private ResultActions createOperator(String token, String email, boolean superAdmin) throws Exception {
        return createOperator(token, email, superAdmin, InitialAccessMethod.EMAIL_LINK);
    }

    private ResultActions createOperator(
            String token,
            String email,
            boolean superAdmin,
            InitialAccessMethod initialAccessMethod
    ) throws Exception {
        return mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateAdminUserRequest(
                                "Op", "Erator", email, initialAccessMethod, superAdmin))));
    }

    private String capturedCredentialToken(String email, CredentialTokenPurpose purpose) {
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendCredentialLink(
                eq(email), eq("Op Erator"), anyString(), url.capture(), eq(purpose), any(Instant.class));
        String actionUrl = url.getValue();
        return actionUrl.substring(actionUrl.indexOf("token=") + 6);
    }

    private JsonNode json(ResultActions action) throws Exception {
        return objectMapper.readTree(action.andReturn().getResponse().getContentAsString());
    }

    private static String operatorEmail() {
        return "op-" + UUID.randomUUID() + "@hiveapp.test";
    }
}
