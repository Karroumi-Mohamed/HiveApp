package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.client.company.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.company.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.client.member.dto.OverridePermissionRequest;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope;
import com.hiveapp.identity.dto.InitialPasswordChangeRequest;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.platform.registry.definition.B2bFeature;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.HashSet;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberPermissionSecurityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionSnapshotReader subscriptionSnapshotReader;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void effectivePermissionResponseIdentifiesItsAccountAndCompanyContext() throws Exception {
        String token = registerClientAndGetToken();
        UUID accountId = currentAccountId(token);
        UUID companyId = UUID.fromString(createCompany(token, "Context Company").get("id").asText());

        mockMvc.perform(get("/api/v1/me/permissions")
                        .header("Authorization", bearer(token))
                        .header("X-Company-ID", companyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()));
    }

    @Test
    void memberPermissionOverrideCanBeGrantedAndReadWithinCurrentWorkspace() throws Exception {
        String token = registerClientAndGetToken();
        UUID creatorId = currentMemberId(token);
        UUID memberId = createOrdinaryMember(token);
        UUID companyId = UUID.fromString(createCompany(token, "Owner Company").get("id").asText());

        grantOverride(token, memberId, companyId, "platform.company.read_single")
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/members/{id}/permissions", memberId)
                        .header("Authorization", bearer(token))
                        .param("scope", PermissionOverrideScope.COMPANY.name())
                        .param("companyId", companyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].decision").value(PermissionOverrideDecision.GRANT.name()))
                .andExpect(jsonPath("$[0].scope").value(PermissionOverrideScope.COMPANY.name()))
                .andExpect(jsonPath("$[0].companyId").value(companyId.toString()))
                .andExpect(jsonPath("$[0].reason").value("Temporary operational access"))
                .andExpect(jsonPath("$[0].createdByMemberId").value(creatorId.toString()))
                .andExpect(jsonPath("$[0].effective").value(true))
                .andExpect(jsonPath("$[0].expiresAt").isNotEmpty())
                .andExpect(jsonPath("$[0].permissionCode").value("platform.company.read_single"));
    }

    @Test
    void accountGrantCascadesToCompanyButCompanyDenyWinsOnlyThere() throws Exception {
        String ownerToken = registerClientAndGetToken();
        AcceptedMemberTokens memberTokens = createAndActivateMember(ownerToken);
        UUID memberId = memberIdForNonOwner(ownerToken);
        UUID companyId = UUID.fromString(createCompany(ownerToken, "Scoped Company").get("id").asText());
        String permissionCode = "platform.company.delete";

        setOverride(ownerToken, memberId, PermissionOverrideScope.ACCOUNT, null,
                permissionCode, PermissionOverrideDecision.GRANT, Instant.now().plusSeconds(3600))
                .andExpect(status().isNoContent());
        setOverride(ownerToken, memberId, PermissionOverrideScope.COMPANY, companyId,
                permissionCode, PermissionOverrideDecision.DENY, null)
                .andExpect(status().isNoContent());

        assertThat(mePermissions(memberTokens.accessToken())).contains(permissionCode);
        assertThat(mePermissions(memberTokens.accessToken(), companyId)).doesNotContain(permissionCode);
    }

    @Test
    void companyGrantAppliesOnlyToItsExactCompany() throws Exception {
        String ownerToken = registerClientAndGetToken();
        AcceptedMemberTokens memberTokens = createAndActivateMember(ownerToken);
        UUID memberId = memberIdForNonOwner(ownerToken);
        UUID firstCompanyId = UUID.fromString(createCompany(ownerToken, "First Company").get("id").asText());
        UUID secondCompanyId = createCompanyWithoutQuota(ownerToken, "Second Company");
        String permissionCode = "platform.company.delete";

        setOverride(ownerToken, memberId, PermissionOverrideScope.COMPANY, firstCompanyId,
                permissionCode, PermissionOverrideDecision.GRANT, Instant.now().plusSeconds(3600))
                .andExpect(status().isNoContent());

        assertThat(mePermissions(memberTokens.accessToken(), firstCompanyId)).contains(permissionCode);
        assertThat(mePermissions(memberTokens.accessToken(), secondCompanyId)).doesNotContain(permissionCode);
    }

    @Test
    void grantExceptionRequiresExpiryWhileDenyMayBePermanent() throws Exception {
        String ownerToken = registerClientAndGetToken();
        UUID memberId = createOrdinaryMember(ownerToken);

        setOverride(ownerToken, memberId, PermissionOverrideScope.ACCOUNT, null,
                "platform.company.delete", PermissionOverrideDecision.GRANT, null)
                .andExpect(status().isConflict());
        setOverride(ownerToken, memberId, PermissionOverrideScope.ACCOUNT, null,
                "platform.company.delete", PermissionOverrideDecision.DENY, null)
                .andExpect(status().isNoContent());
    }

    @Test
    void mePermissionsDoNotExposePermissionsDeniedByCurrentPlanEntitlement() throws Exception {
        String token = registerClientAndGetToken();
        removeFeatureFromActiveSubscription(token, B2bFeature.CODE);

        Set<String> permissions = mePermissions(token);

        assertThat(permissions).doesNotContain("platform.b2b.request");
        assertThat(permissions).contains(CompanyFeature.CODE + "." + CompanyFeature.READ_SINGLE);
    }

    @Test
    void memberPermissionOverrideRejectsPlatformControlPermission() throws Exception {
        String token = registerClientAndGetToken();
        UUID memberId = createOrdinaryMember(token);
        UUID companyId = UUID.fromString(createCompany(token, "Owner Company").get("id").asText());

        grantOverride(token, memberId, companyId, "platform.plans.create")
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberPermissionOverrideRejectsMemberFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerMemberId = currentMemberId(ownerToken);
        UUID otherCompanyId = UUID.fromString(createCompany(otherToken, "Other Company").get("id").asText());

        grantOverride(otherToken, ownerMemberId, otherCompanyId, "platform.company.read_single")
                .andExpect(status().isNotFound());
    }

    @Test
    void memberPermissionOverrideRejectsCompanyFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());
        UUID otherMemberId = currentMemberId(otherToken);

        grantOverride(otherToken, otherMemberId, ownerCompanyId, "platform.company.read_single")
                .andExpect(status().isNotFound());
    }

    @Test
    void memberCannotReadOrRevokeOverridesUsingAnotherWorkspaceCompany() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());
        UUID otherMemberId = currentMemberId(otherToken);

        mockMvc.perform(get("/api/v1/members/{id}/permissions", otherMemberId)
                        .header("Authorization", bearer(otherToken))
                .param("scope", PermissionOverrideScope.COMPANY.name())
                .param("companyId", ownerCompanyId.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/members/{id}/permissions/{permissionCode}",
                        otherMemberId, "platform.company.read_single")
                        .header("Authorization", bearer(otherToken))
                        .param("scope", PermissionOverrideScope.COMPANY.name())
                        .param("companyId", ownerCompanyId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void workspaceOwnerCannotDeactivateSelf() throws Exception {
        String token = registerClientAndGetToken();
        UUID memberId = currentMemberId(token);

        mockMvc.perform(post("/api/v1/members/{id}/deactivate", memberId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Owner protection test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedMemberCannotContinueUsingExistingAccessToken() throws Exception {
        String ownerToken = registerClientAndGetToken();
        AcceptedMemberTokens memberTokens = createAndActivateMember(ownerToken);
        UUID createdMemberId = memberIdForNonOwner(ownerToken);

        mockMvc.perform(post("/api/v1/members/{id}/deactivate", createdMemberId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Security revocation test\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", bearer(memberTokens.accessToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshTokenRequest(memberTokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions grantOverride(
            String token, UUID memberId, UUID companyId, String permissionCode) throws Exception {
        return setOverride(token, memberId, PermissionOverrideScope.COMPANY, companyId,
                permissionCode, PermissionOverrideDecision.GRANT, Instant.now().plusSeconds(3600));
    }

    private org.springframework.test.web.servlet.ResultActions setOverride(
            String token,
            UUID memberId,
            PermissionOverrideScope scope,
            UUID companyId,
            String permissionCode,
            PermissionOverrideDecision decision,
            Instant expiresAt) throws Exception {
        OverridePermissionRequest request = new OverridePermissionRequest(
                permissionCode, scope, companyId, decision,
                "Temporary operational access", expiresAt);
        return mockMvc.perform(post("/api/v1/members/{id}/permissions", memberId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
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

    private UUID createCompanyWithoutQuota(String token, String name) throws Exception {
        Company company = new Company();
        company.setAccount(accountRepository.findById(currentAccountId(token)).orElseThrow());
        company.setName(name);
        company.setLegalName(name + " LLC");
        company.setIndustry("Software");
        company.setCountry("US");
        company.setActive(true);
        return companyRepository.saveAndFlush(company).getId();
    }

    private Set<String> mePermissions(String token) throws Exception {
        return mePermissions(token, null);
    }

    private Set<String> mePermissions(String token, UUID companyId) throws Exception {
        var request = get("/api/v1/me/permissions")
                .header("Authorization", bearer(token));
        if (companyId != null) {
            request.header("X-Company-ID", companyId.toString());
        }
        String response = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Set<String> permissions = new HashSet<>();
        for (JsonNode permission : objectMapper.readTree(response).get("permissions")) {
            permissions.add(permission.asText());
        }
        return permissions;
    }

    private void removeFeatureFromActiveSubscription(String token, String featureCode) throws Exception {
        var subscription = subscriptionRepository.findActiveForSnapshotUpdateByAccountId(
                currentAccountId(token)).orElseThrow();
        var snapshot = subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()).orElseThrow();
        var updated = new SubscriptionEntitlementSnapshot(
                snapshot.planCode(),
                snapshot.basePrice(),
                snapshot.currencyCode(),
                snapshot.billingCycle(),
                snapshot.features().stream()
                        .filter(feature -> !featureCode.equals(feature.featureCode()))
                        .toList(),
                snapshot.addOns());
        subscription.setEntitlementSnapshot(subscriptionSnapshotReader.write(updated));
        subscriptionRepository.saveAndFlush(subscription);
    }

    private AcceptedMemberTokens createAndActivateMember(String ownerToken) throws Exception {
        String username = "member-" + UUID.randomUUID().toString().substring(0, 8);
        CreateMemberRequest request = new CreateMemberRequest(
                username, null, "Created", "Member", "Created Member", null, null, java.util.List.of());
        String response = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = objectMapper.readTree(response).get("temporaryPassword").asText();

        String restricted = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, temporaryPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String restrictedToken = objectMapper.readTree(restricted).get("accessToken").asText();

        String activated = mockMvc.perform(post("/api/v1/auth/initial-password/change")
                        .header("Authorization", bearer(restrictedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitialPasswordChangeRequest(CLIENT_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tokens = objectMapper.readTree(activated);
        return new AcceptedMemberTokens(
                tokens.get("accessToken").asText(),
                tokens.get("refreshToken").asText());
    }

    private UUID memberIdForNonOwner(String ownerToken) throws Exception {
        JsonNode members = listMembers(ownerToken);
        for (JsonNode member : members) {
            if (!member.get("isOwner").asBoolean()) {
                return UUID.fromString(member.get("id").asText());
            }
        }
        throw new AssertionError("Expected a created non-owner member");
    }

    private record AcceptedMemberTokens(String accessToken, String refreshToken) {
    }
}
