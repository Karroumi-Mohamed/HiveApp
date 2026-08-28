package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentSource;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaign;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegmentActivation;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCampaignAudienceSnapshotRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCampaignRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentActivationRepository;
import com.hiveapp.platform.client.plan.dto.CommercialCampaignRequests;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentRequests;
import com.hiveapp.platform.client.plan.service.CommercialCampaignLifecycleTransitionService;
import com.hiveapp.platform.client.plan.service.CommercialCampaignLifecycleProcessor;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
class CommercialCampaignControlPlaneIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private AccountRepository accountRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CommercialCampaignRepository campaignRepository;
    @Autowired private CommercialCampaignAudienceSnapshotRepository snapshotRepository;
    @Autowired private CommercialSegmentActivationRepository segmentActivationRepository;
    @Autowired private CommercialPreviewTokenService previewTokenService;
    @Autowired private CommercialCatalogVersionService catalogVersionService;
    @Autowired private CommercialCampaignLifecycleTransitionService lifecycleTransitions;
    @Autowired private CommercialCampaignLifecycleProcessor lifecycleProcessor;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void explicitAudienceIsReviewedFrozenPrivateAndFullyOperational() throws Exception {
        String token = loginAdminAndGetToken();
        Account first = registerAccount("campaign-first");
        Account second = registerAccount("campaign-second");
        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(7200);
        JsonNode draft = createCampaign(token, create("Targeted launch", start, end,
                audience(CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS,
                        Set.of(first.getId(), second.getId()), null, null)));
        UUID campaignId = id(draft);

        JsonNode preview = preview(token, campaignId);
        assertThat(preview.get("targetedAccountCount").asInt()).isEqualTo(2);
        assertThat(preview.get("registryVersion").asText()).isNotBlank();
        assertThat(preview.get("sample").toString()).contains(first.getId().toString())
                .doesNotContain(first.getName());
        JsonNode scheduled = schedule(token, campaignId, preview, "Accept the exact launch audience");
        assertThat(scheduled.at("/summary/status").asText()).isEqualTo("SCHEDULED");
        assertThat(auditLogRepository.findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc(
                "COMMERCIAL_CAMPAIGN_ADMIN", campaignId.toString()))
                .filteredOn(log -> "platform.campaigns.schedule".equals(log.getAction()))
                .singleElement().satisfies(log -> {
                    assertThat(log.getRequestData()).contains("[REDACTED]");
                    assertThat(log.getRequestData()).doesNotContain(preview.get("previewToken").asText());
                });

        first.setName("Renamed after schedule");
        accountRepository.saveAndFlush(first);
        registerAccount("campaign-later");
        JsonNode frozenAudience = response(mockMvc.perform(
                        get("/api/admin/campaigns/{id}/audience", campaignId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.immutableAccountCount").value(2))
                .andExpect(jsonPath("$.accounts.content", hasSize(2)))
                .andExpect(jsonPath("$.accounts.content[0].accountName").doesNotExist()));
        assertThat(frozenAudience.get("reviewedByActorUserId").asText()).isNotBlank();
        assertThat(frozenAudience.get("evidenceExpiresAt").asText()).isNotBlank();
        assertThat(frozenAudience.get("campaignVersion").asLong())
                .isEqualTo(preview.get("campaignVersion").asLong());
        assertThat(frozenAudience.get("catalogRevision").asLong()).isNotNegative();
        assertThat(frozenAudience.get("registryVersion").asText())
                .isEqualTo(preview.get("registryVersion").asText());
        assertThat(frozenAudience.get("audienceFingerprint").asText())
                .isEqualTo(preview.get("fingerprint").asText());
        assertThat(frozenAudience.get("reason").asText())
                .isEqualTo("Accept the exact launch audience");
        assertThat(frozenAudience.has("previewToken")).isFalse();
        mockMvc.perform(get("/api/admin/campaigns/{id}/audience-identities", campaignId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.content[*].accountName", hasItem(first.getName())));

        lifecycleTransitions.processDueStart(campaignId, start.plusSeconds(1));
        JsonNode active = getCampaign(token, campaignId);
        assertThat(active.at("/summary/status").asText()).isEqualTo("ACTIVE");
        String firstActivatedAt = active.get("activatedAt").asText();
        JsonNode paused = lifecycle(token, campaignId, "pause", active, "Pause the campaign");
        JsonNode resumed = lifecycle(token, campaignId, "resume", paused, "Resume the campaign");
        assertThat(resumed.get("activatedAt").asText()).isEqualTo(firstActivatedAt);
        assertThat(resumed.get("resumedAt").asText()).isNotBlank();
        JsonNode ended = lifecycle(token, campaignId, "end", resumed, "End the campaign");
        JsonNode archived = lifecycle(token, campaignId, "archive", ended, "Retain campaign history");
        assertThat(archived.at("/summary/status").asText()).isEqualTo("ARCHIVED");
        assertThat(snapshotRepository.findWithAccountsByCampaignId(campaignId).orElseThrow()
                .getAccountIds()).containsExactlyInAnyOrder(first.getId(), second.getId());
        mockMvc.perform(get("/api/admin/campaigns/{id}/history", campaignId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].reason", hasItem("Retain campaign history")));
    }

    @Test
    void publicCampaignNeverSnapshotsThePlatformAccountPopulation() throws Exception {
        String token = loginAdminAndGetToken();
        registerAccount("campaign-public-one");
        registerAccount("campaign-public-two");
        Instant start = Instant.now().plusSeconds(3600);
        JsonNode draft = createCampaign(token, create("Public announcement", start,
                start.plusSeconds(3600), audience(
                        CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null)));
        UUID id = id(draft);
        JsonNode preview = preview(token, id);
        assertThat(preview.get("publicAudience").asBoolean()).isTrue();
        assertThat(preview.get("targetedAccountCount").isNull()).isTrue();
        assertThat(preview.get("sample")).isEmpty();
        schedule(token, id, preview, "Publish without enumerating every Account");

        var snapshot = snapshotRepository.findWithAccountsByCampaignId(id).orElseThrow();
        assertThat(snapshot.getAffectedAccountCount()).isZero();
        assertThat(snapshot.getAccountIds()).isEmpty();
        mockMvc.perform(get("/api/admin/campaigns/{id}/audience", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicAudience").value(true))
                .andExpect(jsonPath("$.accounts.content").isEmpty());
    }

    @Test
    void campaignBindsExactActiveSegmentActivationAndThenOwnsItsSnapshot() throws Exception {
        String token = loginAdminAndGetToken();
        Account target = registerAccount("campaign-segment");
        JsonNode segment = createSegment(token, target.getId());
        UUID segmentId = UUID.fromString(segment.at("/summary/id").asText());
        JsonNode segmentPreview = response(mockMvc.perform(get("/api/admin/segments/{id}/preview", segmentId)
                        .header("Authorization", bearer(token))).andExpect(status().isOk()));
        JsonNode activeSegment = response(mockMvc.perform(post("/api/admin/segments/{id}/activate", segmentId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialSegmentRequests.Activation(
                                segmentPreview.get("criteriaVersion").asLong(), "Freeze Campaign audience",
                                segmentPreview.get("previewToken").asText()))))
                .andExpect(status().isOk()));
        UUID activationId = segmentActivationRepository
                .findTopBySegment_IdOrderByActivationNumberDesc(segmentId).orElseThrow().getId();
        mockMvc.perform(get("/api/admin/campaigns/segment-choices/selected")
                        .header("Authorization", bearer(token))
                        .param("segmentId", segmentId.toString())
                        .param("activationId", activationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activationId").value(activationId.toString()))
                .andExpect(jsonPath("$.state").value("AVAILABLE"));
        Instant start = Instant.now().plusSeconds(3600);
        JsonNode campaign = createCampaign(token, create("Segment campaign", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.SEGMENT,
                        Set.of(), segmentId, activationId)));
        UUID campaignId = id(campaign);

        mockMvc.perform(post("/api/admin/segments/{id}/archive", segmentId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialSegmentRequests.VersionReason(
                                activeSegment.at("/summary/version").asLong(),
                                "Draft Campaign still needs the Segment"))))
                .andExpect(status().isConflict());

        JsonNode scheduled = schedule(token, campaignId, preview(token, campaignId),
                "Freeze the Segment activation into the Campaign");
        CommercialSegmentActivation firstActivation = segmentActivationRepository
                .findByIdAndSegment_Id(activationId, segmentId).orElseThrow();
        CommercialSegmentActivation newerActivation = CommercialSegmentActivation.record(
                firstActivation.getSegment(), firstActivation.getActivationNumber() + 1,
                firstActivation.getActorUserId(), Instant.now(), Instant.now().plusSeconds(300),
                firstActivation.getCriteriaVersion(), firstActivation.getAssessmentFingerprint(),
                "Refresh the exact Segment selection", Set.of(target.getId()));
        segmentActivationRepository.saveAndFlush(newerActivation);
        mockMvc.perform(get("/api/admin/campaigns/segment-choices/selected")
                        .header("Authorization", bearer(token))
                        .param("segmentId", segmentId.toString())
                        .param("activationId", activationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activationId").value(activationId.toString()))
                .andExpect(jsonPath("$.state").value("ACTIVATION_SUPERSEDED"));
        mockMvc.perform(get("/api/admin/campaigns/segment-choices")
                        .header("Authorization", bearer(token))
                        .param("query", segment.at("/summary/code").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].activationId")
                        .value(newerActivation.getId().toString()))
                .andExpect(jsonPath("$.content[0].state").value("AVAILABLE"));
        mockMvc.perform(post("/api/admin/segments/{id}/archive", segmentId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialSegmentRequests.VersionReason(
                                activeSegment.at("/summary/version").asLong(),
                                "Campaign now owns its immutable audience"))))
                .andExpect(status().isOk());
        assertThat(snapshotRepository.findWithAccountsByCampaignId(campaignId).orElseThrow()
                .getAccountIds()).containsExactly(target.getId());
        assertThat(scheduled.at("/audience/segmentActivationId").asText())
                .isEqualTo(activationId.toString());
    }

    @Test
    void scheduleRejectsAStaleSegmentActivationWithoutFreezingAnyAudience() throws Exception {
        String token = loginAdminAndGetToken();
        Account target = registerAccount("campaign-stale-segment");
        JsonNode segment = createSegment(token, target.getId());
        UUID segmentId = UUID.fromString(segment.at("/summary/id").asText());
        JsonNode segmentPreview = response(mockMvc.perform(
                        get("/api/admin/segments/{id}/preview", segmentId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        mockMvc.perform(post("/api/admin/segments/{id}/activate", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CommercialSegmentRequests.Activation(
                                        segmentPreview.get("criteriaVersion").asLong(),
                                        "Freeze the first Segment audience",
                                        segmentPreview.get("previewToken").asText()))))
                .andExpect(status().isOk());
        CommercialSegmentActivation original = segmentActivationRepository
                .findTopBySegment_IdOrderByActivationNumberDesc(segmentId).orElseThrow();
        Instant start = Instant.now().plusSeconds(3600);
        JsonNode campaign = createCampaign(token, create("Stale Segment campaign", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.SEGMENT,
                        Set.of(), segmentId, original.getId())));
        JsonNode campaignPreview = preview(token, id(campaign));

        CommercialSegmentActivation replacement = CommercialSegmentActivation.record(
                original.getSegment(), original.getActivationNumber() + 1,
                original.getActorUserId(), Instant.now(), Instant.now().plusSeconds(300),
                original.getCriteriaVersion(), original.getAssessmentFingerprint(),
                "Supersede the reviewed Segment activation", Set.of(target.getId()));
        segmentActivationRepository.saveAndFlush(replacement);

        JsonNode staleDetail = getCampaign(token, id(campaign));
        assertThat(actionNames(staleDetail)).doesNotContain("SCHEDULE");
        assertThat(staleDetail.at("/summary/blockedActions/SCHEDULE").toString())
                .contains("SEGMENT_ACTIVATION_STALE");

        mockMvc.perform(post("/api/admin/campaigns/{id}/schedule", id(campaign))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequest(campaign.at("/summary/version").asLong(),
                                campaignPreview.get("previewToken").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("SEGMENT_ACTIVATION_STALE")));
        assertThat(snapshotRepository.existsByCampaign_Id(id(campaign))).isFalse();
        assertThat(campaignRepository.findById(id(campaign)).orElseThrow().getStatus().name())
                .isEqualTo("DRAFT");
    }

    @Test
    void scheduleEvidenceRejectsCrossActorCrossResourceAndStaleCampaignTerms() throws Exception {
        String token = loginAdminAndGetToken();
        Account target = registerAccount("campaign-evidence");
        Instant start = Instant.now().plusSeconds(3600);
        JsonNode first = createCampaign(token, create("Evidence first", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS,
                        Set.of(target.getId()), null, null)));
        JsonNode second = createCampaign(token, create("Evidence second", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS,
                        Set.of(target.getId()), null, null)));
        JsonNode reviewed = preview(token, id(first));

        mockMvc.perform(post("/api/admin/campaigns/{id}/schedule", id(second))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequest(second.at("/summary/version").asLong(),
                                reviewed.get("previewToken").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_SCHEDULE_PREVIEW"));

        CommercialCampaign managed = campaignRepository.findDetailById(id(first)).orElseThrow();
        String crossActor = previewTokenService.issue(CommercialPreviewKind.COMMERCIAL_CAMPAIGN_SCHEDULE,
                managed.getId(), managed.getVersion(), UUID.randomUUID(),
                catalogVersionService.currentRevision(),
                registryCatalogVersionService.currentVersion(),
                reviewed.get("fingerprint").asText(), Instant.now()).token();
        mockMvc.perform(post("/api/admin/campaigns/{id}/schedule", id(first))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequest(first.at("/summary/version").asLong(), crossActor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_SCHEDULE_PREVIEW"));

        CommercialCampaignRequests.Update update = new CommercialCampaignRequests.Update(
                first.at("/summary/version").asLong(), "Evidence first changed", null,
                start, start.plusSeconds(7200), CommercialCampaignSource.MARKETING,
                "Change the reviewed window", audience(CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS,
                Set.of(target.getId()), null, null));
        JsonNode updated = response(mockMvc.perform(put("/api/admin/campaigns/{id}", id(first))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update))).andExpect(status().isOk()));
        mockMvc.perform(post("/api/admin/campaigns/{id}/schedule", id(first))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequest(updated.get("version").asLong(),
                                reviewed.get("previewToken").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_SCHEDULE_PREVIEW"));
    }

    @Test
    void scheduleEvidenceRejectsWrongOperationExpiryAndCatalogDriftWithoutPartialWrites()
            throws Exception {
        String token = loginAdminAndGetToken();
        Account target = registerAccount("campaign-evidence-fence");
        Instant start = Instant.now().plusSeconds(3600);
        JsonNode draft = createCampaign(token, create("Evidence fences", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS,
                        Set.of(target.getId()), null, null)));
        UUID campaignId = id(draft);
        JsonNode reviewed = preview(token, campaignId);
        CommercialCampaign managed = campaignRepository.findDetailById(campaignId).orElseThrow();
        UUID actorUserId = adminUserRepository.findByUser_Email(ADMIN_EMAIL).orElseThrow()
                .getUser().getId();

        String wrongOperation = previewTokenService.issue(CommercialPreviewKind.PLAN_ACTIVATION,
                campaignId, managed.getVersion(), actorUserId,
                catalogVersionService.currentRevision(),
                registryCatalogVersionService.currentVersion(),
                reviewed.get("fingerprint").asText(), Instant.now()).token();
        assertRejectedSchedule(token, draft, wrongOperation);

        String wrongRegistry = previewTokenService.issue(
                CommercialPreviewKind.COMMERCIAL_CAMPAIGN_SCHEDULE,
                campaignId, managed.getVersion(), actorUserId,
                catalogVersionService.currentRevision(),
                registryCatalogVersionService.currentVersion() + "-stale",
                reviewed.get("fingerprint").asText(), Instant.now()).token();
        assertRejectedSchedule(token, draft, wrongRegistry);

        String expired = previewTokenService.issue(
                CommercialPreviewKind.COMMERCIAL_CAMPAIGN_SCHEDULE,
                campaignId, managed.getVersion(), actorUserId,
                catalogVersionService.currentRevision(),
                registryCatalogVersionService.currentVersion(),
                reviewed.get("fingerprint").asText(), Instant.now().minusSeconds(600)).token();
        assertRejectedSchedule(token, draft, expired);

        createCampaign(token, create("Unrelated catalogue mutation", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.PUBLIC,
                        Set.of(), null, null)));
        assertRejectedSchedule(token, draft, reviewed.get("previewToken").asText());

        assertThat(snapshotRepository.existsByCampaign_Id(campaignId)).isFalse();
        assertThat(campaignRepository.findById(campaignId).orElseThrow().getStatus().name())
                .isEqualTo("DRAFT");
    }

    @Test
    void concurrentReviewedScheduleCreatesExactlyOneSnapshot() throws Exception {
        String token = loginAdminAndGetToken();
        Account target = registerAccount("campaign-concurrent");
        Instant start = Instant.now().plusSeconds(3600);
        JsonNode draft = createCampaign(token, create("Concurrent schedule", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS,
                        Set.of(target.getId()), null, null)));
        UUID id = id(draft);
        JsonNode preview = preview(token, id);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                    () -> scheduleStatus(token, id, draft, preview, go), executor);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                    () -> scheduleStatus(token, id, draft, preview, go), executor);
            go.countDown();
            assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(snapshotRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(snapshotRepository.findByCampaign_Id(id)).isPresent();
    }

    @Test
    void dueProcessingStartsAndEndsIndependentlyWithSystemAudit() throws Exception {
        String token = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(3600);
        JsonNode draft = createCampaign(token, create("Due lifecycle", start, end,
                audience(CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null)));
        UUID id = id(draft);
        schedule(token, id, preview(token, id), "Schedule lifecycle processing");

        long revisionBeforeNoOp = catalogVersionService.currentRevision();
        int auditsBeforeNoOp = campaignAudits(id).size();
        assertThat(lifecycleTransitions.processDueStart(id, start.minusSeconds(1))).isFalse();
        assertThat(lifecycleTransitions.processDueStart(UUID.randomUUID(), start.plusSeconds(1)))
                .isFalse();
        assertThat(catalogVersionService.currentRevision()).isEqualTo(revisionBeforeNoOp);
        assertThat(campaignAudits(id)).hasSize(auditsBeforeNoOp);

        lifecycleProcessor.processDue(start.minusSeconds(1));
        assertThat(campaignRepository.findById(id).orElseThrow().getStatus().name())
                .isEqualTo("SCHEDULED");
        lifecycleProcessor.processDue(start.plusSeconds(1));
        assertThat(campaignRepository.findById(id).orElseThrow().getStatus().name())
                .isEqualTo("ACTIVE");
        long revisionAfterStart = catalogVersionService.currentRevision();
        int auditsAfterStart = campaignAudits(id).size();
        assertThat(lifecycleTransitions.processDueStart(id, start.plusSeconds(2))).isFalse();
        assertThat(catalogVersionService.currentRevision()).isEqualTo(revisionAfterStart);
        assertThat(campaignAudits(id)).hasSize(auditsAfterStart);
        lifecycleProcessor.processDue(end.plusSeconds(1));
        assertThat(campaignRepository.findById(id).orElseThrow().getStatus().name())
                .isEqualTo("ENDED");
        assertThat(campaignAudits(id))
                .anySatisfy(log -> {
                    assertThat(log.getActorSurface()).isEqualTo(AuditActorSurface.SYSTEM);
                    assertThat(log.getActorUserId()).isNull();
                });

        JsonNode expiredDraft = createCampaign(token, create("Expired before processing",
                start, end, audience(CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null)));
        UUID expiredId = id(expiredDraft);
        schedule(token, expiredId, preview(token, expiredId), "Schedule an expiring Campaign");
        assertThat(lifecycleTransitions.processDueStart(expiredId, end.plusSeconds(1))).isTrue();
        assertThat(campaignRepository.findById(expiredId).orElseThrow().getStatus().name())
                .isEqualTo("ENDED");
        assertThat(campaignAudits(expiredId))
                .filteredOn(log -> "platform.campaigns.process_due_end".equals(log.getAction()))
                .singleElement().satisfies(log -> {
                    assertThat(log.getRequestData()).contains("SCHEDULED");
                    assertThat(log.getResultData()).contains("ENDED");
                });
    }

    @Test
    void lineageRevisionsDuplicateAndDraftDeletionRemainSafe() throws Exception {
        String token = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3600);
        JsonNode draft = createCampaign(token, create("Lineage campaign", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.PUBLIC,
                        Set.of(), null, null)));
        JsonNode draftCopy = response(mockMvc.perform(post("/api/admin/campaigns/{id}/duplicate", id(draft))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialCampaignRequests.Duplicate(
                                draft.at("/summary/version").asLong(), "Draft source child",
                                "Prove source deletion is guarded"))))
                .andExpect(status().isCreated()));
        mockMvc.perform(delete("/api/admin/campaigns/{id}", id(draft))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(draft, "Cannot orphan provenance")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mockMvc.perform(delete("/api/admin/campaigns/{id}", id(draftCopy))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(draftCopy, "Remove derived draft first")))
                .andExpect(status().isNoContent());
        JsonNode scheduled = schedule(token, id(draft), preview(token, id(draft)), "Publish revision one");
        JsonNode successor = response(mockMvc.perform(post("/api/admin/campaigns/{id}/revisions", id(draft))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(scheduled, "Prepare revision two")))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/admin/campaigns/{id}/revisions", id(draft))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(scheduled, "Reject second draft successor")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_SUCCESSOR_EXISTS"));
        JsonNode duplicateResult = response(mockMvc.perform(post("/api/admin/campaigns/{id}/duplicate", id(draft))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialCampaignRequests.Duplicate(
                                scheduled.at("/summary/version").asLong(), "Independent campaign",
                                "Create a new lineage")))).andExpect(status().isCreated()));
        JsonNode duplicate = getCampaign(token, id(duplicateResult));
        assertThat(duplicate.at("/summary/lineageId").asText())
                .isNotEqualTo(scheduled.at("/summary/lineageId").asText());
        assertThat(duplicate.get("sourceCampaignId").asText()).isEqualTo(id(draft).toString());
        mockMvc.perform(get("/api/admin/campaigns/{id}/compare/{comparedId}",
                        id(draft), id(successor)).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sameLineage").value(true))
                .andExpect(jsonPath("$.directSuccessor").value(true));
        mockMvc.perform(get("/api/admin/campaigns/{id}/compare/{comparedId}",
                        id(draft), id(duplicate)).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sameLineage").value(false))
                .andExpect(jsonPath("$.directSuccessor").value(false));
        mockMvc.perform(delete("/api/admin/campaigns/{id}", id(successor))
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(successor, "Discard unused successor")))
                .andExpect(status().isNoContent());
    }

    @Test
    void draftSuccessorAdvertisesSchedulingOnlyAfterTheLiveRevisionEnds() throws Exception {
        String token = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3600);
        JsonNode draft = createCampaign(token, create("Executable actions", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.PUBLIC,
                        Set.of(), null, null)));
        JsonNode scheduled = schedule(token, id(draft), preview(token, id(draft)),
                "Schedule the first revision");
        JsonNode successor = response(mockMvc.perform(
                        post("/api/admin/campaigns/{id}/revisions", id(draft))
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(versionReason(scheduled, "Prepare the successor")))
                .andExpect(status().isCreated()));

        JsonNode blocked = getCampaign(token, id(successor));
        assertThat(actionNames(blocked)).doesNotContain("SCHEDULE");
        assertThat(blocked.at("/summary/blockedActions/SCHEDULE").toString())
                .contains("LIVE_LINEAGE_REVISION_EXISTS");

        lifecycle(token, id(draft), "end", scheduled, "End the first revision");
        JsonNode executable = getCampaign(token, id(successor));
        assertThat(actionNames(executable)).contains("SCHEDULE");
        assertThat(executable.at("/summary/blockedActions/SCHEDULE").toString())
                .doesNotContain("LIVE_LINEAGE_REVISION_EXISTS");
    }

    @Test
    void concurrentRevisionCreationHasOneWinnerAndOneStableConflict() throws Exception {
        String token = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3600);
        JsonNode draft = createCampaign(token, create("Concurrent revision", start,
                start.plusSeconds(3600), audience(CommercialCampaignAudienceMode.PUBLIC,
                        Set.of(), null, null)));
        JsonNode scheduled = schedule(token, id(draft), preview(token, id(draft)),
                "Publish source revision");
        String body = versionReason(scheduled, "Create one successor");
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                    () -> revisionStatus(token, id(draft), body, go), executor);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                    () -> revisionStatus(token, id(draft), body, go), executor);
            go.countDown();
            assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(campaignRepository.findAllByLineageId(
                UUID.fromString(scheduled.at("/summary/lineageId").asText()),
                org.springframework.data.domain.Pageable.unpaged()).getContent())
                .filteredOn(item -> item.getStatus().name().equals("DRAFT"))
                .hasSize(1);
    }

    @Test
    void campaignListQueriesAndStablePagesStayBoundedAsRowsGrow() throws Exception {
        String token = loginAdminAndGetToken();
        String marker = "Paged Campaign " + UUID.randomUUID();
        Instant start = Instant.now().plusSeconds(3600);
        for (int index = 0; index < 6; index++) {
            createCampaign(token, create(marker, start, start.plusSeconds(3600),
                    audience(CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null)));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        JsonNode firstPage = listCampaigns(token, marker, 0, 3);
        long baseline = statistics.getPrepareStatementCount();
        JsonNode secondPage = listCampaigns(token, marker, 1, 3);
        Set<String> ids = new java.util.LinkedHashSet<>();
        firstPage.get("content").forEach(item -> ids.add(item.get("id").asText()));
        secondPage.get("content").forEach(item -> ids.add(item.get("id").asText()));
        assertThat(ids).hasSize(6);

        for (int index = 0; index < 12; index++) {
            createCampaign(token, create(marker + " growth " + index, start,
                    start.plusSeconds(3600), audience(
                            CommercialCampaignAudienceMode.PUBLIC, Set.of(), null, null)));
        }
        statistics.clear();
        listCampaigns(token, marker, 0, 3);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(baseline);
        mockMvc.perform(get("/api/admin/campaigns")
                        .header("Authorization", bearer(token)).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/campaigns")
                        .header("Authorization", bearer(token)).param("sort", "owner.email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void malformedAudienceIsRejectedByValidationRatherThanFailingAsAnInternalError()
            throws Exception {
        String token = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3600);
        String body = """
                {"name":"Malformed audience","startsAt":"%s","endsAt":"%s",
                 "source":"MARKETING","reason":"Validate null account ids",
                 "audience":{"mode":"EXPLICIT_ACCOUNTS","explicitAccountIds":[null]}}
                """.formatted(start, start.plusSeconds(3600));
        mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unreadableJsonAndUnknownEnumsAreStableBadRequestsWithoutParserDetails()
            throws Exception {
        String token = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3600);
        String unknownEnum = """
                {"name":"Unknown source","startsAt":"%s","endsAt":"%s",
                 "source":"NOT_A_REAL_SOURCE","reason":"Exercise request decoding",
                 "audience":{"mode":"PUBLIC","explicitAccountIds":[]}}
                """.formatted(start, start.plusSeconds(3600));

        mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(unknownEnum))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("Request body is malformed or contains an unsupported value"));

        mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("Request body is malformed or contains an unsupported value"));
    }

    private CommercialCampaignRequests.Create create(String name, Instant start, Instant end,
                                                       CommercialCampaignRequests.Audience audience) {
        return new CommercialCampaignRequests.Create(name, "Campaign integration test", start, end,
                CommercialCampaignSource.MARKETING, "Define a controlled Campaign", audience);
    }

    private CommercialCampaignRequests.Audience audience(CommercialCampaignAudienceMode mode,
            Set<UUID> ids, UUID segmentId, UUID activationId) {
        return new CommercialCampaignRequests.Audience(mode, ids, segmentId, activationId);
    }

    private JsonNode createCampaign(String token, CommercialCampaignRequests.Create request)
            throws Exception {
        JsonNode result = response(mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
        return getCampaign(token, id(result));
    }

    private JsonNode preview(String token, UUID id) throws Exception {
        return response(mockMvc.perform(get("/api/admin/campaigns/{id}/schedule-preview", id)
                        .header("Authorization", bearer(token))).andExpect(status().isOk()));
    }

    private JsonNode schedule(String token, UUID id, JsonNode preview, String reason) throws Exception {
        response(mockMvc.perform(post("/api/admin/campaigns/{id}/schedule", id)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialCampaignRequests.Schedule(
                                preview.get("campaignVersion").asLong(), reason,
                                preview.get("previewToken").asText())))).andExpect(status().isOk()));
        return getCampaign(token, id);
    }

    private JsonNode lifecycle(String token, UUID id, String action, JsonNode detail, String reason)
            throws Exception {
        response(mockMvc.perform(post("/api/admin/campaigns/{id}/" + action, id)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(versionReason(detail, reason))).andExpect(status().isOk()));
        return getCampaign(token, id);
    }

    private JsonNode getCampaign(String token, UUID id) throws Exception {
        return response(mockMvc.perform(get("/api/admin/campaigns/{id}", id)
                        .header("Authorization", bearer(token))).andExpect(status().isOk()));
    }

    private JsonNode createSegment(String token, UUID accountId) throws Exception {
        CommercialSegmentRequests.Create request = new CommercialSegmentRequests.Create(
                "Campaign source " + UUID.randomUUID(), null,
                CommercialSegmentKind.EXPLICIT_ACCOUNTS, CommercialSegmentSource.MANUAL,
                "Create Campaign source Segment",
                new CommercialSegmentRequests.Definition(Set.of(accountId), null));
        return response(mockMvc.perform(post("/api/admin/segments")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated()));
    }

    private Account registerAccount(String marker) throws Exception {
        String email = marker + "-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, CLIENT_PASSWORD, "Campaign", "Target", null))))
                .andExpect(status().isCreated());
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        return accountRepository.findByOwner_Id(userId).orElseThrow();
    }

    private int scheduleStatus(String token, UUID id, JsonNode draft, JsonNode preview,
                               CountDownLatch go) {
        try {
            go.await();
            return mockMvc.perform(post("/api/admin/campaigns/{id}/schedule", id)
                            .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CommercialCampaignRequests.Schedule(
                                    draft.at("/summary/version").asLong(), "Concurrent schedule",
                                    preview.get("previewToken").asText()))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void assertRejectedSchedule(String token, JsonNode draft, String previewToken)
            throws Exception {
        mockMvc.perform(post("/api/admin/campaigns/{id}/schedule", id(draft))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequest(
                                draft.at("/summary/version").asLong(), previewToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_SCHEDULE_PREVIEW"));
    }

    private int revisionStatus(String token, UUID id, String body, CountDownLatch go) {
        try {
            go.await();
            return mockMvc.perform(post("/api/admin/campaigns/{id}/revisions", id)
                            .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                            .content(body)).andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private JsonNode listCampaigns(String token, String search, int page, int size) throws Exception {
        return response(mockMvc.perform(get("/api/admin/campaigns")
                        .header("Authorization", bearer(token)).param("search", search)
                        .param("page", Integer.toString(page)).param("size", Integer.toString(size))
                        .param("sort", "name").param("direction", "asc"))
                .andExpect(status().isOk()));
    }

    private String scheduleRequest(long version, String token) throws Exception {
        return objectMapper.writeValueAsString(new CommercialCampaignRequests.Schedule(
                version, "Reject substituted evidence", token));
    }

    private String versionReason(JsonNode detail, String reason) throws Exception {
        return objectMapper.writeValueAsString(new CommercialCampaignRequests.VersionReason(
                detail.has("version") ? detail.get("version").asLong()
                        : detail.at("/summary/version").asLong(), reason));
    }

    private UUID id(JsonNode detail) {
        String value = detail.hasNonNull("campaignId")
                ? detail.get("campaignId").asText() : detail.at("/summary/id").asText();
        return UUID.fromString(value);
    }

    private Set<String> actionNames(JsonNode detail) {
        Set<String> actions = new java.util.LinkedHashSet<>();
        detail.at("/summary/availableActions").forEach(action -> actions.add(action.asText()));
        return actions;
    }

    private JsonNode response(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private List<com.hiveapp.shared.audit.domain.AuditLog> campaignAudits(UUID campaignId) {
        return auditLogRepository.findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc(
                "COMMERCIAL_CAMPAIGN_ADMIN", campaignId.toString());
    }
}
