package com.hiveapp.platform.client.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSegmentChoiceState;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaign;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaignAudienceSnapshot;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegment;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegmentActivation;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCampaignAudienceSnapshotRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCampaignRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentActivationRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentRepository;
import com.hiveapp.platform.client.plan.dto.CommercialCampaignRequests;
import com.hiveapp.platform.client.plan.dto.CommercialCampaignViews;
import com.hiveapp.platform.client.plan.service.CommercialCampaignAdminService;
import com.hiveapp.platform.client.plan.service.CommercialCampaignAudienceResolver;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialCodeGenerator;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.registry.definition.CampaignsFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.exception.DraftSuccessorExistsException;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import com.hiveapp.shared.exception.StaleSchedulePreviewException;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = CampaignsFeature.KEY,
        description = "Commercial Campaign management", guard = PermissionNode.Guard.ON)
public class CommercialCampaignAdminServiceImpl extends PlatformControlFeatureService
        implements CommercialCampaignAdminService {

    private static final String AUDIT_RESOURCE_TYPE = "COMMERCIAL_CAMPAIGN_ADMIN";
    private static final Set<CommercialCampaignStatus> LIVE_STATUSES = Set.of(
            CommercialCampaignStatus.SCHEDULED,
            CommercialCampaignStatus.ACTIVE,
            CommercialCampaignStatus.PAUSED);

    private final CommercialCampaignRepository campaignRepository;
    private final CommercialCampaignAudienceSnapshotRepository snapshotRepository;
    private final CommercialSegmentRepository segmentRepository;
    private final CommercialSegmentActivationRepository segmentActivationRepository;
    private final AccountDirectoryService accountDirectoryService;
    private final AdminUserRepository adminUserRepository;
    private final AdminMutationAuthorizer adminMutationAuthorizer;
    private final CommercialCampaignAudienceResolver audienceResolver;
    private final CommercialCatalogVersionService catalogVersionService;
    private final RegistryCatalogVersionService registryCatalogVersionService;
    private final CommercialPreviewTokenService previewTokenService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    protected FeatureDefinition featureDefinition() {
        return CampaignsFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list", description = "List and filter Campaign revisions")
    public Page<CommercialCampaignViews.Summary> list(
            String search, CommercialCampaignStatus status,
            CommercialCampaignAudienceMode audienceMode, CommercialCampaignSource source,
            boolean includeArchived, Pageable pageable) {
        Specification<CommercialCampaign> specification = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!includeArchived && status != CommercialCampaignStatus.ARCHIVED) {
                predicates.add(cb.notEqual(root.get("status"), CommercialCampaignStatus.ARCHIVED));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (audienceMode != null) predicates.add(cb.equal(root.get("audienceMode"), audienceMode));
            if (source != null) predicates.add(cb.equal(root.get("source"), source));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<CommercialCampaign> page = campaignRepository.findAll(specification, pageable);
        List<UUID> ids = page.getContent().stream().map(CommercialCampaign::getId).toList();
        Map<UUID, Integer> explicitCounts = ids.isEmpty() ? Map.of()
                : groupedInteger(campaignRepository.countExplicitAccountsByCampaignIds(ids));
        Map<UUID, Integer> frozenCounts = ids.isEmpty() ? Map.of()
                : groupedInteger(snapshotRepository.countFrozenAccounts(ids));
        Map<UUID, Integer> derivedCounts = ids.isEmpty() ? Map.of()
                : groupedInteger(campaignRepository.countDerivedBySourceCampaignIds(ids));
        Map<UUID, Integer> maximumRevisions = maximumRevisions(page.getContent());
        Map<UUID, Integer> liveByLineage = ids.isEmpty() ? Map.of()
                : groupedInteger(campaignRepository.countLiveByLineageIds(
                        page.getContent().stream().map(CommercialCampaign::getLineageId)
                                .collect(Collectors.toSet()), LIVE_STATUSES));
        Map<UUID, LatestSegmentActivation> latestSegmentActivations = latestSegmentActivations(
                page.getContent().stream()
                        .map(CommercialCampaign::getSegment)
                        .filter(Objects::nonNull)
                        .map(CommercialSegment::getId)
                        .collect(Collectors.toSet()));
        Instant stateAt = clock.instant();
        ActionPermissions permissions = actionPermissions();
        return page.map(campaign -> {
            Integer configured = campaign.getAudienceMode()
                    == CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS
                    ? explicitCounts.getOrDefault(campaign.getId(), 0) : null;
            int otherLive = liveByLineage.getOrDefault(campaign.getLineageId(), 0)
                    - (LIVE_STATUSES.contains(campaign.getStatus()) ? 1 : 0);
            List<CommercialCampaignBlocker> scheduleBlockers = scheduleStateBlockers(
                    campaign, configured, Math.max(0, otherLive), latestSegmentActivations, stateAt);
            return toSummary(campaign, configured, frozenCounts.get(campaign.getId()),
                    maximumRevisions.getOrDefault(campaign.getLineageId(), campaign.getRevisionNumber()),
                    derivedCounts.getOrDefault(campaign.getId(), 0) > 0,
                    scheduleBlockers, stateAt, permissions);
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "Read a Campaign definition")
    public CommercialCampaignViews.Detail get(UUID campaignId) {
        return toDetail(requireCampaign(campaignId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "create", description = "Create a draft Campaign")
    public CommercialCampaignViews.Detail create(CommercialCampaignRequests.Create request) {
        validateWindow(request.startsAt(), request.endsAt());
        CommercialCampaign campaign = CommercialCampaign.draft(nextCode(request.name()), request.name(),
                request.description(), request.startsAt(), request.endsAt(), request.source(),
                request.reason(), currentOwner());
        applyAudience(campaign, request.audience());
        campaignRepository.saveAndFlush(campaign);
        return toDetail(requireCampaign(campaign.getId()));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update", description = "Edit a draft Campaign")
    public CommercialCampaignViews.Detail update(UUID campaignId,
                                                   CommercialCampaignRequests.Update request) {
        validateWindow(request.startsAt(), request.endsAt());
        CommercialCampaign campaign = requireCampaignForUpdate(campaignId);
        requireVersion(campaign, request.version());
        translate(() -> campaign.editDraft(request.name(), request.description(), request.startsAt(),
                request.endsAt(), request.source(), request.reason()));
        applyAudience(campaign, request.audience());
        campaignRepository.saveAndFlush(campaign);
        return toDetail(requireCampaign(campaignId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "duplicate", description = "Duplicate a Campaign into a new lineage")
    public CommercialCampaignViews.Detail duplicate(UUID campaignId,
            CommercialCampaignRequests.Duplicate request) {
        CommercialCampaign source = requireCampaignForUpdate(campaignId);
        requireVersion(source, request.version());
        CommercialCampaign duplicate = translate(() -> source.duplicate(nextCode(request.name()),
                request.name(), currentOwner(), request.reason()));
        campaignRepository.saveAndFlush(duplicate);
        return toDetail(requireCampaign(duplicate.getId()));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "revise", description = "Create a successor draft Campaign revision")
    public CommercialCampaignViews.Detail revise(UUID campaignId,
            CommercialCampaignRequests.VersionReason request) {
        UUID lineageId = requireLineage(campaignId);
        List<CommercialCampaign> lineage = campaignRepository.findLineageForUpdate(lineageId);
        CommercialCampaign source = lineage.stream().filter(item -> item.getId().equals(campaignId))
                .findFirst().orElseThrow(() -> notFound(campaignId));
        requireVersion(source, request.version());
        int maximum = lineage.stream().mapToInt(CommercialCampaign::getRevisionNumber).max().orElse(0);
        if (lineage.stream().anyMatch(item -> item.getStatus() == CommercialCampaignStatus.DRAFT
                && !item.getId().equals(source.getId()))) {
            throw new DraftSuccessorExistsException(
                    "A draft successor already exists for this Campaign lineage.");
        }
        if (source.getRevisionNumber() != maximum) {
            throw new InvalidStateException("Only the latest Campaign revision can be revised.");
        }
        CommercialCampaign successor = translate(() -> source.revise(nextCode(source.getName()),
                currentOwner(), request.reason(), maximum + 1));
        try {
            campaignRepository.saveAndFlush(successor);
        } catch (DataIntegrityViolationException exception) {
            throw new DraftSuccessorExistsException(
                    "A concurrent request already created this Campaign revision.");
        }
        return toDetail(requireCampaign(successor.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "compare", description = "Compare two Campaign revisions")
    public CommercialCampaignViews.Comparison compare(UUID campaignId, UUID comparedCampaignId) {
        CommercialCampaign source = requireCampaign(campaignId);
        CommercialCampaign compared = requireCampaign(comparedCampaignId);
        boolean sameLineage = source.getLineageId().equals(compared.getLineageId());
        return new CommercialCampaignViews.Comparison(source.getId(), compared.getId(),
                sameLineage,
                sameLineage && compared.getSourceCampaign() != null
                        && compared.getSourceCampaign().getId().equals(source.getId()),
                changedFields(source, compared), toDetail(source), toDetail(compared));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "revisions", description = "Read Campaign revision lineage")
    public Page<CommercialCampaignViews.Revision> revisions(UUID campaignId, Pageable pageable) {
        UUID lineageId = requireLineage(campaignId);
        return campaignRepository.findAllByLineageId(lineageId,
                historyPage(pageable, "revisionNumber")).map(campaign ->
                new CommercialCampaignViews.Revision(campaign.getId(), campaign.getCode(),
                        campaign.getStatus(), campaign.getRevisionNumber(),
                        campaign.getSourceCampaign() == null ? null : campaign.getSourceCampaign().getId(),
                        campaign.getVersion(), campaign.getCreatedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "history", description = "Read Campaign mutation history")
    public Page<CommercialCampaignViews.History> history(UUID campaignId, Pageable pageable) {
        requireCampaignExists(campaignId);
        Page<AuditLog> logs = auditLogRepository.findAllByResourceTypeAndResourceId(
                AUDIT_RESOURCE_TYPE, campaignId.toString(), historyPage(pageable, "occurredAt"));
        Set<UUID> actorIds = logs.getContent().stream().map(AuditLog::getActorUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> emails = actorIds.isEmpty() ? Map.of()
                : adminUserRepository.findAllWithUserByUserIdIn(actorIds).stream()
                .collect(Collectors.toMap(item -> item.getUser().getId(),
                        item -> item.getUser().getEmail()));
        return logs.map(log -> new CommercialCampaignViews.History(log.getId(), log.getAction(),
                log.getOutcome(), log.getActorUserId(), emails.get(log.getActorUserId()),
                historyReason(log), log.getOccurredAt()));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PermissionNode(key = "preview_schedule", description = "Preview and sign a Campaign schedule")
    public CommercialCampaignViews.AudiencePreview previewSchedule(UUID campaignId) {
        String registryVersion = registryCatalogVersionService.currentVersion();
        CommercialCampaign campaign = requireCampaign(campaignId);
        return catalogVersionService.readConsistently(catalogRevision -> {
            Instant evaluatedAt = clock.instant();
            var evaluation = audienceResolver.evaluate(campaign, evaluatedAt);
            UUID actor = adminMutationAuthorizer.currentActorUserId();
            var evidence = previewTokenService.issue(CommercialPreviewKind.COMMERCIAL_CAMPAIGN_SCHEDULE,
                    campaign.getId(), campaign.getVersion(), actor, catalogRevision,
                    registryVersion, evaluation.fingerprint(), evaluatedAt);
            List<CommercialCampaignBlocker> blockers = scheduleBlockers(campaign, evaluation, evaluatedAt);
            return new CommercialCampaignViews.AudiencePreview(campaign.getId(), campaign.getVersion(),
                    campaign.getAudienceMode(), registryVersion,
                    evidence.evaluatedAt(), evidence.expiresAt(),
                    evidence.token(), evaluation.publicAudience() ? null : evaluation.total(),
                    evaluation.publicAudience(), blockers.isEmpty(), blockers,
                    evaluation.sampleIds().stream().map(CommercialCampaignViews.AudienceReference::new).toList(),
                    evaluation.segmentId(), evaluation.segmentActivationId(), evaluation.fingerprint());
        }, StaleSchedulePreviewException::new);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CommercialCatalogMutation
    @PermissionNode(key = "schedule", description = "Schedule a reviewed Campaign revision")
    public CommercialCampaignViews.Detail schedule(UUID campaignId,
            CommercialCampaignRequests.Schedule request) {
        if (!adminMutationAuthorizer.canManagePermission("platform.campaigns.preview_schedule")) {
            throw new ForbiddenException(
                    "Scheduling requires current permission to preview the Campaign audience.");
        }
        registryCatalogVersionService.lockForMutation();
        String registryVersion = registryCatalogVersionService.currentVersion();
        UUID lineageId = requireLineage(campaignId);
        campaignRepository.findLineageForUpdate(lineageId);
        CommercialCampaign campaign = requireCampaignForUpdate(campaignId);
        long catalogRevision = catalogVersionService.currentRevision();
        Instant evaluatedAt = clock.instant();
        var evaluation = audienceResolver.evaluate(campaign, evaluatedAt);
        UUID actor = adminMutationAuthorizer.currentActorUserId();
        var evidence = previewTokenService.requireValid(request.previewToken(),
                CommercialPreviewKind.COMMERCIAL_CAMPAIGN_SCHEDULE,
                campaign.getId(), campaign.getVersion(), actor, catalogRevision,
                registryVersion, evaluation.fingerprint(), StaleSchedulePreviewException::new);
        if (request.version() != campaign.getVersion()) throw new StaleSchedulePreviewException();
        List<CommercialCampaignBlocker> blockers = scheduleBlockers(campaign, evaluation, evaluatedAt);
        if (!blockers.isEmpty()) {
            throw new InvalidStateException("Campaign cannot be scheduled: " + blockers + ".");
        }
        translate(() -> campaign.schedule(evaluatedAt));
        campaignRepository.save(campaign);
        snapshotRepository.save(CommercialCampaignAudienceSnapshot.record(campaign, actor,
                evidence.evaluatedAt(), evidence.expiresAt(), request.version(), catalogRevision,
                registryVersion, evaluation.fingerprint(), evaluation.segmentId(),
                evaluation.segmentActivationId(),
                request.reason(), evaluation.accountIds()));
        campaignRepository.flush();
        snapshotRepository.flush();
        return toDetail(requireCampaign(campaignId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "pause", description = "Pause an active Campaign")
    public CommercialCampaignViews.Detail pause(UUID campaignId,
            CommercialCampaignRequests.VersionReason request) {
        CommercialCampaign campaign = requireCampaignForUpdate(campaignId);
        requireVersion(campaign, request.version());
        Instant now = clock.instant();
        if (!now.isBefore(campaign.getEndsAt())) {
            throw new InvalidStateException("An expired Campaign cannot be paused.");
        }
        translate(() -> campaign.pause(now));
        campaignRepository.saveAndFlush(campaign);
        return toDetail(requireCampaign(campaignId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "resume", description = "Resume a paused Campaign")
    public CommercialCampaignViews.Detail resume(UUID campaignId,
            CommercialCampaignRequests.VersionReason request) {
        CommercialCampaign campaign = requireCampaignForUpdate(campaignId);
        requireVersion(campaign, request.version());
        Instant now = clock.instant();
        if (!now.isBefore(campaign.getEndsAt())) {
            throw new InvalidStateException("An expired Campaign cannot be resumed.");
        }
        translate(() -> campaign.resume(now));
        campaignRepository.saveAndFlush(campaign);
        return toDetail(requireCampaign(campaignId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "end", description = "End a scheduled or running Campaign")
    public CommercialCampaignViews.Detail end(UUID campaignId,
            CommercialCampaignRequests.VersionReason request) {
        CommercialCampaign campaign = requireCampaignForUpdate(campaignId);
        requireVersion(campaign, request.version());
        translate(() -> campaign.end(clock.instant()));
        campaignRepository.saveAndFlush(campaign);
        return toDetail(requireCampaign(campaignId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "archive", description = "Archive an ended Campaign")
    public CommercialCampaignViews.Detail archive(UUID campaignId,
            CommercialCampaignRequests.VersionReason request) {
        CommercialCampaign campaign = requireCampaignForUpdate(campaignId);
        requireVersion(campaign, request.version());
        translate(() -> campaign.archive(clock.instant()));
        campaignRepository.saveAndFlush(campaign);
        return toDetail(requireCampaign(campaignId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "delete_draft", description = "Delete an unused Campaign draft")
    public void deleteDraft(UUID campaignId, CommercialCampaignRequests.VersionReason request) {
        CommercialCampaign campaign = requireCampaignForUpdate(campaignId);
        requireVersion(campaign, request.version());
        if (campaign.getStatus() != CommercialCampaignStatus.DRAFT) {
            throw new InvalidStateException("Only an unpublished Campaign draft can be deleted.");
        }
        if (snapshotRepository.existsByCampaign_Id(campaignId)) {
            throw new InvalidStateException("A Campaign with schedule history cannot be deleted.");
        }
        if (campaignRepository.existsBySourceCampaign_Id(campaignId)) {
            throw new InvalidStateException(
                    "A Campaign used as the source of another Campaign cannot be deleted.");
        }
        campaignRepository.delete(campaign);
        campaignRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "owner", description = "Read the separately authorized Campaign owner")
    public CommercialCampaignViews.Owner owner(UUID campaignId) {
        CommercialCampaignRepository.OwnerState state = campaignRepository.findOwnerStateById(campaignId)
                .orElseThrow(() -> notFound(campaignId));
        UUID ownerId = state.getOwnerAdminUserId();
        AdminUser owner = adminUserRepository.findWithUserById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "id", ownerId));
        var user = owner.getUser();
        return new CommercialCampaignViews.Owner(campaignId, owner.getId(), user.getId(),
                user.getEmail(), user.getUsername(), user.getFullName(), owner.isActive(),
                state.getStatus(), state.getVersion());
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "reassign_owner", description = "Reassign a draft Campaign owner")
    public CommercialCampaignViews.OwnerMutation reassignOwner(UUID campaignId,
            CommercialCampaignRequests.ReassignOwner request) {
        CommercialCampaign campaign = requireCampaignForUpdate(campaignId);
        requireVersion(campaign, request.version());
        AdminUser owner = adminUserRepository.findWithUserById(request.ownerAdminUserId())
                .filter(AdminUser::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active AdminUser", "id", request.ownerAdminUserId()));
        translate(() -> campaign.reassignDraftOwner(owner));
        campaignRepository.saveAndFlush(campaign);
        return new CommercialCampaignViews.OwnerMutation(
                campaign.getId(), campaign.getStatus(), campaign.getVersion());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "choose_owners", description = "Choose active Campaign owners")
    public Page<CommercialCampaignViews.OwnerChoice> chooseOwners(
            String query, Pageable pageable) {
        String normalized = query == null || query.isBlank() ? null : query.trim();
        return adminUserRepository.searchPageWithUser(normalized, true, boundedPage(pageable))
                .map(this::toOwnerChoice);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "resolve_owner_choices",
            description = "Resolve selected active Campaign owners")
    public List<CommercialCampaignViews.OwnerChoice> resolveOwnerChoices(Collection<UUID> ids) {
        validateIds(ids, "Owner");
        Map<UUID, AdminUser> byId = adminUserRepository.findAllWithUserByIdIn(ids).stream()
                .filter(AdminUser::isActive)
                .collect(Collectors.toMap(AdminUser::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull)
                .map(this::toOwnerChoice).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_audience", description = "Read opaque frozen Campaign audience references")
    public CommercialCampaignViews.FrozenAudience audience(UUID campaignId, Pageable pageable) {
        CommercialCampaignAudienceSnapshot snapshot = requireSnapshot(campaignId);
        Page<UUID> ids = snapshotRepository.findAccountIds(campaignId, boundedPage(pageable));
        Page<CommercialCampaignViews.AudienceReference> references = new PageImpl<>(
                ids.getContent().stream().map(CommercialCampaignViews.AudienceReference::new).toList(),
                ids.getPageable(), ids.getTotalElements());
        return new CommercialCampaignViews.FrozenAudience(campaignId, snapshot.getId(),
                snapshot.getAudienceMode(),
                snapshot.getAudienceMode() == CommercialCampaignAudienceMode.PUBLIC,
                snapshot.getAffectedAccountCount(), snapshot.getSegmentId(),
                snapshot.getSegmentActivationId(), snapshot.getActorUserId(),
                snapshot.getEvaluatedAt(), snapshot.getEvidenceExpiresAt(),
                snapshot.getCampaignVersion(), snapshot.getCatalogRevision(),
                snapshot.getRegistryVersion(), snapshot.getAudienceFingerprint(), snapshot.getReason(),
                snapshot.getStartsAt(), snapshot.getEndsAt(), PageResponse.from(references));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_audience_identities",
            description = "Read sensitive identities in a frozen Campaign audience")
    public CommercialCampaignViews.FrozenIdentityAudience audienceIdentities(
            UUID campaignId, Pageable pageable) {
        CommercialCampaignAudienceSnapshot snapshot = requireSnapshot(campaignId);
        Page<UUID> ids = snapshotRepository.findAccountIds(campaignId, boundedPage(pageable));
        Page<CommercialCampaignViews.AudienceIdentity> identities = new PageImpl<>(
                audienceResolver.identities(ids.getContent()), ids.getPageable(), ids.getTotalElements());
        return new CommercialCampaignViews.FrozenIdentityAudience(campaignId, snapshot.getId(),
                snapshot.getAffectedAccountCount(), PageResponse.from(identities));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "choose_accounts", description = "Choose safe explicit Campaign Accounts")
    public Page<AccountDirectoryEntryDto> chooseAccounts(String query, Boolean active, Pageable pageable) {
        return accountDirectoryService.search(query, active, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "resolve_account_choices", description = "Resolve selected Campaign Accounts")
    public List<AccountDirectoryEntryDto> resolveAccountChoices(Collection<UUID> ids) {
        validateIds(ids, "Account");
        return accountDirectoryService.resolve(ids);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "choose_segments", description = "Choose executable Segment activations")
    public Page<CommercialCampaignViews.SegmentChoice> chooseSegments(String search, Pageable pageable) {
        Page<CommercialSegment> segments = segmentRepository.findAll(
                executableSegmentSpecification(search, null), pageable);
        return new PageImpl<>(segmentChoices(segments.getContent()), segments.getPageable(),
                segments.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "resolve_segment_choices",
            description = "Resolve selected executable Segment activations")
    public CommercialCampaignViews.SegmentChoice resolveSegmentChoice(
            UUID segmentId, UUID activationId) {
        if (segmentId == null || activationId == null) {
            throw new InvalidRequestException(
                    "Exact Segment and activation ids are required for selected hydration.");
        }
        CommercialSegment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CommercialSegment", "id", segmentId));
        CommercialSegmentActivation activation = segmentActivationRepository
                .findByIdAndSegment_Id(activationId, segmentId).orElse(null);
        UUID latestId = segmentActivationRepository
                .findTopBySegment_IdOrderByActivationNumberDesc(segmentId)
                .map(CommercialSegmentActivation::getId).orElse(null);
        CommercialCampaignSegmentChoiceState state;
        if (segment.getStatus() != CommercialSegmentStatus.ACTIVE) {
            state = CommercialCampaignSegmentChoiceState.SEGMENT_INACTIVE;
        } else if (activation == null) {
            state = CommercialCampaignSegmentChoiceState.ACTIVATION_UNAVAILABLE;
        } else if (!activationId.equals(latestId)) {
            state = CommercialCampaignSegmentChoiceState.ACTIVATION_SUPERSEDED;
        } else {
            state = CommercialCampaignSegmentChoiceState.AVAILABLE;
        }
        return new CommercialCampaignViews.SegmentChoice(segment.getId(), segment.getCode(),
                segment.getName(), segment.getRevisionNumber(), segment.getKind(), activationId,
                activation == null ? null : activation.getActivationNumber(),
                activation == null ? null : activation.getAffectedAccountCount(), state);
    }

    private void applyAudience(CommercialCampaign campaign, CommercialCampaignRequests.Audience request) {
        if (request == null || request.mode() == null) {
            throw new InvalidRequestException("Campaign audience is required.");
        }
        switch (request.mode()) {
            case PUBLIC -> {
                requireAudienceShape(request, false, false);
                translate(campaign::configurePublicAudience);
            }
            case EXPLICIT_ACCOUNTS -> {
                if (request.explicitAccountIds().isEmpty()
                        || request.segmentId() != null || request.segmentActivationId() != null) {
                    throw new InvalidRequestException(
                            "EXPLICIT_ACCOUNTS requires only a non-empty Account set.");
                }
                List<Account> accounts = accountDirectoryService.requireManagedAccounts(
                        request.explicitAccountIds());
                translate(() -> campaign.configureExplicitAudience(accounts));
            }
            case SEGMENT -> {
                if (!request.explicitAccountIds().isEmpty()
                        || request.segmentId() == null || request.segmentActivationId() == null) {
                    throw new InvalidRequestException(
                            "SEGMENT requires only an exact Segment and activation id.");
                }
                CommercialSegment segment = segmentRepository.findDetailById(request.segmentId())
                        .filter(item -> item.getStatus() == CommercialSegmentStatus.ACTIVE)
                        .orElseThrow(() -> new InvalidRequestException(
                                "Campaign Segment must be an active exact revision."));
                CommercialSegmentActivation activation = segmentActivationRepository
                        .findWithAccountsById(request.segmentActivationId())
                        .filter(item -> item.getSegment().getId().equals(segment.getId()))
                        .orElseThrow(() -> new InvalidRequestException(
                                "Campaign Segment activation is unavailable."));
                UUID latest = segmentActivationRepository
                        .findTopBySegment_IdOrderByActivationNumberDesc(segment.getId())
                        .map(CommercialSegmentActivation::getId).orElse(null);
                if (!activation.getId().equals(latest)) {
                    throw new InvalidRequestException("Campaign must bind the latest Segment activation.");
                }
                translate(() -> campaign.configureSegmentAudience(segment, activation));
            }
        }
    }

    private void requireAudienceShape(CommercialCampaignRequests.Audience request,
                                      boolean accounts, boolean segment) {
        if ((!accounts && !request.explicitAccountIds().isEmpty())
                || (!segment && (request.segmentId() != null || request.segmentActivationId() != null))) {
            throw new InvalidRequestException("Campaign audience fields do not match its mode.");
        }
    }

    private List<CommercialCampaignBlocker> scheduleBlockers(
            CommercialCampaign campaign,
            CommercialCampaignAudienceResolver.Evaluation evaluation,
            Instant evaluatedAt) {
        List<CommercialCampaignBlocker> blockers = new ArrayList<>(evaluation.blockers());
        if (campaign.getStatus() != CommercialCampaignStatus.DRAFT) {
            blockers.add(CommercialCampaignBlocker.NOT_DRAFT);
        }
        if (!campaign.getEndsAt().isAfter(campaign.getStartsAt())) {
            blockers.add(CommercialCampaignBlocker.INVALID_WINDOW);
        }
        if (!campaign.getEndsAt().isAfter(evaluatedAt)) {
            blockers.add(CommercialCampaignBlocker.WINDOW_ENDED);
        }
        if (campaignRepository.countOtherLiveRevisions(
                campaign.getLineageId(), campaign.getId(), LIVE_STATUSES) > 0) {
            blockers.add(CommercialCampaignBlocker.LIVE_LINEAGE_REVISION_EXISTS);
        }
        return List.copyOf(new LinkedHashSet<>(blockers));
    }

    /**
     * Cheap, bounded schedule blockers used by list/detail action contracts. The signed preview
     * remains authoritative, but the UI must not advertise an apply action that current persisted
     * state already proves cannot succeed.
     */
    private List<CommercialCampaignBlocker> scheduleStateBlockers(
            CommercialCampaign campaign,
            Integer explicitAccountCount,
            int otherLiveRevisions,
            Map<UUID, LatestSegmentActivation> latestSegmentActivations,
            Instant evaluatedAt) {
        if (campaign.getStatus() != CommercialCampaignStatus.DRAFT) return List.of();
        List<CommercialCampaignBlocker> blockers = new ArrayList<>();
        if (!campaign.getEndsAt().isAfter(campaign.getStartsAt())) {
            blockers.add(CommercialCampaignBlocker.INVALID_WINDOW);
        }
        if (!campaign.getEndsAt().isAfter(evaluatedAt)) {
            blockers.add(CommercialCampaignBlocker.WINDOW_ENDED);
        }
        if (otherLiveRevisions > 0) {
            blockers.add(CommercialCampaignBlocker.LIVE_LINEAGE_REVISION_EXISTS);
        }
        switch (campaign.getAudienceMode()) {
            case PUBLIC -> { }
            case EXPLICIT_ACCOUNTS -> addAudienceSizeBlockers(
                    explicitAccountCount == null ? 0 : explicitAccountCount, blockers);
            case SEGMENT -> {
                CommercialSegment segment = campaign.getSegment();
                CommercialSegmentActivation activation = campaign.getSegmentActivation();
                if (segment == null || segment.getStatus() != CommercialSegmentStatus.ACTIVE) {
                    blockers.add(CommercialCampaignBlocker.SEGMENT_NOT_ACTIVE);
                }
                LatestSegmentActivation latest = segment == null
                        ? null : latestSegmentActivations.get(segment.getId());
                if (activation == null || latest == null || !activation.getId().equals(latest.id())) {
                    blockers.add(CommercialCampaignBlocker.SEGMENT_ACTIVATION_STALE);
                }
                addAudienceSizeBlockers(
                        activation == null ? 0 : activation.getAffectedAccountCount(), blockers);
            }
        }
        return List.copyOf(new LinkedHashSet<>(blockers));
    }

    private void addAudienceSizeBlockers(
            int accountCount, List<CommercialCampaignBlocker> blockers) {
        if (accountCount == 0) blockers.add(CommercialCampaignBlocker.EMPTY_AUDIENCE);
        if (accountCount > CommercialCampaignAudienceResolver.SNAPSHOT_ACCOUNT_LIMIT) {
            blockers.add(CommercialCampaignBlocker.AUDIENCE_TOO_LARGE);
        }
    }

    private CommercialCampaignViews.Detail toDetail(CommercialCampaign campaign) {
        initializeAudience(campaign);
        Integer configured = campaign.getAudienceMode() == CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS
                ? campaign.getExplicitAccounts().size() : null;
        Integer frozen = snapshotRepository.findByCampaign_Id(campaign.getId())
                .map(CommercialCampaignAudienceSnapshot::getAffectedAccountCount).orElse(null);
        boolean hasDerivedCampaigns = campaignRepository.existsBySourceCampaign_Id(campaign.getId());
        int maximum = campaignRepository.findMaximumRevisionNumber(campaign.getLineageId());
        int otherLive = Math.toIntExact(campaignRepository.countOtherLiveRevisions(
                campaign.getLineageId(), campaign.getId(), LIVE_STATUSES));
        Map<UUID, LatestSegmentActivation> latestSegmentActivations = latestSegmentActivations(
                campaign.getSegment() == null ? Set.of() : Set.of(campaign.getSegment().getId()));
        Instant stateAt = clock.instant();
        List<CommercialCampaignBlocker> scheduleBlockers = scheduleStateBlockers(
                campaign, configured, otherLive, latestSegmentActivations, stateAt);
        Set<UUID> explicitIds = campaign.getAudienceMode() == CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS
                ? campaign.getExplicitAccounts().stream().map(Account::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new)) : Set.of();
        return new CommercialCampaignViews.Detail(
                toSummary(campaign, configured, frozen, maximum, hasDerivedCampaigns,
                        scheduleBlockers, stateAt, actionPermissions()),
                campaign.getDescription(), campaign.getReason(),
                new CommercialCampaignViews.Audience(campaign.getAudienceMode(), explicitIds,
                        campaign.getSegment() == null ? null : campaign.getSegment().getId(),
                        campaign.getSegmentActivation() == null ? null
                                : campaign.getSegmentActivation().getId()),
                campaign.getSourceCampaign() == null ? null : campaign.getSourceCampaign().getId(),
                campaign.getScheduledAt(), campaign.getActivatedAt(), campaign.getPausedAt(),
                campaign.getResumedAt(),
                campaign.getEndedAt(), campaign.getArchivedAt());
    }

    private CommercialCampaignViews.OwnerChoice toOwnerChoice(AdminUser adminUser) {
        var user = adminUser.getUser();
        return new CommercialCampaignViews.OwnerChoice(adminUser.getId(), user.getEmail(),
                user.getUsername(), user.getFullName());
    }

    private CommercialCampaignViews.Summary toSummary(
            CommercialCampaign campaign, Integer configured, Integer frozen,
            int maximumRevision, boolean hasDerivedCampaigns,
            List<CommercialCampaignBlocker> scheduleBlockers,
            Instant stateAt, ActionPermissions permissions) {
        return new CommercialCampaignViews.Summary(campaign.getId(), campaign.getCode(),
                campaign.getName(), campaign.getStatus(), campaign.getAudienceMode(),
                campaign.getSource(), campaign.getStartsAt(), campaign.getEndsAt(), configured, frozen,
                campaign.getLineageId(), campaign.getRevisionNumber(), campaign.getCreationReason(),
                campaign.getVersion(), campaign.getCreatedAt(), campaign.getUpdatedAt(),
                availableActions(campaign, maximumRevision, frozen != null,
                        hasDerivedCampaigns, scheduleBlockers, stateAt, permissions),
                blockedActions(campaign, maximumRevision, frozen != null,
                        hasDerivedCampaigns, scheduleBlockers, stateAt, permissions), true, true);
    }

    private List<CommercialCampaignAction> availableActions(
            CommercialCampaign campaign, int maximumRevision, boolean hasSnapshot,
            boolean hasDerivedCampaigns, List<CommercialCampaignBlocker> scheduleBlockers,
            Instant stateAt, ActionPermissions permissions) {
        EnumSet<CommercialCampaignAction> actions = EnumSet.of(
                CommercialCampaignAction.DUPLICATE, CommercialCampaignAction.COMPARE,
                CommercialCampaignAction.HISTORY, CommercialCampaignAction.REVISIONS,
                CommercialCampaignAction.OWNER);
        if (hasSnapshot) {
            actions.add(CommercialCampaignAction.READ_AUDIENCE);
            actions.add(CommercialCampaignAction.READ_AUDIENCE_IDENTITIES);
        }
        switch (campaign.getStatus()) {
            case DRAFT -> {
                actions.addAll(Set.of(CommercialCampaignAction.UPDATE,
                        CommercialCampaignAction.PREVIEW_SCHEDULE,
                        CommercialCampaignAction.DELETE_DRAFT,
                        CommercialCampaignAction.REASSIGN_OWNER));
                if (scheduleBlockers.isEmpty()) actions.add(CommercialCampaignAction.SCHEDULE);
            }
            case SCHEDULED -> actions.add(CommercialCampaignAction.END);
            case ACTIVE -> {
                actions.add(CommercialCampaignAction.END);
                if (stateAt.isBefore(campaign.getEndsAt())) actions.add(CommercialCampaignAction.PAUSE);
            }
            case PAUSED -> {
                actions.add(CommercialCampaignAction.END);
                if (stateAt.isBefore(campaign.getEndsAt())) actions.add(CommercialCampaignAction.RESUME);
            }
            case ENDED -> actions.add(CommercialCampaignAction.ARCHIVE);
            case ARCHIVED -> { }
        }
        if (campaign.getStatus() != CommercialCampaignStatus.DRAFT
                && campaign.getStatus() != CommercialCampaignStatus.ARCHIVED
                && campaign.getRevisionNumber() == maximumRevision) {
            actions.add(CommercialCampaignAction.REVISE);
        }
        if (hasDerivedCampaigns) actions.remove(CommercialCampaignAction.DELETE_DRAFT);
        actions.removeIf(action -> !permissions.allows(action));
        return List.copyOf(actions);
    }

    private Map<CommercialCampaignAction, List<CommercialCampaignBlocker>> blockedActions(
            CommercialCampaign campaign, int maximumRevision, boolean hasSnapshot,
            boolean hasDerivedCampaigns, List<CommercialCampaignBlocker> scheduleBlockers,
            Instant stateAt, ActionPermissions permissions) {
        Map<CommercialCampaignAction, List<CommercialCampaignBlocker>> blocked =
                new EnumMap<>(CommercialCampaignAction.class);
        if (campaign.getStatus() != CommercialCampaignStatus.DRAFT) {
            for (CommercialCampaignAction action : List.of(CommercialCampaignAction.UPDATE,
                    CommercialCampaignAction.SCHEDULE, CommercialCampaignAction.DELETE_DRAFT,
                    CommercialCampaignAction.REASSIGN_OWNER)) {
                block(blocked, action, CommercialCampaignBlocker.NOT_DRAFT);
            }
        }
        scheduleBlockers.forEach(blocker -> block(
                blocked, CommercialCampaignAction.SCHEDULE, blocker));
        if (campaign.getStatus() != CommercialCampaignStatus.ACTIVE) {
            block(blocked, CommercialCampaignAction.PAUSE, CommercialCampaignBlocker.NOT_ACTIVE);
        } else if (!stateAt.isBefore(campaign.getEndsAt())) {
            block(blocked, CommercialCampaignAction.PAUSE, CommercialCampaignBlocker.WINDOW_ENDED);
        }
        if (campaign.getStatus() != CommercialCampaignStatus.PAUSED) {
            block(blocked, CommercialCampaignAction.RESUME, CommercialCampaignBlocker.NOT_PAUSED);
        } else if (!stateAt.isBefore(campaign.getEndsAt())) {
            block(blocked, CommercialCampaignAction.RESUME, CommercialCampaignBlocker.WINDOW_ENDED);
        }
        if (campaign.getStatus() != CommercialCampaignStatus.ENDED) {
            block(blocked, CommercialCampaignAction.ARCHIVE, CommercialCampaignBlocker.NOT_ENDED);
        }
        if (campaign.getStatus() == CommercialCampaignStatus.ARCHIVED) {
            block(blocked, CommercialCampaignAction.ARCHIVE, CommercialCampaignBlocker.ALREADY_ARCHIVED);
        }
        if (campaign.getRevisionNumber() < maximumRevision) {
            block(blocked, CommercialCampaignAction.REVISE, CommercialCampaignBlocker.NOT_LATEST_REVISION);
        }
        if (hasSnapshot) {
            block(blocked, CommercialCampaignAction.DELETE_DRAFT,
                    CommercialCampaignBlocker.HAS_SCHEDULE_HISTORY);
        }
        if (hasDerivedCampaigns) {
            block(blocked, CommercialCampaignAction.DELETE_DRAFT,
                    CommercialCampaignBlocker.HAS_DERIVED_CAMPAIGNS);
        }
        blocked.entrySet().removeIf(entry -> !permissions.allows(entry.getKey()));
        return Map.copyOf(blocked);
    }

    private static String permissionFor(CommercialCampaignAction action) {
        return switch (action) {
            case UPDATE -> "platform.campaigns.update";
            case DUPLICATE -> "platform.campaigns.duplicate";
            case REVISE -> "platform.campaigns.revise";
            case COMPARE -> "platform.campaigns.compare";
            case HISTORY -> "platform.campaigns.history";
            case PREVIEW_SCHEDULE -> "platform.campaigns.preview_schedule";
            case SCHEDULE -> "platform.campaigns.schedule";
            case PAUSE -> "platform.campaigns.pause";
            case RESUME -> "platform.campaigns.resume";
            case END -> "platform.campaigns.end";
            case ARCHIVE -> "platform.campaigns.archive";
            case DELETE_DRAFT -> "platform.campaigns.delete_draft";
            case OWNER -> "platform.campaigns.owner";
            case REASSIGN_OWNER -> "platform.campaigns.reassign_owner";
            case REVISIONS -> "platform.campaigns.revisions";
            case READ_AUDIENCE -> "platform.campaigns.read_audience";
            case READ_AUDIENCE_IDENTITIES -> "platform.campaigns.read_audience_identities";
        };
    }

    private Specification<CommercialSegment> executableSegmentSpecification(
            String search, Set<UUID> ids) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), CommercialSegmentStatus.ACTIVE));
            var activation = query.subquery(Long.class);
            var activationRoot = activation.from(CommercialSegmentActivation.class);
            activation.select(cb.literal(1L)).where(
                    cb.equal(activationRoot.get("segment").get("id"), root.get("id")));
            predicates.add(cb.exists(activation));
            if (ids != null) predicates.add(root.get("id").in(ids));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private List<CommercialCampaignViews.SegmentChoice> segmentChoices(List<CommercialSegment> segments) {
        if (segments.isEmpty()) return List.of();
        Map<UUID, Object[]> latest = segmentActivationRepository.findLatestMetadata(segments.stream()
                        .map(CommercialSegment::getId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], Function.identity()));
        return segments.stream().map(segment -> {
            Object[] activation = latest.get(segment.getId());
            return activation == null ? null : new CommercialCampaignViews.SegmentChoice(
                    segment.getId(), segment.getCode(), segment.getName(), segment.getRevisionNumber(),
                    segment.getKind(), (UUID) activation[1], ((Number) activation[2]).intValue(),
                    ((Number) activation[3]).intValue(),
                    CommercialCampaignSegmentChoiceState.AVAILABLE);
        }).filter(Objects::nonNull).toList();
    }

    private Set<String> changedFields(CommercialCampaign left, CommercialCampaign right) {
        Set<String> fields = new LinkedHashSet<>();
        changed(fields, "NAME", left.getName(), right.getName());
        changed(fields, "DESCRIPTION", left.getDescription(), right.getDescription());
        changed(fields, "WINDOW", List.of(left.getStartsAt(), left.getEndsAt()),
                List.of(right.getStartsAt(), right.getEndsAt()));
        changed(fields, "SOURCE", left.getSource(), right.getSource());
        changed(fields, "REASON", left.getReason(), right.getReason());
        changed(fields, "AUDIENCE", audienceSignature(left), audienceSignature(right));
        changed(fields, "OWNER", left.getOwner().getId(), right.getOwner().getId());
        return Set.copyOf(fields);
    }

    private String audienceSignature(CommercialCampaign campaign) {
        initializeAudience(campaign);
        return String.join("|", campaign.getAudienceMode().name(),
                campaign.getExplicitAccounts().stream().map(Account::getId).sorted()
                        .map(UUID::toString).collect(Collectors.joining(",")),
                campaign.getSegment() == null ? "" : campaign.getSegment().getId().toString(),
                campaign.getSegmentActivation() == null ? ""
                        : campaign.getSegmentActivation().getId().toString());
    }

    private void validateWindow(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new InvalidRequestException("Campaign end must be after its start.");
        }
    }

    private void validateIds(Collection<UUID> ids, String label) {
        if (ids == null || ids.isEmpty() || ids.size() > 100
                || ids.stream().anyMatch(Objects::isNull)
                || new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new InvalidRequestException(label
                    + " choice resolution requires 1 to 100 unique non-null ids.");
        }
    }

    private CommercialCampaign requireCampaign(UUID id) {
        CommercialCampaign campaign = campaignRepository.findDetailById(id)
                .orElseThrow(() -> notFound(id));
        initializeAudience(campaign);
        return campaign;
    }

    private void initializeAudience(CommercialCampaign campaign) {
        campaign.getExplicitAccounts().size();
    }

    private CommercialCampaign requireCampaignForUpdate(UUID id) {
        campaignRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        return requireCampaign(id);
    }

    private CommercialCampaignAudienceSnapshot requireSnapshot(UUID campaignId) {
        requireCampaignExists(campaignId);
        return snapshotRepository.findByCampaign_Id(campaignId)
                .orElseThrow(() -> new InvalidStateException(
                        "Campaign has not accepted a schedule audience yet."));
    }

    private UUID requireLineage(UUID campaignId) {
        return campaignRepository.findLineageIdById(campaignId)
                .orElseThrow(() -> notFound(campaignId));
    }

    private void requireCampaignExists(UUID id) {
        if (!campaignRepository.existsById(id)) throw notFound(id);
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("CommercialCampaign", "id", id);
    }

    private AdminUser currentOwner() {
        UUID id = adminMutationAuthorizer.currentActorAdminUserId();
        return adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "id", id));
    }

    private String nextCode(String name) {
        return CommercialCodeGenerator.generate(name, "CAMPAIGN", campaignRepository::existsByCode);
    }

    private void requireVersion(CommercialCampaign campaign, long expected) {
        if (campaign.getVersion() != expected) {
            throw new StaleResourceVersionException(
                    "Campaign changed since it was read. Reload it and retry.");
        }
    }

    private Pageable boundedPage(Pageable pageable) {
        if (pageable == null || pageable.getPageNumber() < 0
                || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
    }

    private Pageable historyPage(Pageable pageable, String property) {
        Pageable bounded = boundedPage(pageable);
        return PageRequest.of(bounded.getPageNumber(), bounded.getPageSize(),
                Sort.by(Sort.Direction.DESC, property).and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private String historyReason(AuditLog log) {
        if (log.getRequestData() == null) return null;
        try {
            var request = objectMapper.readTree(log.getRequestData());
            var reason = request.get("reason");
            for (String wrapper : List.of("request", "before")) {
                if ((reason == null || !reason.isTextual()) && request.path(wrapper).isObject()) {
                    reason = request.path(wrapper).get("reason");
                }
            }
            return reason != null && reason.isTextual() && !reason.textValue().isBlank()
                    ? reason.textValue().trim() : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return null;
        }
    }

    private Map<UUID, Integer> maximumRevisions(List<CommercialCampaign> campaigns) {
        if (campaigns.isEmpty()) return Map.of();
        return campaignRepository.findMaximumRevisionNumbers(campaigns.stream()
                        .map(CommercialCampaign::getLineageId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0],
                        row -> ((Number) row[1]).intValue()));
    }

    private Map<UUID, Integer> groupedInteger(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0],
                row -> ((Number) row[1]).intValue()));
    }

    private Map<UUID, LatestSegmentActivation> latestSegmentActivations(Set<UUID> segmentIds) {
        if (segmentIds.isEmpty()) return Map.of();
        return segmentActivationRepository.findLatestMetadata(segmentIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row ->
                        new LatestSegmentActivation((UUID) row[1],
                                ((Number) row[2]).intValue(), ((Number) row[3]).intValue())));
    }

    private void block(Map<CommercialCampaignAction, List<CommercialCampaignBlocker>> blocked,
                       CommercialCampaignAction action, CommercialCampaignBlocker blocker) {
        blocked.computeIfAbsent(action, ignored -> new ArrayList<>()).add(blocker);
    }

    private void changed(Set<String> fields, String field, Object left, Object right) {
        if (!Objects.equals(left, right)) fields.add(field);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void translate(Runnable operation) {
        try { operation.run(); }
        catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InvalidStateException(exception.getMessage());
        }
    }

    private <T> T translate(java.util.function.Supplier<T> operation) {
        try { return operation.get(); }
        catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InvalidStateException(exception.getMessage());
        }
    }

    private ActionPermissions actionPermissions() {
        return new ActionPermissions(adminMutationAuthorizer.currentActorGrantCeiling());
    }

    private static final class ActionPermissions {
        private final AdminMutationAuthorizer.GrantCeiling ceiling;
        private final Map<String, Boolean> decisions = new HashMap<>();
        private ActionPermissions(AdminMutationAuthorizer.GrantCeiling ceiling) {
            this.ceiling = ceiling;
        }
        private boolean allows(CommercialCampaignAction action) {
            if (action == CommercialCampaignAction.SCHEDULE) {
                return allowsPermission("platform.campaigns.preview_schedule")
                        && allowsPermission("platform.campaigns.schedule");
            }
            return allowsPermission(permissionFor(action));
        }
        private boolean allowsPermission(String permission) {
            return decisions.computeIfAbsent(permission, ceiling::allows);
        }
    }

    private record LatestSegmentActivation(UUID id, int activationNumber, int accountCount) {}
}
