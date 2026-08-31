package com.hiveapp.platform.security;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionLifecycleAction;
import com.hiveapp.platform.client.plan.dto.SubscriptionLifecycleModels;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SubscriptionLifecycleControlPlaneIntegrationTest
        extends PlatformShellIntegrationTestSupport {

    @Test
    void suspensionRevokesClientSessionsAndTerminalCancellationRemainsReadable()
            throws Exception {
        String email = "lifecycle-" + UUID.randomUUID() + "@example.com";
        String clientToken = register(email);
        UUID accountId = UUID.fromString(objectMapper.readTree(mockMvc.perform(
                                get("/api/v1/accounts/me")
                                        .header("Authorization", bearer(clientToken)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("id").asText());
        String adminToken = loginAdminAndGetToken();

        apply(adminToken, accountId, SubscriptionLifecycleAction.SUSPEND,
                "Security review", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/subscriptions/account/{accountId}", accountId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionCause").value("OPERATOR"))
                .andExpect(jsonPath("$.suspensionReason").value("Security review"))
                .andExpect(jsonPath("$.availableLifecycleActions", hasItems(
                        "RESTORE", "CANCEL_IMMEDIATELY")));

        apply(adminToken, accountId, SubscriptionLifecycleAction.RESTORE,
                "Review completed", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        // Restoration changes the entitlement state; it does not resurrect a bearer session.
        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isUnauthorized());
        String restoredClientToken = loginClient(email);
        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(restoredClientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        apply(adminToken, accountId, SubscriptionLifecycleAction.CANCEL_IMMEDIATELY,
                "Account closure requested", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/admin/subscriptions/account/{accountId}", accountId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.availableLifecycleActions").isEmpty());
        mockMvc.perform(get("/api/admin/subscriptions/account/{accountId}/lifecycle-history", accountId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", hasItems(
                        "SUSPEND", "RESTORE", "CANCEL_IMMEDIATELY")))
                .andExpect(jsonPath("$.content[0].actorEmail").value(ADMIN_EMAIL));
    }

    private org.springframework.test.web.servlet.ResultActions apply(
            String adminToken,
            UUID accountId,
            SubscriptionLifecycleAction action,
            String reason,
            java.time.Instant graceEndsAt) throws Exception {
        String preview = mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{accountId}/lifecycle/preview",
                                accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SubscriptionLifecycleModels.PreviewRequest(action, graceEndsAt))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(preview).get("previewToken").asText();
        return mockMvc.perform(post(
                        "/api/admin/subscriptions/account/{accountId}/lifecycle/{action}",
                        accountId, action)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SubscriptionLifecycleModels.ApplyRequest(token, reason, graceEndsAt))));
    }

    private String register(String email) throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, CLIENT_PASSWORD, "Lifecycle", "Owner", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private String loginClient(String email) throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, CLIENT_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }
}
