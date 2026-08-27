package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegment;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyActivationRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentActivationRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyRequests;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentRequests;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.service.CommercialSegmentAudienceResolver;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
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
class CommercialSegmentControlPlaneIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Test
    void policyScopedSegmentChooserReturnsOnlyActiveFrozenRevisions() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("segment-policy-choice");
        JsonNode executable = createSegment(token,
                explicitRequest("Executable renewal audience", Set.of(account.getId())));
        UUID executableId = id(executable);
        JsonNode active = activate(token, executableId, preview(token, executableId),
                "Freeze the audience for policy targeting");
        JsonNode draft = createSegment(token,
                explicitRequest("Unpublished renewal audience", Set.of(account.getId())));

        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices")
                        .header("Authorization", bearer(token))
                        .param("query", "Executable renewal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(executableId.toString()))
                .andExpect(jsonPath("$.content[0].code")
                        .value(active.at("/summary/code").asText()))
                .andExpect(jsonPath("$.content[0].immutableAccountCount").value(1))
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());

        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices/selected")
                        .header("Authorization", bearer(token))
                        .param("references", active.at("/summary/code").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Executable renewal audience"));
        mockMvc.perform(get("/api/admin/commercial-policies/segment-choices/selected")
                        .header("Authorization", bearer(token))
                        .param("references", draft.at("/summary/code").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private CommercialSegmentRepository segmentRepository;
    @Autowired private CommercialSegmentActivationRepository segmentActivationRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private AddOnRepository addOnRepository;
    @Autowired private QuotaPackageRepository quotaPackageRepository;
    @Autowired private CommercialPolicyRepository policyRepository;
    @Autowired private CommercialPolicyActivationRepository policyActivationRepository;
    @Autowired private CommercialSegmentAudienceResolver audienceResolver;
    @Autowired private CommercialPreviewTokenService previewTokenService;
    @Autowired private CommercialCatalogVersionService catalogVersionService;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void explicitWorkflowSeparatesIdentityAndFreezesReviewedAudience() throws Exception {
        String token = loginAdminAndGetToken();
        Account first = registerAccount("segment-explicit-first");
        Account second = registerAccount("segment-explicit-second");
        JsonNode draft = createSegment(token, explicitRequest(
                "Quarterly onboarding", Set.of(first.getId(), second.getId())));
        UUID segmentId = id(draft);

        mockMvc.perform(get("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .param("search", "quarterly")
                        .param("sort", "name").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].configuredAccountCount").value(2))
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());

        JsonNode preview = preview(token, segmentId);
        assertThat(preview.get("totalAccounts").asInt()).isEqualTo(2);
        assertThat(preview.get("sample").toString()).contains(first.getId().toString());
        assertThat(preview.get("sample").toString()).doesNotContain(first.getName());
        mockMvc.perform(get("/api/admin/segments/{id}/count", segmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccounts").value(2))
                .andExpect(jsonPath("$.previewToken").doesNotExist())
                .andExpect(jsonPath("$.sample").doesNotExist());
        mockMvc.perform(get("/api/admin/segments/{id}/preview-identities", segmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sample[*].accountName", hasItem(first.getName())))
                .andExpect(jsonPath("$.sample[*].ownerEmail").exists());

        JsonNode active = activate(token, segmentId, preview, "Approve the exact onboarding audience");
        assertThat(active.at("/summary/status").asText()).isEqualTo("ACTIVE");
        assertThat(active.at("/summary/availableActions").toString()).contains("ARCHIVE");
        assertThat(active.at("/summary/blockedActions/DELETE_DRAFT").toString())
                .contains("NOT_DRAFT", "HAS_ACTIVATION_HISTORY");
        assertThat(active.at("/summary/blockedActions/ARCHIVE").isMissingNode()).isTrue();
        var activation = segmentActivationRepository
                .findTopBySegment_IdOrderByActivationNumberDesc(segmentId).orElseThrow();

        first.setName("Renamed after Segment activation");
        accountRepository.saveAndFlush(first);
        registerAccount("segment-explicit-later");
        mockMvc.perform(get("/api/admin/segments/{id}/activations/{activationId}/accounts",
                                segmentId, activation.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.immutableAccountCount").value(2))
                .andExpect(jsonPath("$.accounts.content", hasSize(2)))
                .andExpect(jsonPath("$.accounts.content[0].accountName").doesNotExist());
        assertThat(segmentActivationRepository.findWithAccountsById(activation.getId()).orElseThrow()
                .getAccountIds()).containsExactlyInAnyOrder(first.getId(), second.getId());

        mockMvc.perform(get("/api/admin/segments/{id}/history", segmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].reason",
                        hasItem("Approve the exact onboarding audience")));
        mockMvc.perform(delete("/api/admin/segments/{id}", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(active, "Never erase published evidence")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        JsonNode duplicate = responseJson(mockMvc.perform(post(
                                "/api/admin/segments/{id}/duplicate", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialSegmentRequests.Duplicate(
                                active.at("/summary/version").asLong(),
                                "Quarterly onboarding copy", "Create an independent draft"))))
                .andExpect(status().isCreated()));
        assertThat(duplicate.at("/summary/lineageId").asText())
                .isNotEqualTo(active.at("/summary/lineageId").asText());
        mockMvc.perform(delete("/api/admin/segments/{id}", id(duplicate))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(duplicate, "Discard the unused copy")))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/segments/{id}/archive", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(active, "Retain immutable activation evidence")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("ARCHIVED"));
    }

    @Test
    void typedCriteriaAreAndAcrossFieldsOrWithinValuesAndProjectionTracksSnapshots() throws Exception {
        String token = loginAdminAndGetToken();
        Account matching = registerAccount("segment-criteria-match");
        Account otherProduct = registerAccount("segment-criteria-other");
        var matchingSubscription = subscriptionRepository.findUsableByAccountId(matching.getId())
                .orElseThrow();
        var otherSubscription = subscriptionRepository.findUsableByAccountId(otherProduct.getId())
                .orElseThrow();
        UUID planId = matchingSubscription.getPlan().getId();

        replaceHoldings(matching.getId(), "CUSTOM_ROLES", "MEMBERS_5");
        replaceHoldings(otherProduct.getId(), "ORGANIZATION_TOOLS", "COMPANY_1");

        CommercialSegmentRequests.Criteria criteria = new CommercialSegmentRequests.Criteria(
                Set.of(planId), Set.of(SubscriptionStatus.ACTIVE), Set.of("usd"),
                Set.of(BillingCycle.MONTHLY), Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600), Set.of(
                        holding(CommercialSegmentProductType.ADD_ON, "CUSTOM_ROLES"),
                        holding(CommercialSegmentProductType.QUOTA_PACKAGE, "COMPANIES_20")));
        JsonNode segment = createSegment(token, criteriaRequest("Current Insights customers", criteria));
        UUID segmentId = id(segment);
        JsonNode firstPreview = preview(token, segmentId);
        assertThat(firstPreview.get("totalAccounts").asInt()).isOne();
        assertThat(firstPreview.get("sample").toString()).contains(matching.getId().toString())
                .doesNotContain(otherProduct.getId().toString());
        JsonNode active = activate(token, segmentId, firstPreview,
                "Freeze the currently matched product audience");

        replaceHoldings(matching.getId(), "ORGANIZATION_TOOLS", "COMPANY_1");
        JsonNode secondPreview = preview(token, segmentId);
        assertThat(secondPreview.get("totalAccounts").asInt()).isZero();
        assertThat(secondPreview.get("blockers").toString()).contains("EMPTY_AUDIENCE");
        assertThat(audienceResolver.resolveFrozenReference(
                active.at("/summary/code").asText()).accountIds()).containsExactly(matching.getId());

        // The plan itself is also a normalized current holding, independent of the typed plan-id field.
        CommercialSegmentRequests.Criteria planProduct = new CommercialSegmentRequests.Criteria(
                Set.of(), Set.of(), Set.of(), Set.of(), null, null,
                Set.of(holding(CommercialSegmentProductType.PLAN,
                        otherSubscription.getEntitlementSnapshot().planCode())));
        JsonNode planSegment = createSegment(token, criteriaRequest("Current product holding", planProduct));
        assertThat(preview(token, id(planSegment)).get("totalAccounts").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void criteriaValidationRejectsEmptyContradictoryAndHistoricalStateDefinitions() throws Exception {
        String token = loginAdminAndGetToken();
        assertInvalidCriteria(token, "Empty typed criteria", new CommercialSegmentRequests.Criteria(
                Set.of(), Set.of(), Set.of(), Set.of(), null, null, Set.of()));
        assertInvalidCriteria(token, "Contradictory bounds", new CommercialSegmentRequests.Criteria(
                Set.of(), Set.of(), Set.of(), Set.of(), Instant.now(),
                Instant.now().minusSeconds(60), Set.of()));
        assertInvalidCriteria(token, "Historical state", new CommercialSegmentRequests.Criteria(
                Set.of(), Set.of(SubscriptionStatus.CANCELLED), Set.of(), Set.of(),
                null, null, Set.of()));
        UUID freePlanId = planRepository.findByCode("FREE").orElseThrow().getId();
        assertInvalidCriteria(token, "Contradictory product and revision", new CommercialSegmentRequests.Criteria(
                Set.of(freePlanId), Set.of(), Set.of(), Set.of(), null, null,
                Set.of(holding(CommercialSegmentProductType.PLAN, "PRO"))));
        mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criteriaRequest(
                                "Unknown product holding", new CommercialSegmentRequests.Criteria(
                                        Set.of(), Set.of(), Set.of(), Set.of(), null, null,
                                        Set.of(holding(CommercialSegmentProductType.ADD_ON,
                                                "DOES_NOT_EXIST")))))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criteriaRequest(
                                "Invalid currency", new CommercialSegmentRequests.Criteria(
                                        Set.of(), Set.of(), Set.of("US"), Set.of(),
                                        null, null, Set.of())))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        Account account = registerAccount("segment-explicit-validation");
        CommercialSegmentRequests.Create mixed = new CommercialSegmentRequests.Create(
                "Mixed definition", null, CommercialSegmentKind.EXPLICIT_ACCOUNTS,
                CommercialSegmentSource.MANUAL, "Reject ambiguous definition",
                new CommercialSegmentRequests.Definition(Set.of(account.getId()),
                        new CommercialSegmentRequests.Criteria(Set.of(), Set.of(), Set.of(), Set.of(),
                                Instant.now().minusSeconds(60), null, Set.of())));
        mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mixed)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void activationEvidenceRejectsTamperingCrossActorCrossOperationAndStaleness() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("segment-evidence");
        JsonNode draft = createSegment(token,
                explicitRequest("Evidence-bound Segment", Set.of(account.getId())));
        JsonNode otherDraft = createSegment(token,
                explicitRequest("Other evidence resource", Set.of(account.getId())));
        UUID segmentId = id(draft);
        JsonNode reviewed = preview(token, segmentId);
        JsonNode otherReviewed = preview(token, id(otherDraft));
        String signed = reviewed.get("previewToken").asText();
        assertStale(token, segmentId, reviewed.get("criteriaVersion").asLong(),
                otherReviewed.get("previewToken").asText());
        String tampered = signed.substring(0, signed.length() - 1)
                + (signed.endsWith("A") ? "B" : "A");
        assertStale(token, segmentId, reviewed.get("criteriaVersion").asLong(), tampered);

        Object[] evidenceInputs = transactionTemplate.execute(ignored -> {
            CommercialSegment managed = segmentRepository.findDetailById(segmentId).orElseThrow();
            var evaluation = audienceResolver.evaluate(managed, Instant.now());
            return new Object[]{managed.getVersion(), evaluation.fingerprint()};
        });
        long segmentVersion = (long) evidenceInputs[0];
        String assessmentFingerprint = (String) evidenceInputs[1];
        long catalogRevision = catalogVersionService.currentRevision();
        UUID actor = adminUserRepository.findByUser_Email(ADMIN_EMAIL).orElseThrow().getUser().getId();
        String crossActor = previewTokenService.issue(
                CommercialPreviewKind.COMMERCIAL_SEGMENT_ACTIVATION, segmentId, segmentVersion,
                UUID.randomUUID(), catalogRevision, "SEGMENT_CRITERIA_V1", assessmentFingerprint,
                Instant.now()).token();
        assertStale(token, segmentId, segmentVersion, crossActor);
        String crossOperation = previewTokenService.issue(
                CommercialPreviewKind.COMMERCIAL_POLICY_ACTIVATION, segmentId, segmentVersion,
                actor, catalogRevision, "SEGMENT_CRITERIA_V1", assessmentFingerprint,
                Instant.now()).token();
        assertStale(token, segmentId, segmentVersion, crossOperation);

        createSegment(token, explicitRequest(
                "Unrelated catalogue mutation " + UUID.randomUUID(), Set.of(account.getId())));
        assertStale(token, segmentId, reviewed.get("criteriaVersion").asLong(), signed);

        JsonNode fresh = preview(token, segmentId);
        CommercialSegmentRequests.Update update = new CommercialSegmentRequests.Update(
                draft.at("/summary/version").asLong(), "Edited evidence Segment", null,
                CommercialSegmentKind.EXPLICIT_ACCOUNTS, CommercialSegmentSource.SUPPORT,
                "Change reviewed definition", new CommercialSegmentRequests.Definition(
                        Set.of(account.getId()), null));
        mockMvc.perform(put("/api/admin/segments/{id}", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
        assertStale(token, segmentId, fresh.get("criteriaVersion").asLong(),
                fresh.get("previewToken").asText());
        assertThat(segmentActivationRepository.findMaximumActivationNumber(segmentId)).isZero();
    }

    @Test
    void concurrentActivationAcceptsExactlyOneReviewedWrite() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("segment-concurrent");
        JsonNode draft = createSegment(token,
                explicitRequest("Concurrent Segment", Set.of(account.getId())));
        UUID segmentId = id(draft);
        JsonNode reviewed = preview(token, segmentId);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                () -> activationStatus(token, segmentId, reviewed, start));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                () -> activationStatus(token, segmentId, reviewed, start));
        start.countDown();
        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(200, 409);
        assertThat(segmentActivationRepository.findMaximumActivationNumber(segmentId)).isEqualTo(1);

        CommercialSegment active = segmentRepository.findById(segmentId).orElseThrow();
        CountDownLatch revisionStart = new CountDownLatch(1);
        CompletableFuture<Integer> firstRevision = CompletableFuture.supplyAsync(
                () -> revisionStatus(token, segmentId, active.getVersion(), revisionStart));
        CompletableFuture<Integer> secondRevision = CompletableFuture.supplyAsync(
                () -> revisionStatus(token, segmentId, active.getVersion(), revisionStart));
        revisionStart.countDown();
        assertThat(List.of(firstRevision.join(), secondRevision.join()))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(segmentRepository.findByLineageIdAndStatus(
                active.getLineageId(), com.hiveapp.platform.client.plan.domain.constant
                        .CommercialSegmentStatus.DRAFT)).hasSize(1);

        CommercialSegment abandoned = segmentRepository.findByLineageIdAndStatus(
                active.getLineageId(), com.hiveapp.platform.client.plan.domain.constant
                        .CommercialSegmentStatus.DRAFT).getFirst();
        mockMvc.perform(post("/api/admin/segments/{id}/archive", abandoned.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CommercialSegmentRequests.VersionReason(
                                        abandoned.getVersion(), "Abandon the unused successor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("ARCHIVED"));
        mockMvc.perform(post("/api/admin/segments/{id}/revisions", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CommercialSegmentRequests.VersionReason(
                                        active.getVersion(), "Replace the abandoned successor"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.revisionNumber").value(3));
    }

    @Test
    void concurrentEquivalentNamesNeverCreateDuplicateCodes() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("segment-code-collision");
        String name = "Same generated code " + UUID.randomUUID();
        var request = explicitRequest(name, Set.of(account.getId()));
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                () -> creationStatus(token, request, start));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                () -> creationStatus(token, request, start));
        start.countDown();
        assertThat(List.of(first.join(), second.join()))
                .allMatch(status -> status == 201 || status == 409)
                .contains(201);
        List<CommercialSegment> created = segmentRepository.findAll().stream()
                .filter(segment -> segment.getName().equals(name)).toList();
        assertThat(created).isNotEmpty();
        assertThat(created.stream().map(CommercialSegment::getCode).distinct().count())
                .isEqualTo(created.size());
    }

    @Test
    void revisionComparisonAndSegmentPolicyUseTheFrozenActivation() throws Exception {
        String token = loginAdminAndGetToken();
        Account first = registerAccount("segment-policy-first");
        Account second = registerAccount("segment-policy-second");
        JsonNode draft = createSegment(token,
                explicitRequest("Policy audience Segment", Set.of(first.getId())));
        UUID segmentId = id(draft);
        JsonNode active = activate(token, segmentId, preview(token, segmentId), "Publish policy audience");

        JsonNode successor = responseJson(mockMvc.perform(post("/api/admin/segments/{id}/revisions", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(active, "Prepare expanded revision")))
                .andExpect(status().isCreated()));
        UUID successorId = id(successor);
        JsonNode unchangedComparison = responseJson(mockMvc.perform(get(
                                "/api/admin/segments/{id}/compare/{other}", segmentId, successorId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        assertThat(unchangedComparison.get("changedFields").toString())
                .doesNotContain("DEFINITION");
        CommercialSegmentRequests.Update successorUpdate = new CommercialSegmentRequests.Update(
                successor.at("/summary/version").asLong(), "Expanded policy audience", null,
                CommercialSegmentKind.EXPLICIT_ACCOUNTS, CommercialSegmentSource.MANUAL,
                "Add another reviewed Account", new CommercialSegmentRequests.Definition(
                        Set.of(first.getId(), second.getId()), null));
        mockMvc.perform(put("/api/admin/segments/{id}", successorId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(successorUpdate)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/segments/{id}/compare/{other}", segmentId, successorId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sameLineage").value(true))
                .andExpect(jsonPath("$.directSuccessor").value(true))
                .andExpect(jsonPath("$.changedFields", hasItem("DEFINITION")));

        CommercialPolicyRequests.Create policyRequest = new CommercialPolicyRequests.Create(
                "Frozen Segment restriction", null, Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600), CommercialPolicySource.MARKETING, 10,
                "Apply to the published exact Segment revision", null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.SEGMENT, null, Set.of(), null,
                        active.at("/summary/code").asText().toLowerCase(java.util.Locale.ROOT)),
                List.of(new CommercialPolicyRequests.Effect(
                        CommercialPolicyEffectType.BLOCK_FEATURE, null, null, "platform.staff",
                        null, null, null, null, null, null, null, null)));
        JsonNode policy = responseJson(mockMvc.perform(post("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policyRequest)))
                .andExpect(status().isCreated()));
        UUID policyId = UUID.fromString(policy.at("/summary/id").asText());
        JsonNode policyPreview = responseJson(mockMvc.perform(get(
                                "/api/admin/commercial-policies/{id}/activation-preview", policyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        assertThat(policyPreview.get("affectedAccountCount").asInt()).isOne();
        mockMvc.perform(post("/api/admin/commercial-policies/{id}/activate", policyId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.Activation(
                                policyPreview.get("expectedVersion").asLong(),
                                "Activate against immutable Segment audience",
                                policyPreview.get("previewToken").asText()))))
                .andExpect(status().isOk());
        UUID policyActivationId = policyActivationRepository
                .findTopByPolicy_IdOrderByActivationNumberDesc(policyId).orElseThrow().getId();
        assertThat(policyActivationRepository.findSnapshotAccountIds(policyActivationId))
                .containsExactly(first.getId());
        assertThat(policyRepository.countBySegmentReference(active.at("/summary/code").asText())).isOne();
        assertThat(policyRepository.findDetailById(policyId).orElseThrow().getSegmentReference())
                .isEqualTo(active.at("/summary/code").asText());

        mockMvc.perform(post("/api/admin/segments/{id}/archive", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(active, "Referenced Segment must remain resolvable")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void listAndCriteriaPreviewQueriesStayConstantWithRowGrowth() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("segment-query-count");
        for (int index = 0; index < 6; index++) {
            createSegment(token, explicitRequest(
                    "Segment baseline " + index + " " + UUID.randomUUID(), Set.of(account.getId())));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .param("page", "0").param("size", "5")
                        .param("sort", "code").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));
        long listBaseline = statistics.getPrepareStatementCount();

        for (int index = 0; index < 12; index++) {
            createSegment(token, explicitRequest(
                    "Segment growth " + index + " " + UUID.randomUUID(), Set.of(account.getId())));
        }
        statistics.clear();
        mockMvc.perform(get("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .param("page", "0").param("size", "5")
                        .param("sort", "code").param("direction", "asc"))
                .andExpect(status().isOk());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(listBaseline);

        JsonNode typed = createSegment(token, criteriaRequest("Bounded database resolver",
                new CommercialSegmentRequests.Criteria(Set.of(), Set.of(SubscriptionStatus.ACTIVE),
                        Set.of(), Set.of(), null, null, Set.of())));
        statistics.clear();
        preview(token, id(typed));
        long previewBaseline = statistics.getPrepareStatementCount();
        for (int index = 0; index < 6; index++) registerAccount("segment-query-account-" + index);
        statistics.clear();
        preview(token, id(typed));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(previewBaseline);

        mockMvc.perform(get("/api/admin/segments")
                        .header("Authorization", bearer(token)).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/segments")
                        .header("Authorization", bearer(token)).param("sort", "owner.email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatableReadEvaluationKeepsContentAndCountOnOneMembershipSnapshot() throws Exception {
        String token = loginAdminAndGetToken();
        JsonNode typed = createSegment(token, criteriaRequest("Repeatable audience",
                new CommercialSegmentRequests.Criteria(Set.of(), Set.of(SubscriptionStatus.ACTIVE),
                        Set.of(), Set.of(), null, null, Set.of())));
        UUID segmentId = id(typed);
        JsonNode reviewedBeforeMembershipChange = preview(token, segmentId);
        CountDownLatch firstEvaluationDone = new CountDownLatch(1);
        CountDownLatch membershipChanged = new CountDownLatch(1);
        CompletableFuture<List<Long>> evaluations = CompletableFuture.supplyAsync(() -> {
            TransactionTemplate repeatable = new TransactionTemplate(transactionManager);
            repeatable.setReadOnly(true);
            repeatable.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            return repeatable.execute(ignored -> {
                CommercialSegment managed = segmentRepository.findDetailById(segmentId).orElseThrow();
                long first = audienceResolver.evaluate(managed, Instant.now()).total();
                firstEvaluationDone.countDown();
                try {
                    if (!membershipChanged.await(15, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting for concurrent membership change");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(exception);
                }
                long second = audienceResolver.evaluate(managed, Instant.now()).total();
                return List.of(first, second);
            });
        });
        assertThat(firstEvaluationDone.await(15, TimeUnit.SECONDS)).isTrue();
        registerAccount("seg-repeat-new");
        membershipChanged.countDown();
        List<Long> stable = evaluations.join();
        assertThat(stable.get(1)).isEqualTo(stable.get(0));
        mockMvc.perform(get("/api/admin/segments/{id}/count", segmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccounts").value(stable.getFirst() + 1));
        assertStale(token, segmentId,
                reviewedBeforeMembershipChange.get("criteriaVersion").asLong(),
                reviewedBeforeMembershipChange.get("previewToken").asText());
    }

    @Test
    void malformedCollectionElementsReturnValidationErrorsBeforeServiceExecution() throws Exception {
        String token = loginAdminAndGetToken();
        String explicit = """
                {"name":"Malformed explicit","kind":"EXPLICIT_ACCOUNTS","source":"MANUAL",
                 "reason":"Reject null ids","definition":{"explicitAccountIds":[null]}}
                """;
        mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(explicit))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String criteria = """
                {"name":"Malformed criteria","kind":"TYPED_CRITERIA","source":"MANUAL",
                 "reason":"Reject null typed values","definition":{"explicitAccountIds":[],
                 "criteria":{"subscriptionStatuses":[null],"currencyCodes":[null],
                 "productHoldings":[null]}}}
                """;
        mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(criteria))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void wideDefinitionsLoadAndValidateWithConstantBoundedQueries() throws Exception {
        String token = loginAdminAndGetToken();
        Set<CommercialSegmentRequests.ProductHolding> holdings = new java.util.LinkedHashSet<>();
        planRepository.findAll().forEach(item -> holdings.add(
                holding(CommercialSegmentProductType.PLAN, item.getCode())));
        addOnRepository.findAll().forEach(item -> holdings.add(
                holding(CommercialSegmentProductType.ADD_ON, item.getCode())));
        quotaPackageRepository.findAll().forEach(item -> holdings.add(
                holding(CommercialSegmentProductType.QUOTA_PACKAGE, item.getCode())));
        Set<String> currencies = IntStream.range(0, 20)
                .mapToObj(index -> "A" + (char) ('A' + index / 26) + (char) ('A' + index % 26))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        CommercialSegmentRequests.Criteria wide = new CommercialSegmentRequests.Criteria(
                planRepository.findAll().stream().map(item -> item.getId()).collect(Collectors.toSet()),
                Set.of(SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE,
                        SubscriptionStatus.PAST_DUE, SubscriptionStatus.SUSPENDED),
                currencies, Set.of(BillingCycle.values()), Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"), holdings);
        JsonNode wideSegment = createSegment(token, criteriaRequest("Maximum-width criteria", wide));
        JsonNode smallSegment = createSegment(token, criteriaRequest("Small criteria",
                new CommercialSegmentRequests.Criteria(Set.of(), Set.of(SubscriptionStatus.ACTIVE),
                        Set.of(), Set.of(), null, null, Set.of())));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/api/admin/segments/{id}", id(smallSegment))
                        .header("Authorization", bearer(token))).andExpect(status().isOk());
        long smallQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        mockMvc.perform(get("/api/admin/segments/{id}", id(wideSegment))
                        .header("Authorization", bearer(token))).andExpect(status().isOk());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(smallQueries);

        Set<CommercialSegmentRequests.ProductHolding> manyAddOns = addOnRepository.findAll().stream()
                .map(item -> holding(CommercialSegmentProductType.ADD_ON, item.getCode()))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        manyAddOns.add(holding(CommercialSegmentProductType.ADD_ON, "DOES_NOT_EXIST"));
        statistics.clear();
        postMissingHolding(token, Set.of(holding(
                CommercialSegmentProductType.ADD_ON, "DOES_NOT_EXIST")));
        long oneCodeQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        postMissingHolding(token, manyAddOns);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(oneCodeQueries);
    }

    private CommercialSegmentRequests.Create explicitRequest(String name, Set<UUID> accountIds) {
        return new CommercialSegmentRequests.Create(name, "Explicit reviewed Account audience",
                CommercialSegmentKind.EXPLICIT_ACCOUNTS, CommercialSegmentSource.MANUAL,
                "Define an operational Segment", new CommercialSegmentRequests.Definition(
                        accountIds, null));
    }

    private CommercialSegmentRequests.Create criteriaRequest(
            String name,
            CommercialSegmentRequests.Criteria criteria
    ) {
        return new CommercialSegmentRequests.Create(name, "Closed typed commercial criteria",
                CommercialSegmentKind.TYPED_CRITERIA, CommercialSegmentSource.MANUAL,
                "Define a database-resolved Segment", new CommercialSegmentRequests.Definition(
                        Set.of(), criteria));
    }

    private CommercialSegmentRequests.ProductHolding holding(
            CommercialSegmentProductType type,
            String code
    ) {
        return new CommercialSegmentRequests.ProductHolding(type, code);
    }

    private JsonNode createSegment(String token, CommercialSegmentRequests.Create request) throws Exception {
        return responseJson(mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
    }

    private JsonNode preview(String token, UUID segmentId) throws Exception {
        return responseJson(mockMvc.perform(get("/api/admin/segments/{id}/preview", segmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
    }

    private JsonNode activate(String token, UUID segmentId, JsonNode preview, String reason)
            throws Exception {
        return responseJson(mockMvc.perform(post("/api/admin/segments/{id}/activate", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialSegmentRequests.Activation(
                                preview.get("criteriaVersion").asLong(), reason,
                                preview.get("previewToken").asText()))))
                .andExpect(status().isOk()));
    }

    private void assertInvalidCriteria(
            String token,
            String name,
            CommercialSegmentRequests.Criteria criteria
    ) throws Exception {
        mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criteriaRequest(name, criteria))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void postMissingHolding(
            String token,
            Set<CommercialSegmentRequests.ProductHolding> holdings
    ) throws Exception {
        mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criteriaRequest(
                                "Missing holding " + UUID.randomUUID(),
                                new CommercialSegmentRequests.Criteria(Set.of(), Set.of(), Set.of(),
                                        Set.of(), null, null, holdings)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private void assertStale(String token, UUID segmentId, long version, String evidence)
            throws Exception {
        mockMvc.perform(post("/api/admin/segments/{id}/activate", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialSegmentRequests.Activation(
                                version, "Reject invalid review evidence", evidence))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ACTIVATION_PREVIEW"));
    }

    private int activationStatus(String token, UUID segmentId, JsonNode preview, CountDownLatch start) {
        try {
            start.await();
            return mockMvc.perform(post("/api/admin/segments/{id}/activate", segmentId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CommercialSegmentRequests.Activation(
                                            preview.get("criteriaVersion").asLong(),
                                            "Concurrent activation", preview.get("previewToken").asText()))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private int revisionStatus(String token, UUID segmentId, long version, CountDownLatch start) {
        try {
            start.await();
            return mockMvc.perform(post("/api/admin/segments/{id}/revisions", segmentId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CommercialSegmentRequests.VersionReason(
                                            version, "Concurrent successor request"))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private int creationStatus(
            String token,
            CommercialSegmentRequests.Create request,
            CountDownLatch start
    ) {
        try {
            start.await();
            return mockMvc.perform(post("/api/admin/segments")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private void replaceHoldings(UUID accountId, String addOnCode, String packageCode) {
        transactionTemplate.executeWithoutResult(ignored -> {
            var subscription = subscriptionRepository.findUsableByAccountId(accountId).orElseThrow();
            SubscriptionEntitlementSnapshot existing = subscription.getEntitlementSnapshot();
            subscription.setEntitlementSnapshot(new SubscriptionEntitlementSnapshot(
                    existing.schemaVersion(), existing.planCode(), existing.planName(),
                    existing.planDefinitionVersion(), existing.basePrice(), existing.currencyCode(),
                    existing.billingCycle(), existing.effectiveFrom(), existing.effectiveUntil(),
                    existing.features(), List.of(new SubscriptionAddOnSnapshot(
                            addOnCode, addOnCode, 1, BigDecimal.ONE, existing.currencyCode(),
                            existing.billingCycle(), List.of("platform.staff"))),
                    List.of(new SubscriptionQuotaPackageSnapshot(
                            packageCode, packageCode, 1, "platform.staff", "members", 100, 1,
                            BigDecimal.ONE, existing.currencyCode(), existing.billingCycle())),
                    existing.planPriceEntryId(), existing.commercialPolicyEvaluation()));
            subscriptionRepository.saveAndFlush(subscription);
        });
    }

    private Account registerAccount(String marker) throws Exception {
        String email = marker + "-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, CLIENT_PASSWORD, "Segment", "Target", null))))
                .andExpect(status().isCreated());
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        return accountRepository.findByOwner_Id(userId).orElseThrow();
    }

    private UUID id(JsonNode detail) {
        return UUID.fromString(detail.at("/summary/id").asText());
    }

    private String versionReason(JsonNode detail, String reason) throws Exception {
        return objectMapper.writeValueAsString(new CommercialSegmentRequests.VersionReason(
                detail.at("/summary/version").asLong(), reason));
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
