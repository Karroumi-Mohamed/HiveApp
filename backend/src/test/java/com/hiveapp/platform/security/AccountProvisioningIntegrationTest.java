package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.service.WorkspaceProvisioningService;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountProvisioningIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PlanRepository planRepository;
    @Autowired WorkspaceProvisioningService workspaceProvisioningService;

    @Test
    void everySuccessfulRegistrationHasOneUsableSubscriptionSnapshot() throws Exception {
        String email = "entitled-" + UUID.randomUUID() + "@example.com";
        JsonNode registration = register(email);
        var user = userRepository.findByEmail(email).orElseThrow();
        var account = accountRepository.findByOwner_Id(user.getId()).orElseThrow();

        var usable = subscriptionRepository.findAllByAccountIdAndStatusIn(
                account.getId(), List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING));
        assertThat(usable).hasSize(1);
        assertThat(usable.getFirst().getEntitlementSnapshot()).isNotBlank().contains("FREE");

        mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(registration.get("accessToken").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId().toString()))
                .andExpect(jsonPath("$.slug").value(account.getSlug()));
    }

    @Test
    void unavailableFreePlanRollsBackTheRegistration() throws Exception {
        String email = "no-free-" + UUID.randomUUID() + "@example.com";
        var freePlan = planRepository.findByCode("FREE").orElseThrow();
        freePlan.setActive(false);
        planRepository.saveAndFlush(freePlan);

        try {
            RegisterRequest request = new RegisterRequest(email, CLIENT_PASSWORD, "No", "Plan", null);
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "Workspace registration is unavailable because the required FREE plan is inactive."));

            assertThat(userRepository.findByEmail(email)).isEmpty();
        } finally {
            freePlan.setActive(true);
            planRepository.saveAndFlush(freePlan);
        }
    }

    @Test
    void sameEmailPrefixProducesDistinctUserBoundSlugs() throws Exception {
        String localPart = "same-prefix-" + UUID.randomUUID();
        register(localPart + "@first.example");
        register(localPart + "@second.example");

        var firstUser = userRepository.findByEmail(localPart + "@first.example").orElseThrow();
        var secondUser = userRepository.findByEmail(localPart + "@second.example").orElseThrow();
        var first = accountRepository.findByOwner_Id(firstUser.getId()).orElseThrow();
        var second = accountRepository.findByOwner_Id(secondUser.getId()).orElseThrow();

        assertThat(first.getSlug()).isNotEqualTo(second.getSlug());
        assertThat(first.getSlug()).endsWith(firstUser.getId().toString().replace("-", ""));
        assertThat(second.getSlug()).endsWith(secondUser.getId().toString().replace("-", ""));
    }

    @Test
    void repeatedProvisioningReturnsExistingCompleteWorkspace() throws Exception {
        String email = "retry-" + UUID.randomUUID() + "@example.com";
        register(email);
        var user = userRepository.findByEmail(email).orElseThrow();
        var account = accountRepository.findByOwner_Id(user.getId()).orElseThrow();

        var result = workspaceProvisioningService.provision(user.getId(), email);

        assertThat(result.created()).isFalse();
        assertThat(result.accountId()).isEqualTo(account.getId());
        assertThat(memberRepository.findAllByAccountId(account.getId())).hasSize(1);
        assertThat(subscriptionRepository.findAllByAccountIdAndStatusIn(
                account.getId(), List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .hasSize(1);
    }

    @Test
    void deactivationBlocksAccessAndRevokesRefreshSession() throws Exception {
        JsonNode registration = register("deactivate-" + UUID.randomUUID() + "@example.com");
        String accessToken = registration.get("accessToken").asText();
        String refreshToken = registration.get("refreshToken").asText();

        mockMvc.perform(delete("/api/v1/accounts/me")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied: Workspace account is suspended"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode register(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(email, CLIENT_PASSWORD, "Account", "Owner", null);
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
