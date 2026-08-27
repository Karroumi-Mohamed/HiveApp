package com.hiveapp.platform.client.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegment;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegmentActivation;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegmentProductSelection;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentActivationRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentRequests;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentViews;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialCodeGenerator;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.service.CommercialSegmentAdminService;
import com.hiveapp.platform.client.plan.service.CommercialSegmentAudienceResolver;
import com.hiveapp.platform.registry.definition.AccountSegmentsFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.exception.DraftSuccessorExistsException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleActivationPreviewException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = AccountSegmentsFeature.KEY,
        description = "Safe Account Segment management", guard = PermissionNode.Guard.ON)
public class CommercialSegmentAdminServiceImpl extends PlatformControlFeatureService
        implements CommercialSegmentAdminService {

    private static final String AUDIT_RESOURCE_TYPE = "COMMERCIAL_SEGMENT_ADMIN";
    private static final String EVIDENCE_REGISTRY_VERSION = "SEGMENT_CRITERIA_V1";
    private static final Instant EARLIEST_ACCOUNT_DATE = Instant.parse("2000-01-01T00:00:00Z");
    private static final Duration MAX_ACCOUNT_DATE_RANGE = Duration.ofDays(366L * 20L);
    private static final Set<SubscriptionStatus> CURRENT_STATUSES = Set.of(
            SubscriptionStatus.TRIALING,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.SUSPENDED);

    private final CommercialSegmentRepository segmentRepository;
    private final CommercialSegmentActivationRepository activationRepository;
    private final CommercialPolicyRepository policyRepository;
    private final AccountRepository accountRepository;
    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminMutationAuthorizer adminMutationAuthorizer;
    private final CommercialSegmentAudienceResolver audienceResolver;
    private final CommercialCatalogVersionService catalogVersionService;
    private final CommercialPreviewTokenService previewTokenService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    protected FeatureDefinition featureDefinition() {
        return AccountSegmentsFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list", description = "List and filter Account Segment revisions")
    public Page<CommercialSegmentViews.Summary> list(
            String search,
            CommercialSegmentStatus status,
            CommercialSegmentKind kind,
            CommercialSegmentSource source,
            boolean includeArchived,
            Pageable pageable
    ) {
        Specification<CommercialSegment> specification = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!includeArchived && status != CommercialSegmentStatus.ARCHIVED) {
                predicates.add(cb.notEqual(root.get("status"), CommercialSegmentStatus.ARCHIVED));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (kind != null) predicates.add(cb.equal(root.get("kind"), kind));
            if (source != null) predicates.add(cb.equal(root.get("source"), source));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<CommercialSegment> page = segmentRepository.findAll(specification, pageable);
        List<UUID> ids = page.getContent().stream().map(CommercialSegment::getId).toList();
        Map<UUID, Integer> explicitCounts = groupedInteger(
                segmentRepository.countExplicitAccountsBySegmentIds(ids));
        Map<UUID, Integer> activationCounts = groupedInteger(
                activationRepository.countLatestSnapshotAccounts(ids));
        Map<UUID, Integer> maximumRevisions = maximumRevisions(page.getContent());
        Map<String, Integer> referenceCounts = policyReferenceCounts(page.getContent());
        ActionPermissions permissions = actionPermissions();
        return page.map(segment -> toSummary(segment,
                explicitCounts.getOrDefault(segment.getId(), 0),
                activationCounts.get(segment.getId()),
                maximumRevisions.getOrDefault(segment.getLineageId(), segment.getRevisionNumber()),
                referenceCounts.getOrDefault(segment.getCode(), 0), permissions));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_detail", description = "Read an Account Segment definition")
    public CommercialSegmentViews.Detail get(UUID segmentId) {
        return toDetail(requireSegment(segmentId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "create", description = "Create a draft Account Segment")
    public CommercialSegmentViews.Detail create(CommercialSegmentRequests.Create request) {
        validateRequest(request.kind(), request.definition());
        CommercialSegment segment = CommercialSegment.draft(
                nextCode(request.name()), request.name(), request.description(), request.kind(),
                request.source(), request.reason(), currentOwner());
        applyDefinition(segment, request.kind(), request.definition());
        segmentRepository.saveAndFlush(segment);
        return toDetail(requireSegment(segment.getId()));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_draft", description = "Edit a draft Account Segment")
    public CommercialSegmentViews.Detail update(
            UUID segmentId,
            CommercialSegmentRequests.Update request
    ) {
        validateRequest(request.kind(), request.definition());
        CommercialSegment segment = requireSegmentForUpdate(segmentId);
        requireVersion(segment, request.version());
        translateState(() -> segment.editDraft(request.name(), request.description(), request.kind(),
                request.source(), request.reason(), segment.getOwner()));
        applyDefinition(segment, request.kind(), request.definition());
        segmentRepository.saveAndFlush(segment);
        return toDetail(requireSegment(segmentId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "duplicate", description = "Duplicate a Segment into a new lineage")
    public CommercialSegmentViews.Detail duplicate(
            UUID segmentId,
            CommercialSegmentRequests.Duplicate request
    ) {
        CommercialSegment source = requireSegmentForUpdate(segmentId);
        requireVersion(source, request.version());
        CommercialSegment duplicate = translateState(() -> source.duplicate(
                nextCode(request.name()), request.name(), currentOwner(), request.reason()));
        segmentRepository.saveAndFlush(duplicate);
        return toDetail(requireSegment(duplicate.getId()));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "revise", description = "Create a successor draft Segment revision")
    public CommercialSegmentViews.Detail revise(
            UUID segmentId,
            CommercialSegmentRequests.VersionReason request
    ) {
        UUID lineageId = segmentRepository.findLineageIdById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialSegment", "id", segmentId));
        List<CommercialSegment> lineage = segmentRepository.findLineageForUpdate(lineageId);
        CommercialSegment source = lineage.stream().filter(item -> item.getId().equals(segmentId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException(
                        "CommercialSegment", "id", segmentId));
        requireVersion(source, request.version());
        int maximum = lineage.stream().mapToInt(CommercialSegment::getRevisionNumber).max().orElse(0);
        int maximumMaterial = segmentRepository.findMaximumMaterialRevisionNumber(
                lineageId, CommercialSegmentStatus.ARCHIVED);
        if (source.getRevisionNumber() != maximumMaterial) {
            throw new InvalidStateException("Only the latest Segment revision can be revised.");
        }
        if (lineage.stream().anyMatch(item -> item.getStatus() == CommercialSegmentStatus.DRAFT
                && !item.getId().equals(source.getId()))) {
            throw new DraftSuccessorExistsException(
                    "A draft successor already exists for this Segment lineage.");
        }
        CommercialSegment successor = translateState(() -> source.revise(
                nextCode(source.getName()), currentOwner(), request.reason(), maximum + 1));
        segmentRepository.saveAndFlush(successor);
        return toDetail(requireSegment(successor.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "compare", description = "Compare two Account Segment revisions")
    public CommercialSegmentViews.Comparison compare(UUID segmentId, UUID comparedSegmentId) {
        CommercialSegment source = requireSegment(segmentId);
        CommercialSegment compared = requireSegment(comparedSegmentId);
        boolean sameLineage = source.getLineageId().equals(compared.getLineageId());
        boolean direct = compared.getSourceSegment() != null
                && compared.getSourceSegment().getId().equals(source.getId());
        return new CommercialSegmentViews.Comparison(source.getId(), compared.getId(),
                sameLineage, direct, changedFields(source, compared),
                toDetail(source), toDetail(compared));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_revisions", description = "Read Segment revision lineage")
    public Page<CommercialSegmentViews.Revision> revisions(UUID segmentId, Pageable pageable) {
        UUID lineageId = segmentRepository.findLineageIdById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialSegment", "id", segmentId));
        Pageable bounded = historyPage(pageable, "revisionNumber");
        return segmentRepository.findAllByLineageId(lineageId, bounded).map(item ->
                new CommercialSegmentViews.Revision(item.getId(), item.getCode(), item.getStatus(),
                        item.getRevisionNumber(), item.getSourceSegment() == null ? null
                                : item.getSourceSegment().getId(), item.getVersion(), item.getCreatedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_history", description = "Read Account Segment mutation history")
    public Page<CommercialSegmentViews.History> history(UUID segmentId, Pageable pageable) {
        requireSegmentExists(segmentId);
        Page<AuditLog> logs = auditLogRepository.findAllByResourceTypeAndResourceId(
                AUDIT_RESOURCE_TYPE, segmentId.toString(), historyPage(pageable, "occurredAt"));
        Set<UUID> actorIds = logs.getContent().stream().map(AuditLog::getActorUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> actorEmails = actorIds.isEmpty() ? Map.of()
                : adminUserRepository.findAllWithUserByUserIdIn(actorIds).stream()
                        .collect(Collectors.toMap(item -> item.getUser().getId(),
                                item -> item.getUser().getEmail()));
        return logs.map(log -> new CommercialSegmentViews.History(
                log.getId(), log.getAction(), log.getOutcome(), log.getActorUserId(),
                actorEmails.get(log.getActorUserId()), historyReason(log), log.getOccurredAt()));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "count", description = "Count a Segment audience without identities or evidence")
    public CommercialSegmentViews.Count count(UUID segmentId) {
        CommercialSegment segment = requireSegment(segmentId);
        Instant evaluatedAt = clock.instant();
        long total = audienceResolver.count(segment);
        return new CommercialSegmentViews.Count(segment.getId(), segment.getVersion(), evaluatedAt,
                total, CommercialSegmentAudienceResolver.ACTIVATION_ACCOUNT_LIMIT,
                total <= CommercialSegmentAudienceResolver.ACTIVATION_ACCOUNT_LIMIT);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PermissionNode(key = "preview", description = "Preview and sign a bounded Segment audience")
    public CommercialSegmentViews.Preview preview(UUID segmentId) {
        CommercialSegment segment = requireSegment(segmentId);
        return catalogVersionService.readConsistently(catalogRevision -> {
            Instant evaluatedAt = clock.instant();
            var evaluation = audienceResolver.evaluate(segment, evaluatedAt);
            UUID actor = adminMutationAuthorizer.currentActorUserId();
            var evidence = previewTokenService.issue(
                    CommercialPreviewKind.COMMERCIAL_SEGMENT_ACTIVATION,
                    segment.getId(), segment.getVersion(), actor, catalogRevision,
                    EVIDENCE_REGISTRY_VERSION, evaluation.fingerprint(), evaluatedAt);
            List<CommercialSegmentBlocker> blockers = previewBlockers(segment, evaluation);
            return new CommercialSegmentViews.Preview(
                    segment.getId(), segment.getVersion(), evidence.evaluatedAt(), evidence.expiresAt(),
                    evidence.token(), evaluation.total(),
                    CommercialSegmentAudienceResolver.ACTIVATION_ACCOUNT_LIMIT,
                    blockers.isEmpty(), blockers,
                    evaluation.sampleIds().stream()
                            .map(CommercialSegmentViews.AudienceReference::new).toList());
        }, StaleActivationPreviewException::new);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PermissionNode(key = "read_sample_identities",
            description = "Read sensitive Account identities in a Segment preview")
    public CommercialSegmentViews.IdentitySample previewIdentities(UUID segmentId) {
        CommercialSegment segment = requireSegment(segmentId);
        Instant evaluatedAt = clock.instant();
        var evaluation = audienceResolver.evaluate(segment, evaluatedAt);
        return new CommercialSegmentViews.IdentitySample(segment.getId(), segment.getVersion(),
                evaluatedAt, evaluation.total(), audienceResolver.identities(evaluation.sampleIds()));
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CommercialCatalogMutation
    @PermissionNode(key = "activate", description = "Activate a reviewed Segment definition")
    public CommercialSegmentViews.Detail activate(
            UUID segmentId,
            CommercialSegmentRequests.Activation request
    ) {
        UUID lineageId = segmentRepository.findLineageIdById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialSegment", "id", segmentId));
        segmentRepository.findLineageForUpdate(lineageId);
        CommercialSegment segment = requireSegmentForUpdate(segmentId);
        long catalogRevision = catalogVersionService.currentRevision();
        Instant evaluatedAt = clock.instant();
        var evaluation = audienceResolver.evaluate(segment, evaluatedAt);
        UUID actor = adminMutationAuthorizer.currentActorUserId();
        var evidence = previewTokenService.requireValid(request.previewToken(),
                CommercialPreviewKind.COMMERCIAL_SEGMENT_ACTIVATION,
                segment.getId(), segment.getVersion(), actor, catalogRevision,
                EVIDENCE_REGISTRY_VERSION, evaluation.fingerprint());
        if (request.version() != segment.getVersion()) throw new StaleActivationPreviewException();
        List<CommercialSegmentBlocker> blockers = previewBlockers(segment, evaluation);
        if (!blockers.isEmpty()) {
            throw new InvalidStateException("Segment cannot be activated: " + blockers + ".");
        }
        translateState(segment::activate);
        segmentRepository.save(segment);
        int activationNumber = activationRepository.findMaximumActivationNumber(segmentId) + 1;
        activationRepository.save(CommercialSegmentActivation.record(
                segment, activationNumber, actor, evidence.evaluatedAt(), evidence.expiresAt(),
                request.version(), evaluation.fingerprint(), request.reason(), evaluation.accountIds()));
        segmentRepository.flush();
        activationRepository.flush();
        return toDetail(requireSegment(segmentId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "archive", description = "Archive a safe Segment revision")
    public CommercialSegmentViews.Detail archive(
            UUID segmentId,
            CommercialSegmentRequests.VersionReason request
    ) {
        CommercialSegment segment = requireSegmentForUpdate(segmentId);
        requireVersion(segment, request.version());
        long references = policyRepository.countBySegmentReference(segment.getCode());
        if (references > 0) {
            throw new InvalidStateException(
                    "Segment cannot be archived while commercial policies reference it.");
        }
        translateState(segment::archive);
        segmentRepository.saveAndFlush(segment);
        return toDetail(requireSegment(segmentId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "delete_draft", description = "Delete an unused Segment draft")
    public void deleteDraft(UUID segmentId, CommercialSegmentRequests.VersionReason request) {
        CommercialSegment segment = requireSegmentForUpdate(segmentId);
        requireVersion(segment, request.version());
        if (segment.getStatus() != CommercialSegmentStatus.DRAFT) {
            throw new InvalidStateException("Only an unpublished Segment draft can be deleted.");
        }
        if (activationRepository.existsBySegment_Id(segmentId)) {
            throw new InvalidStateException("A Segment with activation history cannot be deleted.");
        }
        if (policyRepository.countBySegmentReference(segment.getCode()) > 0) {
            throw new InvalidStateException("A Segment referenced by a commercial policy cannot be deleted.");
        }
        segmentRepository.delete(segment);
        segmentRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_activations", description = "Read immutable Segment activations")
    public Page<CommercialSegmentViews.Activation> activations(UUID segmentId, Pageable pageable) {
        requireSegmentExists(segmentId);
        return activationRepository.findAllBySegment_Id(segmentId,
                historyPage(pageable, "activationNumber")).map(this::toActivation);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_activation_audience",
            description = "Read opaque Account references in a Segment activation")
    public CommercialSegmentViews.ActivationAudience activationAudience(
            UUID segmentId,
            UUID activationId,
            Pageable pageable
    ) {
        CommercialSegmentActivation activation = requireActivation(segmentId, activationId);
        Page<UUID> ids = activationRepository.findSnapshotAccountIds(
                segmentId, activationId, boundedPage(pageable));
        var references = new PageImpl<>(ids.getContent().stream()
                .map(CommercialSegmentViews.AudienceReference::new).toList(),
                ids.getPageable(), ids.getTotalElements());
        return new CommercialSegmentViews.ActivationAudience(segmentId, activationId,
                activation.getActivationNumber(), activation.getAffectedAccountCount(),
                PageResponse.from(references));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_activation_identities",
            description = "Read sensitive Account identities in a Segment activation")
    public CommercialSegmentViews.ActivationIdentityAudience activationIdentities(
            UUID segmentId,
            UUID activationId,
            Pageable pageable
    ) {
        CommercialSegmentActivation activation = requireActivation(segmentId, activationId);
        Page<UUID> ids = activationRepository.findSnapshotAccountIds(
                segmentId, activationId, boundedPage(pageable));
        var identities = new PageImpl<>(audienceResolver.identities(ids.getContent()),
                ids.getPageable(), ids.getTotalElements());
        return new CommercialSegmentViews.ActivationIdentityAudience(segmentId, activationId,
                activation.getActivationNumber(), activation.getAffectedAccountCount(),
                PageResponse.from(identities));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_owner", description = "Read the separately authorized Segment owner")
    public CommercialSegmentViews.Owner owner(UUID segmentId) {
        UUID ownerId = segmentRepository.findOwnerAdminUserIdById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialSegment", "id", segmentId));
        AdminUser owner = adminUserRepository.findWithUserById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "id", ownerId));
        var user = owner.getUser();
        return new CommercialSegmentViews.Owner(segmentId, owner.getId(), user.getId(),
                user.getEmail(), user.getUsername(), user.getFullName(), owner.isActive());
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "reassign_owner", description = "Reassign a draft Segment owner")
    public CommercialSegmentViews.Detail reassignOwner(
            UUID segmentId,
            CommercialSegmentRequests.ReassignOwner request
    ) {
        CommercialSegment segment = requireSegmentForUpdate(segmentId);
        requireVersion(segment, request.version());
        AdminUser owner = adminUserRepository.findWithUserById(request.ownerAdminUserId())
                .filter(AdminUser::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active AdminUser", "id", request.ownerAdminUserId()));
        translateState(() -> segment.reassignDraftOwner(owner));
        segmentRepository.saveAndFlush(segment);
        return toDetail(requireSegment(segmentId));
    }

    private void applyDefinition(
            CommercialSegment segment,
            CommercialSegmentKind kind,
            CommercialSegmentRequests.Definition definition
    ) {
        if (kind == CommercialSegmentKind.EXPLICIT_ACCOUNTS) {
            List<Account> accounts = accountRepository.findAllById(definition.explicitAccountIds());
            if (accounts.size() != definition.explicitAccountIds().size()) {
                throw new ResourceNotFoundException(
                        "Account", "ids", definition.explicitAccountIds());
            }
            translateState(() -> segment.configureExplicitAccounts(accounts));
            return;
        }
        CommercialSegmentRequests.Criteria criteria = definition.criteria();
        var selectedPlanRevisions = planRepository.findAllById(criteria.currentPlanRevisionIds());
        if (selectedPlanRevisions.size() != criteria.currentPlanRevisionIds().size()) {
            throw new ResourceNotFoundException(
                    "Plan", "ids", criteria.currentPlanRevisionIds());
        }
        Set<CommercialSegmentProductSelection> holdings = criteria.productHoldings().stream()
                .map(item -> CommercialSegmentProductSelection.of(item.type(), item.code()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (holdings.size() != criteria.productHoldings().size()) {
            throw new InvalidRequestException("Product holding criteria must be unique after normalization.");
        }
        validateHoldingReferences(holdings);
        Set<String> selectedPlanProducts = holdings.stream()
                .filter(item -> item.getType() == CommercialSegmentProductType.PLAN)
                .map(CommercialSegmentProductSelection::getCode)
                .collect(Collectors.toSet());
        boolean onlyPlanProducts = !holdings.isEmpty()
                && holdings.stream().allMatch(item -> item.getType() == CommercialSegmentProductType.PLAN);
        if (!selectedPlanRevisions.isEmpty() && onlyPlanProducts
                && selectedPlanRevisions.stream().map(item -> item.getCode().toUpperCase(Locale.ROOT))
                .noneMatch(selectedPlanProducts::contains)) {
            throw new InvalidRequestException(
                    "Current Plan revision and Plan product criteria cannot match the same subscription.");
        }
        translateState(() -> segment.configureCriteria(
                criteria.currentPlanRevisionIds(), criteria.subscriptionStatuses(),
                criteria.currencyCodes(), criteria.billingCycles(), criteria.accountCreatedFrom(),
                criteria.accountCreatedUntil(), holdings));
    }

    private void validateHoldingReferences(Set<CommercialSegmentProductSelection> holdings) {
        Map<CommercialSegmentProductType, Set<String>> requested = holdings.stream()
                .collect(Collectors.groupingBy(CommercialSegmentProductSelection::getType,
                        () -> new java.util.EnumMap<>(CommercialSegmentProductType.class),
                        Collectors.mapping(CommercialSegmentProductSelection::getCode,
                                Collectors.toCollection(LinkedHashSet::new))));
        validateHoldingCodes(CommercialSegmentProductType.PLAN,
                requested.getOrDefault(CommercialSegmentProductType.PLAN, Set.of()),
                planRepository::findCodesByCodeIn);
        validateHoldingCodes(CommercialSegmentProductType.ADD_ON,
                requested.getOrDefault(CommercialSegmentProductType.ADD_ON, Set.of()),
                addOnRepository::findCodesByCodeIn);
        validateHoldingCodes(CommercialSegmentProductType.QUOTA_PACKAGE,
                requested.getOrDefault(CommercialSegmentProductType.QUOTA_PACKAGE, Set.of()),
                quotaPackageRepository::findCodesByCodeIn);
    }

    private void validateHoldingCodes(
            CommercialSegmentProductType type,
            Set<String> requested,
            java.util.function.Function<java.util.Collection<String>, List<String>> finder
    ) {
        if (requested.isEmpty()) return;
        Set<String> found = new LinkedHashSet<>(finder.apply(requested));
        requested.stream().filter(code -> !found.contains(code)).sorted().findFirst()
                .ifPresent(code -> {
                    throw new ResourceNotFoundException(type.name(), "code", code);
                });
    }

    private void validateRequest(
            CommercialSegmentKind kind,
            CommercialSegmentRequests.Definition definition
    ) {
        if (kind == null || definition == null) {
            throw new InvalidRequestException("Segment kind and definition are required.");
        }
        if (kind == CommercialSegmentKind.EXPLICIT_ACCOUNTS) {
            if (definition.explicitAccountIds().isEmpty() || definition.criteria() != null) {
                throw new InvalidRequestException(
                        "EXPLICIT_ACCOUNTS requires only a non-empty explicitAccountIds set.");
            }
            return;
        }
        if (!definition.explicitAccountIds().isEmpty() || definition.criteria() == null) {
            throw new InvalidRequestException(
                    "TYPED_CRITERIA requires only one typed criteria object.");
        }
        CommercialSegmentRequests.Criteria criteria = definition.criteria();
        if (criteria.subscriptionStatuses().stream().anyMatch(status -> !CURRENT_STATUSES.contains(status))) {
            throw new InvalidRequestException(
                    "Current subscription status criteria cannot include terminal history states.");
        }
        for (String currency : criteria.currencyCodes()) {
            if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) {
                throw new InvalidRequestException("Currency criteria must use three-letter ISO codes.");
            }
        }
        Instant from = criteria.accountCreatedFrom();
        Instant until = criteria.accountCreatedUntil();
        Instant latest = clock.instant().plus(Duration.ofDays(1));
        if ((from != null && (from.isBefore(EARLIEST_ACCOUNT_DATE) || from.isAfter(latest)))
                || (until != null && (until.isBefore(EARLIEST_ACCOUNT_DATE) || until.isAfter(latest)))) {
            throw new InvalidRequestException("Account creation bounds are outside the supported range.");
        }
        if (from != null && until != null) {
            if (!until.isAfter(from)) {
                throw new InvalidRequestException("Account creation end must be after the start.");
            }
            if (Duration.between(from, until).compareTo(MAX_ACCOUNT_DATE_RANGE) > 0) {
                throw new InvalidRequestException("Account creation range cannot exceed 20 years.");
            }
        }
        boolean empty = criteria.currentPlanRevisionIds().isEmpty()
                && criteria.subscriptionStatuses().isEmpty()
                && criteria.currencyCodes().isEmpty()
                && criteria.billingCycles().isEmpty()
                && from == null && until == null
                && criteria.productHoldings().isEmpty();
        if (empty) {
            throw new InvalidRequestException("A typed Segment requires at least one criterion.");
        }
    }

    private CommercialSegmentViews.Detail toDetail(CommercialSegment segment) {
        int explicitCount = segment.getKind() == CommercialSegmentKind.EXPLICIT_ACCOUNTS
                ? segment.getExplicitAccounts().size() : 0;
        Integer latestActivation = activationRepository
                .findTopBySegment_IdOrderByActivationNumberDesc(segment.getId())
                .map(CommercialSegmentActivation::getAffectedAccountCount).orElse(null);
        int maximum = segmentRepository.findMaximumMaterialRevisionNumber(
                segment.getLineageId(), CommercialSegmentStatus.ARCHIVED);
        int references = Math.toIntExact(policyRepository.countBySegmentReference(segment.getCode()));
        return new CommercialSegmentViews.Detail(
                toSummary(segment, explicitCount, latestActivation, maximum, references,
                        actionPermissions()),
                segment.getDescription(), segment.getReason(), toDefinition(segment),
                segment.getSourceSegment() == null ? null : segment.getSourceSegment().getId());
    }

    private CommercialSegmentViews.Summary toSummary(
            CommercialSegment segment,
            int configuredCount,
            Integer latestActivationCount,
            int maximumRevision,
            int policyReferences,
            ActionPermissions permissions
    ) {
        Map<CommercialSegmentAction, List<CommercialSegmentBlocker>> blockedActions =
                blockedActions(segment, maximumRevision, policyReferences,
                        latestActivationCount != null, permissions);
        List<CommercialSegmentAction> actions = availableActions(
                segment, maximumRevision == segment.getRevisionNumber(), policyReferences, permissions);
        return new CommercialSegmentViews.Summary(
                segment.getId(), segment.getCode(), segment.getName(), segment.getStatus(),
                segment.getKind(), segment.getSource(), configuredCount, latestActivationCount,
                segment.getLineageId(), segment.getRevisionNumber(), segment.getCreationReason(),
                segment.getVersion(), segment.getCreatedAt(), segment.getUpdatedAt(), actions,
                blockedActions,
                true, true);
    }

    private CommercialSegmentViews.Definition toDefinition(CommercialSegment segment) {
        if (segment.getKind() == CommercialSegmentKind.EXPLICIT_ACCOUNTS) {
            return new CommercialSegmentViews.Definition(
                    segment.getExplicitAccounts().stream().map(Account::getId)
                            .collect(Collectors.toCollection(LinkedHashSet::new)), null);
        }
        Set<CommercialSegmentViews.ProductHolding> holdings = segment.getProductHoldings().stream()
                .map(item -> new CommercialSegmentViews.ProductHolding(item.getType(), item.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new CommercialSegmentViews.Definition(Set.of(),
                new CommercialSegmentViews.Criteria(
                        segment.getCurrentPlanRevisionIds(), segment.getSubscriptionStatuses(),
                        segment.getCurrencyCodes(), segment.getBillingCycles(),
                        segment.getAccountCreatedFrom(), segment.getAccountCreatedUntil(), holdings,
                        "AND_ACROSS_FIELDS_OR_WITHIN_SELECTED_VALUES"));
    }

    private CommercialSegmentViews.Activation toActivation(CommercialSegmentActivation activation) {
        return new CommercialSegmentViews.Activation(
                activation.getId(), activation.getActivationNumber(), activation.getSegment().getId(),
                activation.getActorUserId(), activation.getEvaluatedAt(), activation.getEvidenceExpiresAt(),
                activation.getCriteriaVersion(), activation.getAffectedAccountCount(), activation.getReason(),
                activation.getCreatedAt());
    }

    private List<CommercialSegmentBlocker> previewBlockers(
            CommercialSegment segment,
            CommercialSegmentAudienceResolver.Evaluation evaluation
    ) {
        List<CommercialSegmentBlocker> blockers = new ArrayList<>();
        if (segment.getStatus() != CommercialSegmentStatus.DRAFT) {
            blockers.add(CommercialSegmentBlocker.NOT_DRAFT);
        }
        if (evaluation.total() == 0) blockers.add(CommercialSegmentBlocker.EMPTY_AUDIENCE);
        if (!evaluation.withinLimit()) {
            blockers.add(CommercialSegmentBlocker.AUDIENCE_EXCEEDS_ACTIVATION_LIMIT);
        }
        return List.copyOf(blockers);
    }

    private Map<CommercialSegmentAction, List<CommercialSegmentBlocker>> blockedActions(
            CommercialSegment segment,
            int maximumRevision,
            int policyReferences,
            boolean hasActivationHistory,
            ActionPermissions permissions
    ) {
        Map<CommercialSegmentAction, List<CommercialSegmentBlocker>> blocked =
                new java.util.EnumMap<>(CommercialSegmentAction.class);
        if (segment.getStatus() != CommercialSegmentStatus.DRAFT) {
            block(blocked, CommercialSegmentAction.EDIT_DRAFT, CommercialSegmentBlocker.NOT_DRAFT);
            block(blocked, CommercialSegmentAction.ACTIVATE, CommercialSegmentBlocker.NOT_DRAFT);
            block(blocked, CommercialSegmentAction.DELETE_DRAFT, CommercialSegmentBlocker.NOT_DRAFT);
            block(blocked, CommercialSegmentAction.REASSIGN_OWNER, CommercialSegmentBlocker.NOT_DRAFT);
        }
        if (segment.getStatus() != CommercialSegmentStatus.ACTIVE) {
            block(blocked, CommercialSegmentAction.REVISE, CommercialSegmentBlocker.NOT_ACTIVE);
        }
        if (maximumRevision > segment.getRevisionNumber()) {
            block(blocked, CommercialSegmentAction.REVISE,
                    CommercialSegmentBlocker.NOT_LATEST_REVISION);
        }
        if (segment.getStatus() == CommercialSegmentStatus.ARCHIVED) {
            block(blocked, CommercialSegmentAction.ARCHIVE,
                    CommercialSegmentBlocker.ALREADY_ARCHIVED);
        }
        if (policyReferences > 0) {
            block(blocked, CommercialSegmentAction.ARCHIVE,
                    CommercialSegmentBlocker.HAS_POLICY_REFERENCES);
            block(blocked, CommercialSegmentAction.DELETE_DRAFT,
                    CommercialSegmentBlocker.HAS_POLICY_REFERENCES);
        }
        if (hasActivationHistory) {
            block(blocked, CommercialSegmentAction.DELETE_DRAFT,
                    CommercialSegmentBlocker.HAS_ACTIVATION_HISTORY);
        }
        blocked.entrySet().removeIf(entry -> !permissions.allows(permissionFor(entry.getKey())));
        return Map.copyOf(blocked);
    }

    private void block(
            Map<CommercialSegmentAction, List<CommercialSegmentBlocker>> blocked,
            CommercialSegmentAction action,
            CommercialSegmentBlocker blocker
    ) {
        blocked.computeIfAbsent(action, ignored -> new ArrayList<>()).add(blocker);
    }

    private List<CommercialSegmentAction> availableActions(
            CommercialSegment segment,
            boolean latestRevision,
            int policyReferences,
            ActionPermissions permissions
    ) {
        EnumSet<CommercialSegmentAction> actions = EnumSet.of(
                CommercialSegmentAction.DUPLICATE,
                CommercialSegmentAction.COMPARE,
                CommercialSegmentAction.READ_HISTORY,
                CommercialSegmentAction.READ_REVISIONS,
                CommercialSegmentAction.READ_ACTIVATIONS,
                CommercialSegmentAction.READ_ACTIVATION_AUDIENCE,
                CommercialSegmentAction.READ_ACTIVATION_IDENTITIES,
                CommercialSegmentAction.READ_OWNER);
        if (segment.getStatus() != CommercialSegmentStatus.ARCHIVED) {
            actions.add(CommercialSegmentAction.PREVIEW);
            actions.add(CommercialSegmentAction.READ_SAMPLE_IDENTITIES);
        }
        if (segment.getStatus() == CommercialSegmentStatus.DRAFT) {
            actions.add(CommercialSegmentAction.EDIT_DRAFT);
            actions.add(CommercialSegmentAction.ACTIVATE);
            actions.add(CommercialSegmentAction.ARCHIVE);
            actions.add(CommercialSegmentAction.DELETE_DRAFT);
            actions.add(CommercialSegmentAction.REASSIGN_OWNER);
        } else if (segment.getStatus() == CommercialSegmentStatus.ACTIVE) {
            if (latestRevision) actions.add(CommercialSegmentAction.REVISE);
            if (policyReferences == 0) actions.add(CommercialSegmentAction.ARCHIVE);
        }
        actions.add(CommercialSegmentAction.COUNT);
        actions.removeIf(action -> !permissions.allows(permissionFor(action)));
        return List.copyOf(actions);
    }

    private String permissionFor(CommercialSegmentAction action) {
        return switch (action) {
            case EDIT_DRAFT -> "platform.segments.update_draft";
            case DUPLICATE -> "platform.segments.duplicate";
            case COUNT -> "platform.segments.count";
            case PREVIEW -> "platform.segments.preview";
            case READ_SAMPLE_IDENTITIES -> "platform.segments.read_sample_identities";
            case ACTIVATE -> "platform.segments.activate";
            case REVISE -> "platform.segments.revise";
            case COMPARE -> "platform.segments.compare";
            case ARCHIVE -> "platform.segments.archive";
            case DELETE_DRAFT -> "platform.segments.delete_draft";
            case READ_HISTORY -> "platform.segments.read_history";
            case READ_REVISIONS -> "platform.segments.read_revisions";
            case READ_ACTIVATIONS -> "platform.segments.read_activations";
            case READ_ACTIVATION_AUDIENCE -> "platform.segments.read_activation_audience";
            case READ_ACTIVATION_IDENTITIES -> "platform.segments.read_activation_identities";
            case READ_OWNER -> "platform.segments.read_owner";
            case REASSIGN_OWNER -> "platform.segments.reassign_owner";
        };
    }

    private Set<String> changedFields(CommercialSegment source, CommercialSegment compared) {
        Set<String> changed = new LinkedHashSet<>();
        changed(changed, "NAME", source.getName(), compared.getName());
        changed(changed, "DESCRIPTION", source.getDescription(), compared.getDescription());
        changed(changed, "KIND", source.getKind(), compared.getKind());
        changed(changed, "SOURCE", source.getSource(), compared.getSource());
        changed(changed, "REASON", source.getReason(), compared.getReason());
        changed(changed, "DEFINITION", definitionSignature(source), definitionSignature(compared));
        changed(changed, "OWNER", source.getOwner().getId(), compared.getOwner().getId());
        return Set.copyOf(changed);
    }

    private String definitionSignature(CommercialSegment segment) {
        if (segment.getKind() == CommercialSegmentKind.EXPLICIT_ACCOUNTS) {
            return segment.getExplicitAccounts().stream().map(Account::getId).sorted()
                    .map(UUID::toString).collect(Collectors.joining(","));
        }
        return String.join("|",
                segment.getCurrentPlanRevisionIds().stream().sorted().map(UUID::toString)
                        .collect(Collectors.joining(",")),
                segment.getSubscriptionStatuses().stream().map(Enum::name).sorted()
                        .collect(Collectors.joining(",")),
                segment.getCurrencyCodes().stream().sorted().collect(Collectors.joining(",")),
                segment.getBillingCycles().stream().map(Enum::name).sorted()
                        .collect(Collectors.joining(",")),
                String.valueOf(segment.getAccountCreatedFrom()),
                String.valueOf(segment.getAccountCreatedUntil()),
                segment.getProductHoldings().stream()
                        .map(item -> item.getType().name() + ":" + item.getCode()).sorted()
                        .collect(Collectors.joining(",")));
    }

    private void changed(Set<String> fields, String field, Object left, Object right) {
        if (!Objects.equals(left, right)) fields.add(field);
    }

    private Map<UUID, Integer> groupedInteger(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));
    }

    private Map<UUID, Integer> maximumRevisions(List<CommercialSegment> segments) {
        if (segments.isEmpty()) return Map.of();
        return segmentRepository.findMaximumMaterialRevisionNumbers(segments.stream()
                        .map(CommercialSegment::getLineageId).collect(Collectors.toSet()),
                        CommercialSegmentStatus.ARCHIVED).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));
    }

    private Map<String, Integer> policyReferenceCounts(List<CommercialSegment> segments) {
        if (segments.isEmpty()) return Map.of();
        return policyRepository.countBySegmentReferences(segments.stream()
                        .map(CommercialSegment::getCode).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], row -> ((Number) row[1]).intValue()));
    }

    private CommercialSegment requireSegment(UUID id) {
        CommercialSegment segment = segmentRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialSegment", "id", id));
        initializeDefinition(segment);
        return segment;
    }

    /** Loads each bounded definition collection separately to avoid a six-way Cartesian join. */
    private void initializeDefinition(CommercialSegment segment) {
        segment.getExplicitAccounts().size();
        segment.getCurrentPlanRevisionIds().size();
        segment.getSubscriptionStatuses().size();
        segment.getCurrencyCodes().size();
        segment.getBillingCycles().size();
        segment.getProductHoldings().size();
    }

    private CommercialSegment requireSegmentForUpdate(UUID id) {
        segmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialSegment", "id", id));
        return requireSegment(id);
    }

    private void requireSegmentExists(UUID id) {
        if (!segmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("CommercialSegment", "id", id);
        }
    }

    private CommercialSegmentActivation requireActivation(UUID segmentId, UUID activationId) {
        return activationRepository.findByIdAndSegment_Id(activationId, segmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CommercialSegmentActivation", "id", activationId));
    }

    private AdminUser currentOwner() {
        UUID adminUserId = adminMutationAuthorizer.currentActorAdminUserId();
        return adminUserRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "id", adminUserId));
    }

    private String nextCode(String name) {
        return CommercialCodeGenerator.generate(name, "SEGMENT", segmentRepository::existsByCode);
    }

    private void requireVersion(CommercialSegment segment, long expected) {
        if (segment.getVersion() != expected) {
            throw new StaleResourceVersionException(
                    "Segment changed since it was read. Reload it and retry.");
        }
    }

    private Pageable historyPage(Pageable pageable, String property) {
        Pageable bounded = boundedPage(pageable);
        return PageRequest.of(bounded.getPageNumber(), bounded.getPageSize(),
                Sort.by(Sort.Direction.DESC, property).and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private Pageable boundedPage(Pageable pageable) {
        if (pageable == null || pageable.getPageNumber() < 0
                || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
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

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void translateState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new InvalidStateException(exception.getMessage());
        }
    }

    private <T> T translateState(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalStateException | IllegalArgumentException exception) {
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

        private boolean allows(String permission) {
            return decisions.computeIfAbsent(permission, ceiling::allows);
        }
    }
}
