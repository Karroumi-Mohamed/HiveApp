package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyActivationRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyRequests;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPolicyActivationAssessor;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class CommercialPolicyControlPlaneIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private CommercialPolicyRepository policyRepository;
    @Autowired private CommercialPolicyActivationRepository activationRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private CommercialPolicyActivationAssessor activationAssessor;
    @Autowired private CommercialPreviewTokenService previewTokenService;
    @Autowired private CommercialCatalogVersionService catalogVersionService;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void concurrentActivationAcceptsOneReviewedApplyAndCreatesOneSnapshot() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("policy-concurrent");
        JsonNode draft = createPolicy(token,
                accountSetRequest("Concurrent policy", Set.of(account.getId())));
        UUID policyId = UUID.fromString(draft.get("summary").get("id").asText());
        JsonNode preview = activationPreview(token, policyId);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                () -> activationStatus(token, policyId, preview, "Concurrent review", start));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                () -> activationStatus(token, policyId, preview, "Concurrent review", start));
        start.countDown();

        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(200, 409);
        assertThat(activationRepository.findMaximumActivationNumber(policyId)).isEqualTo(1);
        assertThat(policyRepository.findById(policyId).orElseThrow().getStatus().name())
                .isEqualTo("ACTIVE");
    }

    @Test
    void listQueryCountDoesNotGrowWithPolicyRows() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("policy-query-count");
        for (int index = 0; index < 6; index++) {
            createPolicy(token, accountSetRequest(
                    "Query baseline " + index + " " + UUID.randomUUID(), Set.of(account.getId())));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .param("page", "0").param("size", "5")
                        .param("sort", "code").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));
        long baseline = statistics.getPrepareStatementCount();

        for (int index = 0; index < 12; index++) {
            createPolicy(token, accountSetRequest(
                    "Query growth " + index + " " + UUID.randomUUID(), Set.of(account.getId())));
        }
        statistics.clear();
        mockMvc.perform(get("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .param("page", "0").param("size", "5")
                        .param("sort", "code").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(baseline);
    }

    @Test
    void reviewedLifecycleSnapshotsExactAudienceWithoutMutatingSubscriptions() throws Exception {
        String token = loginAdminAndGetToken();
        Account first = registerAccount("policy-first");
        Account second = registerAccount("policy-second");
        SubscriptionEntitlementSnapshot firstBefore = subscriptionRepository.findUsableByAccountId(first.getId())
                .orElseThrow().getEntitlementSnapshot();
        SubscriptionEntitlementSnapshot secondBefore = subscriptionRepository.findUsableByAccountId(second.getId())
                .orElseThrow().getEntitlementSnapshot();

        JsonNode draft = createPolicy(token, accountSetRequest(
                "Contract discount", Set.of(first.getId(), second.getId())));
        UUID policyId = UUID.fromString(draft.get("summary").get("id").asText());
        assertThat(draft.get("summary").get("ownerIdentityRestricted").asBoolean()).isTrue();
        assertThat(draft.get("summary").has("ownerAdminUserId")).isFalse();

        mockMvc.perform(get("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .param("search", "Contract discount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].configuredTargetCount").value(2))
                .andExpect(jsonPath("$.content[0].ownerAdminUserId").doesNotExist())
                .andExpect(jsonPath("$.content[0].executionSupported").value(true));

        mockMvc.perform(get("/api/admin/commercial-policies/{id}/audience", policyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccounts").value(2))
                .andExpect(jsonPath("$.accounts.content", hasSize(2)))
                .andExpect(jsonPath("$.accounts.content[0].ownerEmail").doesNotExist());

        JsonNode preview = activationPreview(token, policyId);
        assertThat(preview.get("activatable").asBoolean()).isTrue();
        assertThat(preview.get("affectedAccountCount").asInt()).isEqualTo(2);
        assertThat(preview.get("executionSupported").asBoolean()).isTrue();
        assertThat(preview.get("executionBlockers").toString())
                .contains("SCHEDULED_EXECUTION_NOT_AVAILABLE");
        JsonNode active = activate(token, policyId, preview, "Approve exact audience");
        assertThat(active.get("summary").get("status").asText()).isEqualTo("ACTIVE");
        var activationEvidence = activationRepository.findTopByPolicy_IdOrderByActivationNumberDesc(policyId)
                .orElseThrow();
        UUID activationId = activationEvidence.getId();
        var immutableActivation = activationRepository.findWithAccountsById(activationId).orElseThrow();
        assertThatThrownBy(() -> immutableActivation.getAccountIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(activationRepository.findSnapshotAccountIds(activationId))
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        mockMvc.perform(get(
                                "/api/admin/commercial-policies/{id}/activations/{activationId}/accounts",
                                policyId, activationId)
                        .header("Authorization", bearer(token))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.immutableAccountCount").value(2))
                .andExpect(jsonPath("$.accounts.content", hasSize(1)))
                .andExpect(jsonPath("$.accounts.content[0].ownerEmail").doesNotExist());
        assertThat(subscriptionRepository.findUsableByAccountId(first.getId()).orElseThrow()
                .getEntitlementSnapshot()).isEqualTo(firstBefore);
        assertThat(subscriptionRepository.findUsableByAccountId(second.getId()).orElseThrow()
                .getEntitlementSnapshot()).isEqualTo(secondBefore);

        mockMvc.perform(get("/api/admin/commercial-policies/{id}/activations", policyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].affectedAccountCount").value(2))
                .andExpect(jsonPath("$.content[0].reason").value("Approve exact audience"));
        mockMvc.perform(get("/api/admin/commercial-policies/{id}/owner", policyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));

        JsonNode paused = responseJson(mockMvc.perform(post("/api/admin/commercial-policies/{id}/pause", policyId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.VersionReason(
                                active.get("summary").get("version").asLong(), "Pause contract exception"))))
                .andExpect(status().isOk()));
        JsonNode resumePreview = activationPreview(token, policyId);
        JsonNode resumed = activateAt(token, policyId, "resume", resumePreview, "Resume reviewed audience");
        assertThat(resumed.get("summary").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(activationRepository.findMaximumActivationNumber(policyId)).isEqualTo(2);
        assertThat(paused.get("summary").get("status").asText()).isEqualTo("PAUSED");
        mockMvc.perform(get("/api/admin/commercial-policies/{id}/history", policyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].reason", hasItem("Resume reviewed audience")))
                .andExpect(jsonPath("$.content[*].actorEmail", hasItem(ADMIN_EMAIL)));
    }

    @Test
    void planRevisionTargetSnapshotsOnlySubscribersOfThatExactRevision() throws Exception {
        String token = loginAdminAndGetToken();
        Account included = registerAccount("policy-plan-included");
        Account excluded = registerAccount("policy-plan-excluded");
        var includedSubscription = subscriptionRepository.findUsableByAccountId(included.getId())
                .orElseThrow();
        var excludedSubscription = subscriptionRepository.findUsableByAccountId(excluded.getId())
                .orElseThrow();
        var targetPlan = planRepository.findByCode(
                includedSubscription.getEntitlementSnapshot().planCode()).orElseThrow();
        var otherPlan = planRepository.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(targetPlan.getId()))
                .findFirst().orElseThrow();
        excludedSubscription.setPlan(otherPlan);
        subscriptionRepository.saveAndFlush(excludedSubscription);

        CommercialPolicyRequests.Create request = new CommercialPolicyRequests.Create(
                "Exact plan revision policy", null, Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600), CommercialPolicySource.RETENTION, 15,
                "Apply only to the reviewed Plan revision population", null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.PLAN_REVISION_SUBSCRIBERS,
                        null, Set.of(), targetPlan.getId(), null),
                List.of(blockFeature("platform.staff")));
        JsonNode draft = createPolicy(token, request);
        UUID policyId = UUID.fromString(draft.get("summary").get("id").asText());
        JsonNode preview = activationPreview(token, policyId);
        assertThat(preview.get("activatable").asBoolean()).isTrue();

        activate(token, policyId, preview, "Approve exact Plan revision audience");
        UUID activationId = activationRepository.findTopByPolicy_IdOrderByActivationNumberDesc(policyId)
                .orElseThrow().getId();
        assertThat(activationRepository.findSnapshotAccountIds(activationId))
                .contains(included.getId())
                .doesNotContain(excluded.getId());
    }

    @Test
    void draftArchiveAndDeleteAreGuardedAndDoNotErasePublishedEvidence() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("policy-safe-delete");
        JsonNode deletable = createPolicy(token,
                accountSetRequest("Deletable draft", Set.of(account.getId())));
        UUID deletableId = UUID.fromString(deletable.get("summary").get("id").asText());
        mockMvc.perform(delete("/api/admin/commercial-policies/{id}", deletableId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.VersionReason(
                                deletable.get("summary").get("version").asLong(),
                                "Discard unused unpublished draft"))))
                .andExpect(status().isNoContent());
        assertThat(policyRepository.findById(deletableId)).isEmpty();

        JsonNode archivable = createPolicy(token,
                accountSetRequest("Archivable draft", Set.of(account.getId())));
        UUID archivableId = UUID.fromString(archivable.get("summary").get("id").asText());
        JsonNode archived = responseJson(mockMvc.perform(post(
                                "/api/admin/commercial-policies/{id}/archive", archivableId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.VersionReason(
                                archivable.get("summary").get("version").asLong(),
                                "Retain cancelled draft history"))))
                .andExpect(status().isOk()));
        assertThat(archived.get("summary").get("status").asText()).isEqualTo("ARCHIVED");
        mockMvc.perform(get("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .param("status", "ARCHIVED")
                        .param("search", "Archivable draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(delete("/api/admin/commercial-policies/{id}", archivableId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.VersionReason(
                                archived.get("summary").get("version").asLong(),
                                "Attempt to erase retained history"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(policyRepository.findById(archivableId)).isPresent();
    }

    @Test
    void unknownSegmentReferenceIsRejectedBeforeAPolicyDraftCanPersist() throws Exception {
        String token = loginAdminAndGetToken();
        CommercialPolicyRequests.Create request = new CommercialPolicyRequests.Create(
                "Future segment policy", null, Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600), CommercialPolicySource.MARKETING, 10,
                "Prepare segment-backed policy", null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.SEGMENT, null, Set.of(), null, "LOYAL_CUSTOMERS"),
                List.of(blockFeature("platform.staff")));
        mockMvc.perform(post("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void typedEffectsRejectAmbiguousFieldsAndAudienceApisStayBoundedAndPrivate() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("policy-private");
        String ownerEmail = accountRepository.findOwnerEmailById(account.getId()).orElseThrow();
        CommercialPolicyRequests.Effect malformed = new CommercialPolicyRequests.Effect(
                CommercialPolicyEffectType.FIXED_DISCOUNT, null, null, "platform.staff", null,
                null, new BigDecimal("5.00"), "USD", null, null, null, null);
        CommercialPolicyRequests.Create invalid = new CommercialPolicyRequests.Create(
                "Ambiguous effect", null, Instant.now(), Instant.now().plusSeconds(300),
                CommercialPolicySource.SALES, 1, "Reject ambiguous fields", null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.ACCOUNT, account.getId(), Set.of(), null, null),
                List.of(malformed));
        mockMvc.perform(post("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        CommercialPolicyRequests.Effect lossyPercentage = new CommercialPolicyRequests.Effect(
                CommercialPolicyEffectType.PERCENTAGE_DISCOUNT, null, null, null, null,
                null, null, null, null, new BigDecimal("0.00001"),
                new BigDecimal("10.00"), "USD");
        CommercialPolicyRequests.Create lossy = new CommercialPolicyRequests.Create(
                "Lossy percentage", null, Instant.now(), Instant.now().plusSeconds(300),
                CommercialPolicySource.SALES, 1, "Reject database rounding", null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.ACCOUNT, account.getId(), Set.of(), null, null),
                List.of(lossyPercentage));
        mockMvc.perform(post("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lossy)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/admin/commercial-policies/account-choices")
                        .header("Authorization", bearer(token))
                        .param("query", ownerEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/admin/commercial-policies/account-choices")
                        .header("Authorization", bearer(token))
                        .param("query", account.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());
        mockMvc.perform(get("/api/admin/commercial-policies/account-choices")
                        .header("Authorization", bearer(token))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .param("sort", "owner.email"))
                .andExpect(status().isBadRequest());

        CommercialPolicyRequests.Create direct = new CommercialPolicyRequests.Create(
                "Direct Account restriction", null, Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300), CommercialPolicySource.COMPLIANCE, 5,
                "Target one exact Account", null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.ACCOUNT, account.getId(), Set.of(), null, null),
                List.of(blockFeature("platform.staff")));
        JsonNode directPolicy = createPolicy(token, direct);
        UUID directId = UUID.fromString(directPolicy.get("summary").get("id").asText());
        mockMvc.perform(get("/api/admin/commercial-policies/{id}/audience", directId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccounts").value(1))
                .andExpect(jsonPath("$.accounts.content[0].id").value(account.getId().toString()));
    }

    @Test
    void activationEvidenceRejectsTamperingExpiryAndStaleDefinitionsWithoutWriting() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("policy-evidence");
        JsonNode draft = createPolicy(token, accountSetRequest("Evidence policy", Set.of(account.getId())));
        UUID policyId = UUID.fromString(draft.get("summary").get("id").asText());
        JsonNode preview = activationPreview(token, policyId);
        String tokenValue = preview.get("previewToken").asText();
        String tampered = tokenValue.substring(0, tokenValue.length() - 1)
                + (tokenValue.endsWith("A") ? "B" : "A");
        assertStaleActivation(token, policyId, preview.get("expectedVersion").asLong(), tampered);

        CommercialPolicy policy = policyRepository.findDetailById(policyId).orElseThrow();
        var assessment = activationAssessor.assess(policy, Instant.now());
        UUID actorUserId = adminUserRepository.findByUser_Email(ADMIN_EMAIL).orElseThrow()
                .getUser().getId();
        String wrongActor = previewTokenService.issue(
                CommercialPreviewKind.COMMERCIAL_POLICY_ACTIVATION,
                policyId, policy.getVersion(), UUID.randomUUID(), catalogVersionService.currentRevision(),
                registryCatalogVersionService.currentVersion(), assessment.fingerprint(),
                Instant.now()).token();
        assertStaleActivation(token, policyId, policy.getVersion(), wrongActor);
        String expired = previewTokenService.issue(
                CommercialPreviewKind.COMMERCIAL_POLICY_ACTIVATION,
                policyId, policy.getVersion(), actorUserId, catalogVersionService.currentRevision(),
                registryCatalogVersionService.currentVersion(), assessment.fingerprint(),
                Instant.now().minusSeconds(360)).token();
        assertStaleActivation(token, policyId, policy.getVersion(), expired);

        createPolicy(token, accountSetRequest(
                "Unrelated catalogue change " + UUID.randomUUID(), Set.of(account.getId())));
        assertStaleActivation(token, policyId, preview.get("expectedVersion").asLong(), tokenValue);
        JsonNode definitionPreview = activationPreview(token, policyId);

        CommercialPolicyRequests.Update update = new CommercialPolicyRequests.Update(
                policy.getVersion(), "Evidence policy edited", null,
                policy.getEffectiveFrom(), policy.getEffectiveUntil(), policy.getSource(),
                policy.getPriority() + 1, "Change signed definition", null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.ACCOUNT_SET, null, Set.of(account.getId()), null, null),
                List.of(fixedDiscount()));
        mockMvc.perform(put("/api/admin/commercial-policies/{id}", policyId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
        assertStaleActivation(token, policyId,
                definitionPreview.get("expectedVersion").asLong(),
                definitionPreview.get("previewToken").asText());
        assertThat(policyRepository.findById(policyId).orElseThrow().getStatus().name()).isEqualTo("DRAFT");
        assertThat(activationRepository.findMaximumActivationNumber(policyId)).isZero();
    }

    @Test
    void revisionActivationEndsPriorRevisionAtomicallyAndComparisonIsTyped() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("policy-revision");
        JsonNode sourceDraft = createPolicy(token,
                accountSetRequest("Revision source", Set.of(account.getId())));
        UUID sourceId = UUID.fromString(sourceDraft.get("summary").get("id").asText());
        JsonNode sourceActive = activate(token, sourceId, activationPreview(token, sourceId), "Publish source");

        JsonNode successor = responseJson(mockMvc.perform(post(
                                "/api/admin/commercial-policies/{id}/revisions", sourceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.VersionReason(
                                sourceActive.get("summary").get("version").asLong(), "Revise terms"))))
                .andExpect(status().isCreated()));
        UUID successorId = UUID.fromString(successor.get("summary").get("id").asText());
        CommercialPolicyRequests.Update update = new CommercialPolicyRequests.Update(
                successor.get("summary").get("version").asLong(), "Revision successor", null,
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600),
                CommercialPolicySource.CONTRACT, 50, "Stronger reviewed terms", null, "C-42",
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.ACCOUNT_SET, null, Set.of(account.getId()), null, null),
                List.of(fixedDiscount()));
        JsonNode updated = responseJson(mockMvc.perform(put(
                                "/api/admin/commercial-policies/{id}", successorId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk()));

        mockMvc.perform(get("/api/admin/commercial-policies/{id}/compare/{other}",
                        sourceId, successorId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sameLineage").value(true))
                .andExpect(jsonPath("$.directSuccessor").value(true))
                .andExpect(jsonPath("$.changedFields", hasItem("PRIORITY")))
                .andExpect(jsonPath("$.changedFields", hasItem("CONTRACT_REFERENCE")));

        JsonNode successorPreview = activationPreview(token, successorId);
        assertThat(successorPreview.get("policyRevisionToEnd").asText()).isEqualTo(sourceId.toString());
        activate(token, successorId, successorPreview, "Replace policy revision");
        assertThat(policyRepository.findById(sourceId).orElseThrow().getStatus().name()).isEqualTo("ENDED");
        assertThat(policyRepository.findById(successorId).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
        assertThat(updated.get("summary").get("revisionNumber").asInt()).isEqualTo(2);
    }

    @Test
    void archivingAnUnpublishedSuccessorDoesNotBrickItsLiveLineage() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("policy-archived-successor");
        JsonNode sourceDraft = createPolicy(token,
                accountSetRequest("Lineage remains evolvable", Set.of(account.getId())));
        UUID sourceId = UUID.fromString(sourceDraft.get("summary").get("id").asText());
        JsonNode sourceActive = activate(
                token, sourceId, activationPreview(token, sourceId), "Publish lineage source");

        JsonNode cancelledSuccessor = responseJson(mockMvc.perform(post(
                                "/api/admin/commercial-policies/{id}/revisions", sourceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.VersionReason(
                                sourceActive.get("summary").get("version").asLong(),
                                "Prepare a successor"))))
                .andExpect(status().isCreated()));
        UUID cancelledId = UUID.fromString(cancelledSuccessor.get("summary").get("id").asText());
        mockMvc.perform(post("/api/admin/commercial-policies/{id}/archive", cancelledId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.VersionReason(
                                cancelledSuccessor.get("summary").get("version").asLong(),
                                "Retain cancelled proposal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/admin/commercial-policies/{id}", sourceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.availableActions", hasItem("REVISE")));
        JsonNode replacement = responseJson(mockMvc.perform(post(
                                "/api/admin/commercial-policies/{id}/revisions", sourceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.VersionReason(
                                sourceActive.get("summary").get("version").asLong(),
                                "Replace the cancelled proposal"))))
                .andExpect(status().isCreated()));
        assertThat(replacement.get("summary").get("revisionNumber").asInt()).isEqualTo(3);
        UUID replacementId = UUID.fromString(replacement.get("summary").get("id").asText());
        mockMvc.perform(get("/api/admin/commercial-policies/{id}/compare/{other}",
                        sourceId, replacementId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directSuccessor").value(true));
    }

    private CommercialPolicyRequests.Create accountSetRequest(String name, Set<UUID> accountIds) {
        return new CommercialPolicyRequests.Create(
                name, "A typed, reviewed contract exception", Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600), CommercialPolicySource.CONTRACT, 20,
                "Approved contract terms", "APPROVAL-1", "CONTRACT-1",
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.ACCOUNT_SET, null, accountIds, null, null),
                List.of(fixedDiscount()));
    }

    private CommercialPolicyRequests.Effect fixedDiscount() {
        return new CommercialPolicyRequests.Effect(
                CommercialPolicyEffectType.FIXED_DISCOUNT, null, null, null, null, null,
                new BigDecimal("5.00"), "MAD", null, null, null, null);
    }

    private CommercialPolicyRequests.Effect blockFeature(String code) {
        return new CommercialPolicyRequests.Effect(
                CommercialPolicyEffectType.BLOCK_FEATURE, null, null, code, null, null,
                null, null, null, null, null, null);
    }

    private JsonNode createPolicy(String token, CommercialPolicyRequests.Create request) throws Exception {
        return responseJson(mockMvc.perform(post("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
    }

    private JsonNode activationPreview(String token, UUID policyId) throws Exception {
        return responseJson(mockMvc.perform(get(
                                "/api/admin/commercial-policies/{id}/activation-preview", policyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
    }

    private JsonNode activate(String token, UUID policyId, JsonNode preview, String reason) throws Exception {
        return activateAt(token, policyId, "activate", preview, reason);
    }

    private JsonNode activateAt(
            String token,
            UUID policyId,
            String action,
            JsonNode preview,
            String reason
    ) throws Exception {
        return responseJson(mockMvc.perform(post(
                                "/api/admin/commercial-policies/{id}/" + action, policyId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.Activation(
                                preview.get("expectedVersion").asLong(), reason,
                                preview.get("previewToken").asText()))))
                .andExpect(status().isOk()));
    }

    private void assertStaleActivation(String token, UUID id, long version, String evidence) throws Exception {
        mockMvc.perform(post("/api/admin/commercial-policies/{id}/activate", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.Activation(
                                version, "Reject stale evidence", evidence))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ACTIVATION_PREVIEW"));
    }

    private int activationStatus(
            String token,
            UUID policyId,
            JsonNode preview,
            String reason,
            CountDownLatch start
    ) {
        try {
            start.await();
            return mockMvc.perform(post("/api/admin/commercial-policies/{id}/activate", policyId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.Activation(
                                    preview.get("expectedVersion").asLong(), reason,
                                    preview.get("previewToken").asText()))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private Account registerAccount(String marker) throws Exception {
        String email = marker + "-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, CLIENT_PASSWORD, "Policy", "Target", null))))
                .andExpect(status().isCreated());
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        return accountRepository.findByOwner_Id(userId).orElseThrow();
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
