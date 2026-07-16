package com.hiveapp.platform.security;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.client.company.dto.CreateCompanyRequest;
import com.hiveapp.platform.client.member.dto.AddMemberRequest;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.shared.quota.QuotaOverride;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotaEnforcementIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void freePlanAllowsOnlyOneCompany() throws Exception {
        String token = registerClientAndGetToken();
        createCompany(token, "Included Company");
        CreateCompanyRequest request = new CreateCompanyRequest(
                "Over Limit", "Over Limit LLC", null, "Software", "US", null);

        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("Quota Exceeded"))
                .andExpect(jsonPath("$.details[0]").value("resource: companies"))
                .andExpect(jsonPath("$.details[1]").value("limit: 1"))
                .andExpect(jsonPath("$.details[2]").value("current: 1"));
    }

    @Test
    void freePlanMemberLimitCountsOwnerAndDeniesThirdAddition() throws Exception {
        String ownerToken = registerClientAndGetToken();
        UUID firstUserId = createUnattachedUser("First");
        UUID secondUserId = createUnattachedUser("Second");
        UUID thirdUserId = createUnattachedUser("Third");

        addMember(ownerToken, firstUserId, "First Member").andExpect(status().isCreated());
        addMember(ownerToken, secondUserId, "Second Member").andExpect(status().isCreated());

        addMember(ownerToken, thirdUserId, "Over Limit")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("Quota Exceeded"))
                .andExpect(jsonPath("$.details[0]").value("resource: members"))
                .andExpect(jsonPath("$.details[1]").value("limit: 3"))
                .andExpect(jsonPath("$.details[2]").value("current: 3"));
    }

    @Test
    void deactivatedMemberStopsConsumingActiveMemberQuota() throws Exception {
        String ownerToken = registerClientAndGetToken();
        UUID firstUserId = createUnattachedUser("First");
        UUID secondUserId = createUnattachedUser("Second");
        UUID replacementUserId = createUnattachedUser("Replacement");

        addMember(ownerToken, firstUserId, "First Member").andExpect(status().isCreated());
        addMember(ownerToken, secondUserId, "Second Member").andExpect(status().isCreated());
        UUID firstMemberId = memberRepository.findByUserIdAndIsActiveTrue(firstUserId).orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/members/{id}", firstMemberId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());

        addMember(ownerToken, replacementUserId, "Replacement Member")
                .andExpect(status().isCreated());
    }

    @Test
    void inactiveCompanyStopsConsumingActiveCompanyQuota() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Inactive Company").get("id").asText());

        mockMvc.perform(delete("/api/v1/companies/{id}", companyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        createCompanyRequest(token, "Replacement Company")
                .andExpect(status().isCreated());
    }

    @Test
    void concurrentCompanyCreationCannotExceedExactFreePlanBoundary() throws Exception {
        String token = registerClientAndGetToken();

        CompletableFuture<Integer> first = createCompanyAsync(token, "Concurrent Company A");
        CompletableFuture<Integer> second = createCompanyAsync(token, "Concurrent Company B");

        assertThat(List.of(first.join(), second.join()))
                .containsExactlyInAnyOrder(201, 402);
    }

    @Test
    void concurrentMemberCreationCannotExceedExactFreePlanBoundary() throws Exception {
        String token = registerClientAndGetToken();
        addMember(token, createUnattachedUser("Existing"), "Existing Member")
                .andExpect(status().isCreated());
        UUID firstUserId = createUnattachedUser("Concurrent First");
        UUID secondUserId = createUnattachedUser("Concurrent Second");

        CompletableFuture<Integer> first = addMemberAsync(token, firstUserId, "Concurrent First");
        CompletableFuture<Integer> second = addMemberAsync(token, secondUserId, "Concurrent Second");

        assertThat(List.of(first.join(), second.join()))
                .containsExactlyInAnyOrder(201, 402);
    }

    @Test
    void concurrentAdditionOfSameUserCreatesOnlyOneWorkspaceMembership() throws Exception {
        String token = registerClientAndGetToken();
        UUID userId = createUnattachedUser("Concurrent Duplicate");

        CompletableFuture<Integer> first = addMemberAsync(token, userId, "Concurrent Duplicate");
        CompletableFuture<Integer> second = addMemberAsync(token, userId, "Concurrent Duplicate");

        assertThat(List.of(first.join(), second.join()))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(memberRepository.findAll().stream()
                .filter(member -> member.getUser().getId().equals(userId)))
                .hasSize(1);
    }

    @Test
    void proPlanAssignmentReplacesFreeAndAllowsFiveCompanies() throws Exception {
        String token = registerClientAndGetToken();
        assignPlan(token, "PRO");

        for (int index = 1; index <= 5; index++) {
            createCompany(token, "Pro Company " + index);
        }

        createCompanyRequest(token, "Pro Over Limit")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.details[0]").value("resource: companies"))
                .andExpect(jsonPath("$.details[1]").value("limit: 5"))
                .andExpect(jsonPath("$.details[2]").value("current: 5"));
    }

    @Test
    void enterprisePlanAssignmentAllowsCompaniesBeyondFinitePlans() throws Exception {
        String token = registerClientAndGetToken();
        assignPlan(token, "ENTERPRISE");

        for (int index = 1; index <= 6; index++) {
            createCompany(token, "Enterprise Company " + index);
        }
    }

    @Test
    void proCompanyQuotaOverrideIsPricedAndEnforcedAtTheRaisedLimit() throws Exception {
        String token = registerClientAndGetToken();
        assignPlan(token, "PRO");
        applyCompanyQuotaOverride(token, 6L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPrice").value(34.99));

        for (int index = 1; index <= 6; index++) {
            createCompany(token, "Overridden Company " + index);
        }

        createCompanyRequest(token, "Override Over Limit")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.details[0]").value("resource: companies"))
                .andExpect(jsonPath("$.details[1]").value("limit: 6"))
                .andExpect(jsonPath("$.details[2]").value("current: 6"));
    }

    private UUID createUnattachedUser(String firstName) {
        User user = User.builder()
                .email("quota-user-" + UUID.randomUUID() + "@example.com")
                .passwordHash("not-used")
                .firstName(firstName)
                .lastName("Member")
                .build();
        return userRepository.save(user).getId();
    }

    private org.springframework.test.web.servlet.ResultActions addMember(
            String token, UUID userId, String displayName) throws Exception {
        AddMemberRequest request = new AddMemberRequest(userId, displayName);
        return mockMvc.perform(post("/api/v1/members")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private CompletableFuture<Integer> addMemberAsync(String token, UUID userId, String displayName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return addMember(token, userId, displayName)
                        .andReturn().getResponse().getStatus();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private CompletableFuture<Integer> createCompanyAsync(String token, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createCompanyRequest(token, name)
                        .andReturn().getResponse().getStatus();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private void assignPlan(String clientToken, String planCode) throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}", accountId)
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

    private org.springframework.test.web.servlet.ResultActions applyCompanyQuotaOverride(
            String clientToken, long limit) throws Exception {
        UpdateSubscriptionOverridesRequest request = new UpdateSubscriptionOverridesRequest(
                java.util.Set.of(),
                java.util.List.of(new QuotaOverride(WorkspaceFeature.CODE, WorkspaceFeature.COMPANIES, limit))
        );
        return mockMvc.perform(patch("/api/admin/subscriptions/account/{accountId}/overrides", currentAccountId(clientToken))
                .header("Authorization", bearer(loginAdminAndGetToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions createCompanyRequest(
            String token, String name) throws Exception {
        CreateCompanyRequest request = new CreateCompanyRequest(
                name, name + " LLC", null, "Software", "US", null);
        return mockMvc.perform(post("/api/v1/companies")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
