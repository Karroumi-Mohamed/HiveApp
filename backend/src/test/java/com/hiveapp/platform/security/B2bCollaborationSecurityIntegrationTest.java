package com.hiveapp.platform.security;

import com.hiveapp.platform.client.collaboration.dto.B2BPermissionRequest;
import com.hiveapp.platform.client.collaboration.dto.CollaborationCommandRequest;
import com.hiveapp.platform.client.collaboration.dto.InitiateCollaborationRequest;
import com.hiveapp.platform.client.collaboration.dto.ShareCodeRequest;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.SuspensionScheduleAction;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class B2bCollaborationSecurityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionSnapshotReader subscriptionSnapshotReader;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private CollaborationRepository collaborationRepository;

    @Test
    void lifecycleAndDiscoveryActionsArePresentInThePermissionizerRegistry() {
        assertThat(Set.of(
                "platform.b2b.read_detail",
                "platform.b2b.reject",
                "platform.b2b.cancel_request",
                "platform.b2b.suspend",
                "platform.b2b.resume",
                "platform.b2b.read_permissions",
                "platform.b2b.regenerate_share_code",
                "platform.b2b.read_share_code",
                "platform.b2b.manage_share_code",
                "platform.b2b.resolve_share_code"))
                .allMatch(code -> permissionRepository.findByCode(code).isPresent());
    }

    @Test
    void clientCannotRequestCollaborationWithOwnCompany() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Own Company").get("id").asText());
        String shareCode = regenerateShareCode(token, companyId);

        mockMvc.perform(post("/api/v1/collaborations/initiate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InitiateCollaborationRequest(
                                shareCode, "Attempt own collaboration", Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyProviderCanAcceptIncomingCollaboration() throws Exception {
        B2bSetup setup = setupPendingCollaboration();

        mockMvc.perform(patch("/api/v1/collaborations/{id}/accept", setup.collaborationId())
                        .header("Authorization", bearer(setup.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), null, null, null))))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/collaborations/{id}/accept", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), null, null, null))))
                .andExpect(status().isOk());
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
    void clientPlanWithoutB2bCannotInitiateCollaboration() throws Exception {
        String providerToken = registerClientAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(providerToken, "Provider Company").get("id").asText());
        String shareCode = regenerateShareCode(providerToken, companyId);
        SubscriptionEntitlementSnapshot originalSnapshot = removeFeatureFromActiveSubscription(clientToken, "platform.b2b");

        try {
            mockMvc.perform(post("/api/v1/collaborations/initiate")
                            .header("Authorization", bearer(clientToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new InitiateCollaborationRequest(
                                    shareCode, "Need provider support", Set.of()))))
                    .andExpect(status().isForbidden());
        } finally {
            restoreActiveSubscriptionSnapshot(clientToken, originalSnapshot);
        }
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
    void b2bPermissionCatalogOnlyShowsExplicitDelegatableActions() throws Exception {
        B2bSetup setup = setupActiveCollaboration();

        mockMvc.perform(get("/api/v1/collaborations/{id}/permission-catalog", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableChoices[*].features[*].permissions[*].code",
                        hasItem("platform.company.read_single")))
                .andExpect(jsonPath("$[*].features[*].permissions[*].code",
                        everyItem(not("platform.company.create"))))
                .andExpect(jsonPath("$[*].features[*].permissions[*].code",
                        everyItem(not("platform.company.read_all"))))
                .andExpect(jsonPath("$[*].features[*].permissions[*].code",
                        everyItem(not("platform.company.update"))))
                .andExpect(jsonPath("$[*].features[*].permissions[*].code",
                        everyItem(not("platform.company.delete"))))
                .andExpect(jsonPath("$[*].features[*].permissions[*].code",
                        everyItem(not("platform.plans.create"))));
    }

    @Test
    void clientCannotViewProviderB2bPermissionCatalog() throws Exception {
        B2bSetup setup = setupActiveCollaboration();

        mockMvc.perform(get("/api/v1/collaborations/{id}/permission-catalog", setup.collaborationId())
                        .header("Authorization", bearer(setup.clientToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void providerPlanDenialHidesAndRejectsB2bDelegationPermission() throws Exception {
        B2bSetup setup = setupActiveCollaboration();
        SubscriptionEntitlementSnapshot originalSnapshot = removeFeatureFromActiveSubscription(setup.providerToken(), "platform.company");

        try {
            mockMvc.perform(get("/api/v1/collaborations/{id}/permission-catalog", setup.collaborationId())
                            .header("Authorization", bearer(setup.providerToken())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("platform.company.read_single"))));

            grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Permission platform.company.read_single is not available in the provider's current plan entitlement."));
        } finally {
            restoreActiveSubscriptionSnapshot(setup.providerToken(), originalSnapshot);
        }
    }

    @Test
    void clientPlanWithoutB2bStillAllowsProviderGrantedDelegatedAccess() throws Exception {
        B2bSetup setup = setupActiveCollaboration();
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNoContent());
        SubscriptionEntitlementSnapshot originalSnapshot = removeFeatureFromActiveSubscription(setup.clientToken(), "platform.b2b");

        try {
            b2bCompanyRead(setup)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(setup.companyId().toString()));
        } finally {
            restoreActiveSubscriptionSnapshot(setup.clientToken(), originalSnapshot);
        }
    }

    @Test
    void existingB2bDelegatedAccessStopsWhenProviderLosesFeatureEntitlement() throws Exception {
        B2bSetup setup = setupActiveCollaboration();
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNoContent());
        SubscriptionEntitlementSnapshot originalSnapshot = removeFeatureFromActiveSubscription(setup.providerToken(), "platform.company");

        try {
            b2bCompanyRead(setup)
                    .andExpect(status().isForbidden());
        } finally {
            restoreActiveSubscriptionSnapshot(setup.providerToken(), originalSnapshot);
        }
    }

    @Test
    void pendingCollaborationCannotExposeB2bPermissionCatalog() throws Exception {
        B2bSetup setup = setupPendingCollaboration();

        mockMvc.perform(get("/api/v1/collaborations/{id}/permission-catalog", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken())))
                .andExpect(status().isConflict());
    }

    @Test
    void clientCannotGrantPermissionsToCollaboration() throws Exception {
        B2bSetup setup = setupActiveCollaboration();

        grantPermission(setup.clientToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNotFound());
    }

    @Test
    void activeDelegatedPermissionAllowsOnlyThenDeniesAfterRevocation() throws Exception {
        B2bSetup setup = setupActiveCollaboration();
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNoContent());

        b2bCompanyRead(setup)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(setup.companyId().toString()));

        mockMvc.perform(delete("/api/v1/collaborations/{id}/permissions/{permissionCode}",
                        setup.collaborationId(), "platform.company.read_single")
                        .header("Authorization", bearer(setup.providerToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/collaborations/{id}/permissions", setup.collaborationId())
                        .header("Authorization", bearer(setup.clientToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].configured").value(false))
                .andExpect(jsonPath("$[0].currentlyActive").value(false))
                .andExpect(jsonPath("$[0].revokedAt").isString());
        b2bCompanyRead(setup).andExpect(status().isForbidden());

        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNoContent());
        b2bCompanyRead(setup).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/collaborations/{id}", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), "Provider ended relationship", null, null))))
                .andExpect(status().isOk());

        b2bCompanyRead(setup)
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivationCutsOffB2bImmediatelyAndReactivationRestoresPreservedGrant() throws Exception {
        B2bSetup setup = setupActiveCollaboration();
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/companies/{id}", setup.companyId())
                        .header("Authorization", bearer(setup.providerToken())))
                .andExpect(status().isNoContent());

        b2bCompanyRead(setup)
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/companies/{id}/reactivate", setup.companyId())
                        .header("Authorization", bearer(setup.providerToken())))
                .andExpect(status().isOk());

        b2bCompanyRead(setup)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(setup.companyId().toString()));
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
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), "Unauthorized attempt", null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shareCodeResolutionIsPrivacyMinimalAndCodesCanBeRotatedOrDisabled() throws Exception {
        String providerToken = registerClientAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(providerToken, "Discoverable Company").get("id").asText());
        String firstCode = regenerateShareCode(providerToken, companyId);
        String storedHash = companyRepository.findById(companyId).orElseThrow().getB2bShareCodeHash();
        assertThat(storedHash).hasSize(64).isNotEqualTo(firstCode).doesNotContain(firstCode);

        mockMvc.perform(post("/api/v1/collaborations/share-code/resolve")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShareCodeRequest(firstCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerAccountName").isString())
                .andExpect(jsonPath("$.companyName").value("Discoverable Company"))
                .andExpect(jsonPath("$.companyCountry").value("US"))
                .andExpect(jsonPath("$.providerAccountId").doesNotExist())
                .andExpect(jsonPath("$.companyId").doesNotExist());

        mockMvc.perform(post("/api/v1/collaborations/initiate")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InitiateCollaborationRequest(
                                firstCode, "Inspect code usage", Set.of()))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/collaborations/companies/{companyId}/share-code", companyId)
                        .header("Authorization", bearer(providerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareCode").doesNotExist())
                .andExpect(jsonPath("$.resolutionCount").value(1))
                .andExpect(jsonPath("$.requestCount").value(1))
                .andExpect(jsonPath("$.lastResolvedAt").isString())
                .andExpect(jsonPath("$.lastRequestedAt").isString());

        String secondCode = regenerateShareCode(providerToken, companyId);
        mockMvc.perform(get("/api/v1/collaborations/companies/{companyId}/share-code", companyId)
                        .header("Authorization", bearer(providerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionCount").value(0))
                .andExpect(jsonPath("$.requestCount").value(0))
                .andExpect(jsonPath("$.lastResolvedAt").doesNotExist())
                .andExpect(jsonPath("$.lastRequestedAt").doesNotExist());
        mockMvc.perform(post("/api/v1/collaborations/share-code/resolve")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShareCodeRequest(firstCode))))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/collaborations/companies/{companyId}/share-code", companyId)
                        .param("enabled", "false")
                        .header("Authorization", bearer(providerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.shareCode").doesNotExist());

        mockMvc.perform(post("/api/v1/collaborations/share-code/resolve")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShareCodeRequest(secondCode))))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyOneLiveTupleExistsButARejectedRequestAllowsANewRequest() throws Exception {
        B2bSetup setup = setupPendingCollaboration();
        String shareCode = regenerateShareCode(setup.providerToken(), setup.companyId());

        mockMvc.perform(post("/api/v1/collaborations/initiate")
                        .header("Authorization", bearer(setup.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InitiateCollaborationRequest(
                                shareCode,
                                "  Provide   company\n operations ",
                                Set.of("platform.company.read_single")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(setup.collaborationId().toString()));

        InitiateCollaborationRequest request = new InitiateCollaborationRequest(
                shareCode, "Duplicate live request", Set.of("platform.company.read_single"));

        mockMvc.perform(post("/api/v1/collaborations/initiate")
                        .header("Authorization", bearer(setup.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/collaborations/{id}/reject", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), "Capacity unavailable", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.lifecycleReason").value("Capacity unavailable"));

        String replacementResponse = mockMvc.perform(post("/api/v1/collaborations/initiate")
                        .header("Authorization", bearer(setup.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        var replacement = objectMapper.readTree(replacementResponse);

        mockMvc.perform(patch("/api/v1/collaborations/{id}/cancel-request",
                        UUID.fromString(replacement.get("id").asText()))
                        .header("Authorization", bearer(setup.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                replacement.get("version").asLong(),
                                "Request no longer needed", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lifecycleReason").value("Request no longer needed"));
    }

    @Test
    void concurrentIdenticalInitialRequestsReturnOneCreatedOneOkAndOneRelationship() throws Exception {
        String providerToken = registerClientAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(
                providerToken, "Concurrent Provider Company").get("id").asText());
        String shareCode = regenerateShareCode(providerToken, companyId);
        InitiateCollaborationRequest request = new InitiateCollaborationRequest(
                shareCode, "Concurrent identical request", Set.of("platform.company.read_single"));

        CompletableFuture<Integer> first = initiateAsync(clientToken, request);
        CompletableFuture<Integer> second = initiateAsync(clientToken, request);

        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 200);
        UUID clientAccountId = currentAccountId(clientToken);
        UUID providerAccountId = currentAccountId(providerToken);
        assertThat(collaborationRepository.findAllByClientAccountId(clientAccountId).stream()
                .filter(collaboration -> collaboration.getProviderAccount().getId().equals(providerAccountId))
                .filter(collaboration -> collaboration.getCompany().getId().equals(companyId)))
                .hasSize(1);
    }

    @Test
    void suspensionPreservesGrantsAndResumeRequiresTheCurrentVersion() throws Exception {
        B2bSetup setup = setupActiveCollaboration();
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isNoContent());
        grantPermission(setup.providerToken(), setup.collaborationId(), "platform.company.read_single")
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/collaborations/{id}", setup.collaborationId())
                        .header("Authorization", bearer(setup.clientToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("Provide company operations"))
                .andExpect(jsonPath("$.requestedPermissionCodes[0]")
                        .value("platform.company.read_single"))
                .andExpect(jsonPath("$.grants[0].permissionCode")
                        .value("platform.company.read_single"))
                .andExpect(jsonPath("$.grants[0].currentlyActive").value(true));

        mockMvc.perform(get("/api/v1/collaborations/{id}/permissions", setup.collaborationId())
                        .header("Authorization", bearer(setup.clientToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].permissionCode").value("platform.company.read_single"))
                .andExpect(jsonPath("$[0].currentlyActive").value(true));

        String suspendedResponse = mockMvc.perform(patch(
                        "/api/v1/collaborations/{id}/suspend", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), "Security review", Instant.now().plusSeconds(3600),
                                SuspensionScheduleAction.REVIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.lifecycleReason").value("Security review"))
                .andExpect(jsonPath("$.suspensionReviewAt").isString())
                .andExpect(jsonPath("$.automaticResumeAt").doesNotExist())
                .andExpect(jsonPath("$.grants[0].permissionCode").value("platform.company.read_single"))
                .andExpect(jsonPath("$.grants[0].currentlyActive").value(false))
                .andReturn().getResponse().getContentAsString();
        long suspendedVersion = objectMapper.readTree(suspendedResponse).get("version").asLong();

        b2bCompanyRead(setup).andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/collaborations/{id}/resume", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), "Stale resume", null, null))))
                .andExpect(status().isConflict());

        String resumedResponse = mockMvc.perform(patch(
                        "/api/v1/collaborations/{id}/resume", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                suspendedVersion, "Review complete", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.grants[0].currentlyActive").value(true))
                .andReturn().getResponse().getContentAsString();
        long resumedVersion = objectMapper.readTree(resumedResponse).get("version").asLong();

        b2bCompanyRead(setup)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(setup.companyId().toString()));

        mockMvc.perform(delete("/api/v1/collaborations/{id}", setup.collaborationId())
                        .header("Authorization", bearer(setup.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                resumedVersion, "Client ended relationship", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        b2bCompanyRead(setup).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/collaborations/{id}/permissions", setup.collaborationId())
                        .header("Authorization", bearer(setup.clientToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].configured").value(true))
                .andExpect(jsonPath("$[0].currentlyActive").value(false));
    }

    @Test
    void automaticResumeMustBeAnExplicitCompleteSuspensionChoice() throws Exception {
        B2bSetup setup = setupActiveCollaboration();
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        mockMvc.perform(patch("/api/v1/collaborations/{id}/suspend", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), "Incomplete schedule", scheduledAt, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/collaborations/{id}/suspend", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), "Scheduled maintenance", scheduledAt,
                                SuspensionScheduleAction.AUTOMATIC_RESUME))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.automaticResumeAt").isString())
                .andExpect(jsonPath("$.suspensionReviewAt").doesNotExist());
    }

    private B2bSetup setupPendingCollaboration() throws Exception {
        String providerToken = registerClientAndGetToken();
        String clientToken = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(providerToken, "Provider Company").get("id").asText());
        return setupPendingCollaboration(providerToken, clientToken, companyId);
    }

    private B2bSetup setupPendingCollaboration(String providerToken, String clientToken, UUID companyId) throws Exception {
        String shareCode = regenerateShareCode(providerToken, companyId);
        InitiateCollaborationRequest request = new InitiateCollaborationRequest(
                shareCode, "Provide company operations", Set.of("platform.company.read_single"));

        String response = mockMvc.perform(post("/api/v1/collaborations/initiate")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var responseJson = objectMapper.readTree(response);
        UUID collaborationId = UUID.fromString(responseJson.get("id").asText());
        return new B2bSetup(providerToken, clientToken, companyId, collaborationId,
                responseJson.get("version").asLong());
    }

    private B2bSetup setupActiveCollaboration() throws Exception {
        B2bSetup setup = setupPendingCollaboration();
        return acceptCollaboration(setup);
    }

    private B2bSetup setupActiveCollaboration(String providerToken, String clientToken, UUID companyId) throws Exception {
        B2bSetup setup = setupPendingCollaboration(providerToken, clientToken, companyId);
        return acceptCollaboration(setup);
    }

    private B2bSetup acceptCollaboration(B2bSetup setup) throws Exception {
        String response = mockMvc.perform(patch("/api/v1/collaborations/{id}/accept", setup.collaborationId())
                        .header("Authorization", bearer(setup.providerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CollaborationCommandRequest(
                                setup.version(), null, null, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new B2bSetup(setup.providerToken(), setup.clientToken(), setup.companyId(),
                setup.collaborationId(), objectMapper.readTree(response).get("version").asLong());
    }

    private org.springframework.test.web.servlet.ResultActions grantPermission(
            String token, UUID collaborationId, String permissionCode) throws Exception {
        return mockMvc.perform(post("/api/v1/collaborations/{id}/permissions", collaborationId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new B2BPermissionRequest(
                                permissionCode, registryCatalogVersionService.currentVersion()))));
    }

    private String regenerateShareCode(String providerToken, UUID companyId) throws Exception {
        String response = mockMvc.perform(post(
                        "/api/v1/collaborations/companies/{companyId}/share-code", companyId)
                        .header("Authorization", bearer(providerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("shareCode").asText();
    }

    private CompletableFuture<Integer> initiateAsync(
            String clientToken, InitiateCollaborationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(post("/api/v1/collaborations/initiate")
                                .header("Authorization", bearer(clientToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
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

    private SubscriptionEntitlementSnapshot removeFeatureFromActiveSubscription(String token, String featureCode) throws Exception {
        var subscription = subscriptionRepository.findActiveByAccountId(currentAccountId(token)).orElseThrow();
        SubscriptionEntitlementSnapshot originalSnapshot = subscription.getEntitlementSnapshot();
        var snapshot = subscriptionSnapshotReader.read(originalSnapshot).orElseThrow();
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
        return originalSnapshot;
    }

    private void restoreActiveSubscriptionSnapshot(String token, SubscriptionEntitlementSnapshot snapshot) throws Exception {
        var subscription = subscriptionRepository.findActiveByAccountId(currentAccountId(token)).orElseThrow();
        subscription.setEntitlementSnapshot(snapshot);
        subscriptionRepository.saveAndFlush(subscription);
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
            UUID collaborationId,
            long version
    ) {
    }
}
