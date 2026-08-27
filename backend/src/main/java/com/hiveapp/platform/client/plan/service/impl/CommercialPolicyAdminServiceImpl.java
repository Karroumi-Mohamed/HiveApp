package com.hiveapp.platform.client.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyExecutionBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicyActivation;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicyEffect;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyActivationRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyRequests;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyViews;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialCodeGenerator;
import com.hiveapp.platform.client.plan.service.CommercialPolicyActivationAssessor;
import com.hiveapp.platform.client.plan.service.CommercialPolicyAdminService;
import com.hiveapp.platform.client.plan.service.CommercialPolicyAudienceResolver;
import com.hiveapp.platform.client.plan.service.CommercialPolicyPrecedence;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.registry.definition.CommercialPoliciesFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.exception.DraftSuccessorExistsException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleActivationPreviewException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import com.hiveapp.shared.money.Money;
import dev.karroumi.permissionizer.PermissionNode;
import jakarta.persistence.criteria.JoinType;
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

import java.math.BigDecimal;
import java.time.Clock;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = CommercialPoliciesFeature.KEY,
        description = "Typed commercial policy management", guard = PermissionNode.Guard.ON)
public class CommercialPolicyAdminServiceImpl extends PlatformControlFeatureService
        implements CommercialPolicyAdminService {

    private static final String AUDIT_RESOURCE_TYPE = "COMMERCIAL_POLICY_ADMIN";
    private static final List<CommercialPolicyExecutionBlocker> EXECUTION_BLOCKERS = List.of(
            CommercialPolicyExecutionBlocker.SCHEDULED_EXECUTION_NOT_AVAILABLE);

    private final CommercialPolicyRepository policyRepository;
    private final CommercialPolicyActivationRepository activationRepository;
    private final AccountRepository accountRepository;
    private final AccountDirectoryService accountDirectoryService;
    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final FeatureRepository featureRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminMutationAuthorizer adminMutationAuthorizer;
    private final CommercialPolicyAudienceResolver audienceResolver;
    private final CommercialPolicyActivationAssessor activationAssessor;
    private final CommercialCatalogVersionService catalogVersionService;
    private final RegistryCatalogVersionService registryVersionService;
    private final CommercialPreviewTokenService previewTokenService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    protected FeatureDefinition featureDefinition() {
        return CommercialPoliciesFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list", description = "List and filter commercial policy revisions")
    public Page<CommercialPolicyViews.Summary> list(
            String search,
            CommercialPolicyStatus status,
            CommercialPolicyTargetKind targetKind,
            CommercialPolicySource source,
            Instant effectiveAt,
            boolean includeArchived,
            Pageable pageable
    ) {
        Specification<CommercialPolicy> specification = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!includeArchived && status != CommercialPolicyStatus.ARCHIVED) {
                predicates.add(cb.notEqual(root.get("status"), CommercialPolicyStatus.ARCHIVED));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (targetKind != null) predicates.add(cb.equal(root.get("targetKind"), targetKind));
            if (source != null) predicates.add(cb.equal(root.get("source"), source));
            if (effectiveAt != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveAt));
                predicates.add(cb.or(cb.isNull(root.get("effectiveUntil")),
                        cb.greaterThan(root.get("effectiveUntil"), effectiveAt)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<CommercialPolicy> page = policyRepository.findAll(specification, pageable);
        List<UUID> ids = page.getContent().stream().map(CommercialPolicy::getId).toList();
        Map<UUID, Integer> effectCounts = groupedInteger(policyRepository.countEffectsByPolicyIds(ids));
        Map<UUID, Integer> targetCounts = groupedInteger(policyRepository.countExplicitAccountsByPolicyIds(ids));
        Map<UUID, Integer> snapshotCounts = groupedInteger(activationRepository.countLatestSnapshotAccounts(ids));
        Map<UUID, Integer> maximumRevisions = maximumRevisions(page.getContent());
        ActionPermissions permissions = actionPermissions();
        return page.map(policy -> {
            int configuredTargets = targetCounts.containsKey(policy.getId())
                    ? targetCounts.get(policy.getId())
                    : policy.getTargetKind() == CommercialPolicyTargetKind.ACCOUNT ? 1 : 0;
            return toSummary(policy,
                    effectCounts.getOrDefault(policy.getId(), 0), configuredTargets,
                    snapshotCounts.get(policy.getId()),
                    maximumRevisions.getOrDefault(policy.getLineageId(), policy.getRevisionNumber()),
                    permissions);
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "Read a commercial policy definition")
    public CommercialPolicyViews.Detail get(UUID policyId) {
        return toDetail(requirePolicy(policyId));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "create", description = "Create a draft commercial policy")
    public CommercialPolicyViews.Detail create(CommercialPolicyRequests.Create request) {
        validateTerms(request.effectiveFrom(), request.effectiveUntil(), request.priority());
        AdminUser owner = currentOwner();
        CommercialPolicy policy = CommercialPolicy.draft(
                nextCode(request.name()), request.name(), request.description(), owner,
                request.effectiveFrom(), request.effectiveUntil(), request.source(), request.priority(),
                request.reason(), request.approvalReference(), request.contractReference());
        applyTarget(policy, request.target());
        policy.replaceEffects(buildEffects(policy, request.effects()));
        return toDetail(policyRepository.saveAndFlush(policy));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_draft", description = "Edit a draft commercial policy")
    public CommercialPolicyViews.Detail update(UUID policyId, CommercialPolicyRequests.Update request) {
        validateTerms(request.effectiveFrom(), request.effectiveUntil(), request.priority());
        CommercialPolicy policy = requirePolicyForUpdate(policyId);
        requireVersion(policy, request.version());
        translateState(() -> policy.editDraft(
                request.name(), request.description(), policy.getOwner(), request.effectiveFrom(),
                request.effectiveUntil(), request.source(), request.priority(), request.reason(),
                request.approvalReference(), request.contractReference()));
        applyTarget(policy, request.target());
        policy.replaceEffects(buildEffects(policy, request.effects()));
        return toDetail(policyRepository.saveAndFlush(policy));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "duplicate", description = "Duplicate a commercial policy into a new lineage")
    public CommercialPolicyViews.Detail duplicate(
            UUID policyId,
            CommercialPolicyRequests.Duplicate request
    ) {
        CommercialPolicy source = requirePolicy(policyId);
        requireVersion(source, request.version());
        CommercialPolicy copy = translateState(() -> source.duplicate(
                nextCode(request.name()), request.name(), currentOwner(), request.reason()));
        copyConfiguration(source, copy);
        return toDetail(policyRepository.saveAndFlush(copy));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "revise", description = "Create an immutable successor policy revision")
    public CommercialPolicyViews.Detail revise(
            UUID policyId,
            CommercialPolicyRequests.VersionReason request
    ) {
        CommercialPolicy hint = requirePolicy(policyId);
        List<CommercialPolicy> lineage = policyRepository.findLineageForUpdate(hint.getLineageId());
        CommercialPolicy source = lineage.stream().filter(candidate -> candidate.getId().equals(policyId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("CommercialPolicy", "id", policyId));
        requireVersion(source, request.version());
        int maximum = lineage.stream().mapToInt(CommercialPolicy::getRevisionNumber).max().orElse(0);
        int maximumMaterial = policyRepository.findMaximumMaterialRevisionNumber(
                source.getLineageId(), CommercialPolicyStatus.ARCHIVED);
        if (source.getRevisionNumber() != maximumMaterial) {
            throw new InvalidStateException(
                    "Only the latest published or retained policy revision can be revised.");
        }
        if (lineage.stream().anyMatch(candidate -> candidate.getStatus() == CommercialPolicyStatus.DRAFT
                && !candidate.getId().equals(source.getId()))) {
            throw new DraftSuccessorExistsException("This policy lineage already has a draft successor.");
        }
        CommercialPolicy successor = translateState(() -> source.revise(
                nextCode(source.getName()), currentOwner(), request.reason(), maximum + 1));
        copyConfiguration(source, successor);
        try {
            return toDetail(policyRepository.saveAndFlush(successor));
        } catch (DataIntegrityViolationException exception) {
            throw new DraftSuccessorExistsException(
                    "A concurrent request already created this policy revision.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "compare", description = "Compare two commercial policy revisions")
    public CommercialPolicyViews.Comparison compare(UUID policyId, UUID comparedPolicyId) {
        CommercialPolicy source = requirePolicy(policyId);
        CommercialPolicy compared = requirePolicy(comparedPolicyId);
        Set<String> changed = changedFields(source, compared);
        boolean sameLineage = source.getLineageId().equals(compared.getLineageId());
        boolean direct = sameLineage && (referencesSource(compared, source)
                || referencesSource(source, compared));
        return new CommercialPolicyViews.Comparison(
                source.getId(), compared.getId(), sameLineage, direct, changed,
                toDetail(source), toDetail(compared));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_revisions", description = "Read policy revision lineage")
    public Page<CommercialPolicyViews.Revision> revisions(UUID policyId, Pageable pageable) {
        UUID lineageId = policyRepository.findLineageIdById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CommercialPolicy", "id", policyId));
        Pageable bounded = historyPage(pageable, "revisionNumber");
        return policyRepository.findAllByLineageId(lineageId, bounded)
                .map(candidate -> new CommercialPolicyViews.Revision(
                        candidate.getId(), candidate.getCode(), candidate.getStatus(),
                        candidate.getRevisionNumber(),
                        candidate.getSourcePolicy() == null ? null : candidate.getSourcePolicy().getId(),
                        candidate.getVersion(), candidate.getCreatedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_history", description = "Read commercial policy mutation history")
    public Page<CommercialPolicyViews.History> history(UUID policyId, Pageable pageable) {
        requirePolicyExists(policyId);
        Pageable bounded = historyPage(pageable, "occurredAt");
        Page<AuditLog> logs = auditLogRepository.findAllByResourceTypeAndResourceId(
                AUDIT_RESOURCE_TYPE, policyId.toString(), bounded);
        Set<UUID> actorIds = logs.getContent().stream().map(AuditLog::getActorUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> actorEmails = actorIds.isEmpty() ? Map.of()
                : adminUserRepository.findAllWithUserByUserIdIn(actorIds).stream()
                        .collect(Collectors.toMap(
                                admin -> admin.getUser().getId(),
                                admin -> admin.getUser().getEmail()));
        return logs.map(log -> new CommercialPolicyViews.History(
                log.getId(), log.getAction(), log.getOutcome(), log.getActorUserId(),
                actorEmails.get(log.getActorUserId()), historyReason(log), log.getOccurredAt()));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_activations", description = "Read immutable policy activation snapshots")
    public Page<CommercialPolicyViews.Activation> activations(UUID policyId, Pageable pageable) {
        requirePolicyExists(policyId);
        Pageable bounded = historyPage(pageable, "activationNumber");
        return activationRepository.findAllByPolicy_Id(policyId, bounded).map(this::toActivation);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_activation_accounts",
            description = "Read a bounded immutable activation Account snapshot")
    public CommercialPolicyViews.ActivationAudience activationAudience(
            UUID policyId,
            UUID activationId,
            Pageable pageable
    ) {
        Pageable bounded = boundedPage(pageable);
        CommercialPolicyActivation activation = activationRepository.findByIdAndPolicy_Id(
                        activationId, policyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CommercialPolicyActivation", "id", activationId));
        Page<UUID> ids = activationRepository.findSnapshotAccountIds(
                policyId, activationId, bounded);
        var accounts = new PageImpl<>(audienceResolver.summaries(ids.getContent()),
                ids.getPageable(), ids.getTotalElements());
        return new CommercialPolicyViews.ActivationAudience(
                policyId, activationId, activation.getActivationNumber(),
                activation.getAffectedAccountCount(), PageResponse.from(accounts));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_audience", description = "Preview a bounded policy Account audience")
    public CommercialPolicyViews.AudiencePreview audience(UUID policyId, int page, int size) {
        return audienceResolver.preview(requirePolicy(policyId), page, size);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PermissionNode(key = "preview_activation", description = "Preview and sign exact policy activation evidence")
    public CommercialPolicyViews.ActivationPreview previewActivation(UUID policyId) {
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        String registryVersion = registryVersionService.currentVersion();
        return catalogVersionService.readConsistently(catalogRevision -> {
            CommercialPolicy policy = requirePolicy(policyId);
            Instant evaluatedAt = clock.instant();
            var assessment = activationAssessor.assess(policy, evaluatedAt);
            var evidence = previewTokenService.issue(
                    CommercialPreviewKind.COMMERCIAL_POLICY_ACTIVATION,
                    policy.getId(), policy.getVersion(), actorUserId, catalogRevision,
                    registryVersion, assessment.fingerprint(), evaluatedAt);
            return toActivationPreview(policy, assessment, catalogRevision, registryVersion, evidence);
        }, StaleActivationPreviewException::new);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CommercialCatalogMutation
    @PermissionNode(key = "activate", description = "Activate a reviewed draft commercial policy")
    public CommercialPolicyViews.Detail activate(
            UUID policyId,
            CommercialPolicyRequests.Activation request
    ) {
        return applyActivation(policyId, request, CommercialPolicyStatus.DRAFT);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CommercialCatalogMutation
    @PermissionNode(key = "resume", description = "Resume a reviewed paused commercial policy")
    public CommercialPolicyViews.Detail resume(
            UUID policyId,
            CommercialPolicyRequests.Activation request
    ) {
        return applyActivation(policyId, request, CommercialPolicyStatus.PAUSED);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "pause", description = "Pause a currently active commercial policy")
    public CommercialPolicyViews.Detail pause(
            UUID policyId,
            CommercialPolicyRequests.VersionReason request
    ) {
        CommercialPolicy policy = requirePolicyForUpdate(policyId);
        requireVersion(policy, request.version());
        translateState(policy::pause);
        return toDetail(policyRepository.saveAndFlush(policy));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "end", description = "Permanently end a commercial policy")
    public CommercialPolicyViews.Detail end(
            UUID policyId,
            CommercialPolicyRequests.VersionReason request
    ) {
        CommercialPolicy policy = requirePolicyForUpdate(policyId);
        requireVersion(policy, request.version());
        translateState(policy::end);
        return toDetail(policyRepository.saveAndFlush(policy));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "archive", description = "Archive a draft or ended commercial policy")
    public CommercialPolicyViews.Detail archive(
            UUID policyId,
            CommercialPolicyRequests.VersionReason request
    ) {
        CommercialPolicy policy = requirePolicyForUpdate(policyId);
        requireVersion(policy, request.version());
        translateState(policy::archive);
        return toDetail(policyRepository.saveAndFlush(policy));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "delete_draft", description = "Delete an unpublished policy draft")
    public void deleteDraft(UUID policyId, CommercialPolicyRequests.VersionReason request) {
        CommercialPolicy policy = requirePolicyForUpdate(policyId);
        requireVersion(policy, request.version());
        if (policy.getStatus() != CommercialPolicyStatus.DRAFT) {
            throw new InvalidStateException("Only an unpublished policy draft can be deleted.");
        }
        if (activationRepository.findMaximumActivationNumber(policyId) > 0) {
            throw new InvalidStateException("A policy with activation evidence cannot be deleted.");
        }
        policyRepository.delete(policy);
        policyRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_owner", description = "Read the separately authorized policy owner identity")
    public CommercialPolicyViews.Owner owner(UUID policyId) {
        UUID ownerAdminUserId = policyRepository.findOwnerAdminUserIdById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CommercialPolicy", "id", policyId));
        AdminUser owner = adminUserRepository.findWithUserById(ownerAdminUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AdminUser", "id", ownerAdminUserId));
        String displayName = (owner.getUser().getFirstName() + " " + owner.getUser().getLastName()).trim();
        return new CommercialPolicyViews.Owner(
                policyId, owner.getId(), owner.getUser().getId(), owner.getUser().getEmail(),
                owner.getUser().getUsername(), displayName.isBlank() ? null : displayName,
                owner.isActive());
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "reassign_owner", description = "Reassign a draft commercial policy owner")
    public CommercialPolicyViews.Detail reassignOwner(
            UUID policyId,
            CommercialPolicyRequests.ReassignOwner request
    ) {
        CommercialPolicy policy = requirePolicyForUpdate(policyId);
        requireVersion(policy, request.version());
        AdminUser owner = adminUserRepository.findWithUserById(request.ownerAdminUserId())
                .filter(AdminUser::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ActiveAdminUser", "id", request.ownerAdminUserId()));
        translateState(() -> policy.reassignDraftOwner(owner));
        return toDetail(policyRepository.saveAndFlush(policy));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "choose_accounts", description = "Choose safe Account policy targets")
    public Page<AccountDirectoryEntryDto> chooseAccounts(
            String query,
            Boolean active,
            Pageable pageable
    ) {
        return accountDirectoryService.search(query, active, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "resolve_account_choices",
            description = "Resolve selected policy Account targets by id")
    public List<AccountDirectoryEntryDto> resolveAccountChoices(java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100
                || ids.stream().anyMatch(Objects::isNull)
                || new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new InvalidRequestException(
                    "Account choice resolution requires 1 to 100 unique non-null ids.");
        }
        return accountDirectoryService.resolve(ids);
    }

    private CommercialPolicyViews.Detail applyActivation(
            UUID policyId,
            CommercialPolicyRequests.Activation request,
            CommercialPolicyStatus requiredStatus
    ) {
        registryVersionService.lockForMutation();
        String registryVersion = registryVersionService.currentVersion();
        UUID lineageId = policyRepository.findLineageIdById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialPolicy", "id", policyId));
        List<CommercialPolicy> lineage = policyRepository.findLineageForUpdate(lineageId);
        CommercialPolicy policy = requirePolicyForUpdate(policyId);
        long catalogRevision = catalogVersionService.currentRevision();
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        Instant assessedAt = clock.instant();
        var assessment = activationAssessor.assess(policy, assessedAt);
        var evidence = previewTokenService.requireValid(
                request.activationPreviewToken(),
                CommercialPreviewKind.COMMERCIAL_POLICY_ACTIVATION,
                policy.getId(), policy.getVersion(), actorUserId, catalogRevision,
                registryVersion, assessment.fingerprint());
        if (request.version() != policy.getVersion()) throw new StaleActivationPreviewException();
        if (policy.getStatus() != requiredStatus) {
            throw new InvalidStateException(
                    "Policy is not in the required " + requiredStatus + " state.");
        }
        if (!assessment.activatable()) {
            throw new InvalidStateException(
                    "Commercial policy cannot be activated: " + assessment.blockers() + ".");
        }
        if (assessment.revisionToEnd() != null) {
            CommercialPolicy replaced = lineage.stream()
                    .filter(candidate -> candidate.getId().equals(assessment.revisionToEnd().getId()))
                    .findFirst().orElseThrow(StaleActivationPreviewException::new);
            translateState(replaced::end);
        }
        translateState(policy::activate);
        policyRepository.saveAll(lineage);
        policyRepository.save(policy);
        int activationNumber = activationRepository.findMaximumActivationNumber(policyId) + 1;
        CommercialPolicyActivation activation = CommercialPolicyActivation.record(
                policy, activationNumber, actorUserId, evidence.evaluatedAt(), evidence.expiresAt(),
                catalogRevision, registryVersion, assessment.fingerprint(), request.reason(),
                assessment.accountIds());
        activationRepository.save(activation);
        policyRepository.flush();
        activationRepository.flush();
        return toDetail(requirePolicy(policyId));
    }

    private void applyTarget(CommercialPolicy policy, CommercialPolicyRequests.Target request) {
        if (request == null || request.kind() == null) {
            throw new InvalidRequestException("Commercial policy target is required.");
        }
        Account account = null;
        List<Account> accounts = List.of();
        Plan plan = null;
        String segment = null;
        switch (request.kind()) {
            case ACCOUNT -> {
                requireOnly(request.accountId() != null
                                && request.accountIds().isEmpty()
                                && request.planRevisionId() == null
                                && request.segmentReference() == null,
                        "ACCOUNT target requires exactly one accountId.");
                account = requireAccount(request.accountId());
            }
            case ACCOUNT_SET -> {
                requireOnly(request.accountId() == null
                                && !request.accountIds().isEmpty()
                                && request.planRevisionId() == null
                                && request.segmentReference() == null,
                        "ACCOUNT_SET target requires only a non-empty accountIds set.");
                accounts = accountRepository.findAllById(request.accountIds());
                if (accounts.size() != request.accountIds().size()) {
                    throw new ResourceNotFoundException(
                            "Account", "ids", request.accountIds());
                }
            }
            case PLAN_REVISION_SUBSCRIBERS -> {
                requireOnly(request.accountId() == null
                                && request.accountIds().isEmpty()
                                && request.planRevisionId() != null
                                && request.segmentReference() == null,
                        "PLAN_REVISION_SUBSCRIBERS target requires exactly one planRevisionId.");
                plan = requirePlan(request.planRevisionId());
            }
            case SEGMENT -> {
                requireOnly(request.accountId() == null
                                && request.accountIds().isEmpty()
                                && request.planRevisionId() == null
                                && request.segmentReference() != null,
                        "SEGMENT target requires exactly one segmentReference.");
                segment = request.segmentReference();
            }
        }
        Account finalAccount = account;
        List<Account> finalAccounts = accounts;
        Plan finalPlan = plan;
        String finalSegment = segment;
        translateState(() -> policy.configureTarget(
                request.kind(), finalAccount, finalAccounts, finalPlan, finalSegment));
    }

    private List<CommercialPolicyEffect> buildEffects(
            CommercialPolicy policy,
            List<CommercialPolicyRequests.Effect> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            throw new InvalidRequestException("A commercial policy requires at least one effect.");
        }
        List<CommercialPolicyEffect> effects = new ArrayList<>();
        Set<String> monetaryCurrencies = new LinkedHashSet<>();
        for (int index = 0; index < requests.size(); index++) {
            CommercialPolicyRequests.Effect request = requests.get(index);
            if (request == null || request.type() == null) {
                throw new InvalidRequestException("Each commercial policy effect requires a type.");
            }
            EffectReferences references = resolveEffectReferences(request);
            Money money = money(request.amount(), request.currencyCode(), "effect money");
            Money maximum = money(request.maximumAmount(), request.maximumCurrencyCode(), "maximum discount");
            validateEffectShape(request, references, money, maximum, policy.getEffectiveUntil());
            if (money != null) monetaryCurrencies.add(money.currencyCode());
            if (maximum != null) monetaryCurrencies.add(maximum.currencyCode());
            effects.add(CommercialPolicyEffect.create(
                    policy, index, request.type(), request.productType(), references.plan(),
                    references.addOn(), references.quotaPackage(), references.feature(),
                    request.quotaResource(), request.quantityDelta(), money, request.billingCycle(),
                    request.percentage(), maximum));
        }
        if (monetaryCurrencies.size() > 1) {
            throw new InvalidRequestException(
                    "All monetary effects in one policy must use the same explicit currency.");
        }
        return List.copyOf(effects);
    }

    private EffectReferences resolveEffectReferences(CommercialPolicyRequests.Effect request) {
        Plan plan = null;
        AddOn addOn = null;
        QuotaPackage quotaPackage = null;
        Feature feature = null;
        if (request.productRevisionId() != null) {
            if (request.productType() == null) {
                throw new InvalidRequestException(
                        "productType is required when productRevisionId is supplied.");
            }
            switch (request.productType()) {
                case PLAN -> plan = requirePlan(request.productRevisionId());
                case ADD_ON -> addOn = requireAddOn(request.productRevisionId());
                case QUOTA_PACKAGE -> quotaPackage = requireQuotaPackage(request.productRevisionId());
            }
        } else if (request.productType() != null) {
            throw new InvalidRequestException(
                    "productRevisionId is required when productType is supplied.");
        }
        if (request.featureCode() != null) {
            feature = featureRepository.findByCode(request.featureCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Feature", "code", request.featureCode()));
        }
        return new EffectReferences(plan, addOn, quotaPackage, feature);
    }

    private void validateEffectShape(
            CommercialPolicyRequests.Effect request,
            EffectReferences references,
            Money money,
            Money maximum,
            Instant policyEnd
    ) {
        boolean valid = switch (request.type()) {
            case FIXED_SUBSCRIPTION_PRICE -> money != null && !money.isNegative()
                    && supportedCycle(request.billingCycle()) && noReferences(request, references)
                    && request.percentage() == null && maximum == null;
            case FIXED_DISCOUNT -> positive(money) && request.billingCycle() == null
                    && noReferences(request, references) && request.percentage() == null && maximum == null;
            case PERCENTAGE_DISCOUNT -> money == null && request.billingCycle() == null
                    && noReferences(request, references) && request.percentage() != null
                    && request.percentage().compareTo(BigDecimal.ZERO) > 0
                    && request.percentage().compareTo(new BigDecimal("100")) <= 0
                    && positive(maximum);
            case ALLOW_PRODUCT_SELECTION, BLOCK_PRODUCT_SELECTION -> references.hasProduct()
                    && references.feature() == null && noAdjustmentValues(request, money, maximum);
            case ADDITIVE_QUOTA_BONUS -> references.feature() != null
                    && !references.hasProduct() && request.quotaResource() != null
                    && request.quantityDelta() != null && request.quantityDelta() > 0
                    && noAdjustmentValues(request, money, maximum);
            case GRANT_ADD_ON -> request.productType() == CommercialPolicyProductType.ADD_ON
                    && references.addOn() != null && references.feature() == null
                    && policyEnd != null && noAdjustmentValues(request, money, maximum);
            case GRANT_QUOTA_PACKAGE -> request.productType() == CommercialPolicyProductType.QUOTA_PACKAGE
                    && references.quotaPackage() != null && references.feature() == null
                    && policyEnd != null && noAdjustmentValues(request, money, maximum);
            case BLOCK_FEATURE -> references.feature() != null && !references.hasProduct()
                    && request.quotaResource() == null && request.quantityDelta() == null
                    && noAdjustmentValues(request, money, maximum);
        };
        if (!valid) {
            throw new InvalidRequestException(
                    "Effect " + request.type() + " has fields outside its typed contract.");
        }
        if (request.type() == CommercialPolicyEffectType.ADDITIVE_QUOTA_BONUS
                && references.feature().getQuotaSchema().stream()
                        .noneMatch(slot -> slot.resource().equals(request.quotaResource()))) {
            throw new InvalidRequestException(
                    "Quota resource " + request.quotaResource() + " is not declared by Feature "
                            + references.feature().getCode() + ".");
        }
    }

    private boolean noReferences(
            CommercialPolicyRequests.Effect request,
            EffectReferences references
    ) {
        return !references.hasProduct() && references.feature() == null
                && request.quotaResource() == null && request.quantityDelta() == null;
    }

    private boolean noAdjustmentValues(
            CommercialPolicyRequests.Effect request,
            Money money,
            Money maximum
    ) {
        return money == null && maximum == null && request.billingCycle() == null
                && request.percentage() == null
                && (request.type() == CommercialPolicyEffectType.ADDITIVE_QUOTA_BONUS
                    || (request.quotaResource() == null && request.quantityDelta() == null));
    }

    private void copyConfiguration(CommercialPolicy source, CommercialPolicy target) {
        translateState(() -> target.configureTarget(
                source.getTargetKind(), source.getTargetAccount(), source.getExplicitAccounts(),
                source.getTargetPlan(), source.getSegmentReference()));
        List<CommercialPolicyEffect> effects = source.getEffects().stream()
                .sorted(Comparator.comparingInt(CommercialPolicyEffect::getEffectOrder))
                .map(effect -> CommercialPolicyEffect.create(
                        target, effect.getEffectOrder(), effect.getType(), effect.getProductType(),
                        effect.getPlan(), effect.getAddOn(), effect.getQuotaPackage(), effect.getFeature(),
                        effect.getQuotaResource(), effect.getQuantityDelta(), effect.money(),
                        effect.getBillingCycle(), effect.getPercentage(), effect.maximumMoney()))
                .toList();
        target.replaceEffects(effects);
    }

    private CommercialPolicyViews.ActivationPreview toActivationPreview(
            CommercialPolicy policy,
            CommercialPolicyActivationAssessor.Assessment assessment,
            long catalogRevision,
            String registryVersion,
            CommercialPreviewTokenService.IssuedEvidence evidence
    ) {
        List<CommercialPolicyViews.AudienceAccount> sample = audienceResolver.summaries(
                assessment.accountIds().stream().limit(10).toList());
        return new CommercialPolicyViews.ActivationPreview(
                policy.getId(), policy.getVersion(), catalogRevision, registryVersion,
                evidence.evaluatedAt(), evidence.expiresAt(), evidence.token(),
                assessment.activatable(), assessment.blockers(), assessment.accountIds().size(), sample,
                assessment.revisionToEnd() == null ? null : assessment.revisionToEnd().getId(),
                true, assessment.executionBlockers());
    }

    private CommercialPolicyViews.Activation toActivation(CommercialPolicyActivation activation) {
        return new CommercialPolicyViews.Activation(
                activation.getId(), activation.getActivationNumber(), activation.getPolicy().getId(),
                activation.getActorUserId(), activation.getEvaluatedAt(),
                activation.getEvidenceExpiresAt(), activation.getCatalogRevision(),
                activation.getRegistryVersion(), activation.getAffectedAccountCount(),
                activation.getReason(), activation.getCreatedAt());
    }

    private CommercialPolicyViews.Detail toDetail(CommercialPolicy policy) {
        int effectCount = policy.getEffects().size();
        int targetCount = configuredTargetCount(policy);
        Integer latestSnapshot = activationRepository.findTopByPolicy_IdOrderByActivationNumberDesc(policy.getId())
                .map(CommercialPolicyActivation::getAffectedAccountCount).orElse(null);
        int maximumRevision = policyRepository.findMaximumMaterialRevisionNumber(
                policy.getLineageId(), CommercialPolicyStatus.ARCHIVED);
        CommercialPolicyViews.Summary summary = toSummary(
                policy, effectCount, targetCount, latestSnapshot, maximumRevision, actionPermissions());
        return new CommercialPolicyViews.Detail(
                summary, policy.getDescription(), policy.getReason(), policy.getApprovalReference(),
                policy.getContractReference(), toTarget(policy),
                policy.getEffects().stream()
                        .sorted(Comparator.comparingInt(CommercialPolicyEffect::getEffectOrder))
                        .map(this::toEffect).toList(),
                policy.getSourcePolicy() == null ? null : policy.getSourcePolicy().getId());
    }

    private CommercialPolicyViews.Summary toSummary(
            CommercialPolicy policy,
            int effectCount,
            int configuredTargetCount,
            Integer latestSnapshotCount,
            int maximumRevision,
            ActionPermissions permissions
    ) {
        List<CommercialPolicyBlocker> blockers = summaryBlockers(policy, effectCount);
        List<CommercialPolicyAction> actions = availableActions(
                policy, maximumRevision == policy.getRevisionNumber(), permissions);
        return new CommercialPolicyViews.Summary(
                policy.getId(), policy.getCode(), policy.getName(), policy.getStatus(),
                policy.getTargetKind(), targetLabel(policy, configuredTargetCount), configuredTargetCount, effectCount,
                latestSnapshotCount, policy.getSource(), policy.getPriority(), policy.getEffectiveFrom(),
                policy.getEffectiveUntil(), policy.getLineageId(), policy.getRevisionNumber(),
                policy.getCreationReason(), policy.getVersion(), policy.getCreatedAt(), policy.getUpdatedAt(),
                actions, blockers, true, true, EXECUTION_BLOCKERS);
    }

    private CommercialPolicyViews.Target toTarget(CommercialPolicy policy) {
        return new CommercialPolicyViews.Target(
                policy.getTargetKind(),
                policy.getTargetAccount() == null ? null : policy.getTargetAccount().getId(),
                policy.getTargetAccount() == null ? null : policy.getTargetAccount().getName(),
                policy.getExplicitAccounts().stream().map(Account::getId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                policy.getTargetPlan() == null ? null : policy.getTargetPlan().getId(),
                policy.getTargetPlan() == null ? null : policy.getTargetPlan().getCode(),
                policy.getTargetPlan() == null ? null : policy.getTargetPlan().getName(),
                policy.getSegmentReference());
    }

    private CommercialPolicyViews.Effect toEffect(CommercialPolicyEffect effect) {
        return new CommercialPolicyViews.Effect(
                effect.getId(), effect.getEffectOrder(), effect.getType(), effect.getProductType(),
                effect.productId(), effect.productCode(),
                effect.getFeature() == null ? null : effect.getFeature().getCode(),
                effect.getQuotaResource(), effect.getQuantityDelta(),
                effect.getAmount(), effect.getCurrencyCode(), effect.getBillingCycle(),
                effect.getPercentage(), effect.getMaximumAmount(), effect.getMaximumCurrencyCode(),
                CommercialPolicyPrecedence.effectClass(effect.getType()), false);
    }

    private List<CommercialPolicyBlocker> summaryBlockers(
            CommercialPolicy policy,
            int effectCount
    ) {
        List<CommercialPolicyBlocker> blockers = new ArrayList<>();
        if (policy.getTargetKind() == CommercialPolicyTargetKind.SEGMENT) {
            blockers.add(CommercialPolicyBlocker.SEGMENT_RESOLUTION_UNAVAILABLE);
        }
        if (effectCount == 0) blockers.add(CommercialPolicyBlocker.NO_EFFECTS);
        if (policy.getEffectiveUntil() != null && !policy.getEffectiveUntil().isAfter(clock.instant())) {
            blockers.add(CommercialPolicyBlocker.EFFECTIVE_WINDOW_EXPIRED);
        }
        return List.copyOf(blockers);
    }

    private List<CommercialPolicyAction> availableActions(
            CommercialPolicy policy,
            boolean latestRevision,
            ActionPermissions permissions
    ) {
        EnumSet<CommercialPolicyAction> actions = EnumSet.of(
                CommercialPolicyAction.DUPLICATE,
                CommercialPolicyAction.COMPARE,
                CommercialPolicyAction.PREVIEW_AUDIENCE,
                CommercialPolicyAction.READ_HISTORY,
                CommercialPolicyAction.READ_REVISIONS,
                CommercialPolicyAction.READ_ACTIVATIONS,
                CommercialPolicyAction.READ_ACTIVATION_ACCOUNTS,
                CommercialPolicyAction.READ_OWNER);
        switch (policy.getStatus()) {
            case DRAFT -> {
                actions.add(CommercialPolicyAction.EDIT_DRAFT);
                actions.add(CommercialPolicyAction.PREVIEW_ACTIVATION);
                actions.add(CommercialPolicyAction.ACTIVATE);
                actions.add(CommercialPolicyAction.ARCHIVE);
                actions.add(CommercialPolicyAction.DELETE_DRAFT);
                actions.add(CommercialPolicyAction.REASSIGN_OWNER);
            }
            case ACTIVE -> {
                actions.add(CommercialPolicyAction.PAUSE);
                actions.add(CommercialPolicyAction.END);
                if (latestRevision) actions.add(CommercialPolicyAction.REVISE);
            }
            case PAUSED -> {
                actions.add(CommercialPolicyAction.PREVIEW_ACTIVATION);
                actions.add(CommercialPolicyAction.RESUME);
                actions.add(CommercialPolicyAction.END);
                if (latestRevision) actions.add(CommercialPolicyAction.REVISE);
            }
            case ENDED -> {
                actions.add(CommercialPolicyAction.ARCHIVE);
                if (latestRevision) actions.add(CommercialPolicyAction.REVISE);
            }
            case ARCHIVED -> { }
        }
        actions.removeIf(action -> !permissions.allows(permissionFor(action)));
        return List.copyOf(actions);
    }

    private String permissionFor(CommercialPolicyAction action) {
        return switch (action) {
            case EDIT_DRAFT -> "platform.commercial_policies.update_draft";
            case DUPLICATE -> "platform.commercial_policies.duplicate";
            case REVISE -> "platform.commercial_policies.revise";
            case COMPARE -> "platform.commercial_policies.compare";
            case PREVIEW_AUDIENCE -> "platform.commercial_policies.preview_audience";
            case PREVIEW_ACTIVATION -> "platform.commercial_policies.preview_activation";
            case ACTIVATE -> "platform.commercial_policies.activate";
            case PAUSE -> "platform.commercial_policies.pause";
            case RESUME -> "platform.commercial_policies.resume";
            case END -> "platform.commercial_policies.end";
            case ARCHIVE -> "platform.commercial_policies.archive";
            case DELETE_DRAFT -> "platform.commercial_policies.delete_draft";
            case READ_HISTORY -> "platform.commercial_policies.read_history";
            case READ_REVISIONS -> "platform.commercial_policies.read_revisions";
            case READ_ACTIVATIONS -> "platform.commercial_policies.read_activations";
            case READ_ACTIVATION_ACCOUNTS ->
                    "platform.commercial_policies.read_activation_accounts";
            case READ_OWNER -> "platform.commercial_policies.read_owner";
            case REASSIGN_OWNER -> "platform.commercial_policies.reassign_owner";
        };
    }

    private Set<String> changedFields(CommercialPolicy source, CommercialPolicy compared) {
        Set<String> fields = new LinkedHashSet<>();
        changed(fields, "NAME", source.getName(), compared.getName());
        changed(fields, "DESCRIPTION", source.getDescription(), compared.getDescription());
        changed(fields, "EFFECTIVE_WINDOW",
                java.util.Arrays.asList(source.getEffectiveFrom(), source.getEffectiveUntil()),
                java.util.Arrays.asList(compared.getEffectiveFrom(), compared.getEffectiveUntil()));
        changed(fields, "SOURCE", source.getSource(), compared.getSource());
        changed(fields, "PRIORITY", source.getPriority(), compared.getPriority());
        changed(fields, "REASON", source.getReason(), compared.getReason());
        changed(fields, "APPROVAL_REFERENCE", source.getApprovalReference(), compared.getApprovalReference());
        changed(fields, "CONTRACT_REFERENCE", source.getContractReference(), compared.getContractReference());
        changed(fields, "TARGET", targetSignature(source), targetSignature(compared));
        changed(fields, "EFFECTS", effectSignatures(source), effectSignatures(compared));
        changed(fields, "OWNER", source.getOwner().getId(), compared.getOwner().getId());
        return Set.copyOf(fields);
    }

    private boolean referencesSource(CommercialPolicy candidate, CommercialPolicy expectedSource) {
        return candidate.getSourcePolicy() != null
                && candidate.getSourcePolicy().getId().equals(expectedSource.getId());
    }

    private String targetSignature(CommercialPolicy policy) {
        return switch (policy.getTargetKind()) {
            case ACCOUNT -> "ACCOUNT:" + policy.getTargetAccount().getId();
            case ACCOUNT_SET -> "ACCOUNT_SET:" + policy.getExplicitAccounts().stream()
                    .map(Account::getId).sorted().map(UUID::toString).collect(Collectors.joining(","));
            case PLAN_REVISION_SUBSCRIBERS -> "PLAN:" + policy.getTargetPlan().getId();
            case SEGMENT -> "SEGMENT:" + policy.getSegmentReference();
        };
    }

    private List<String> effectSignatures(CommercialPolicy policy) {
        return policy.getEffects().stream()
                .sorted(Comparator.comparingInt(CommercialPolicyEffect::getEffectOrder))
                .map(effect -> String.join("|",
                        String.valueOf(effect.getEffectOrder()),
                        effect.getType().name(),
                        String.valueOf(effect.getProductType()),
                        String.valueOf(effect.productId()),
                        effect.getFeature() == null ? "null" : effect.getFeature().getCode(),
                        String.valueOf(effect.getQuotaResource()),
                        String.valueOf(effect.getQuantityDelta()),
                        effect.money() == null ? "null" : effect.money().toString(),
                        String.valueOf(effect.getBillingCycle()),
                        String.valueOf(effect.getPercentage()),
                        effect.maximumMoney() == null ? "null" : effect.maximumMoney().toString()))
                .toList();
    }

    private void changed(Set<String> fields, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) fields.add(name);
    }

    private int configuredTargetCount(CommercialPolicy policy) {
        return switch (policy.getTargetKind()) {
            case ACCOUNT -> 1;
            case ACCOUNT_SET -> policy.getExplicitAccounts().size();
            case PLAN_REVISION_SUBSCRIBERS, SEGMENT -> 0;
        };
    }

    private String targetLabel(CommercialPolicy policy, int configuredTargetCount) {
        return switch (policy.getTargetKind()) {
            case ACCOUNT -> policy.getTargetAccount() == null ? null : policy.getTargetAccount().getName();
            case ACCOUNT_SET -> configuredTargetCount + " selected Accounts";
            case PLAN_REVISION_SUBSCRIBERS -> policy.getTargetPlan() == null ? null
                    : policy.getTargetPlan().getName() + " · " + policy.getTargetPlan().getCode();
            case SEGMENT -> policy.getSegmentReference();
        };
    }

    private Map<UUID, Integer> groupedInteger(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream().collect(Collectors.toMap(
                row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));
    }

    private Map<UUID, Integer> maximumRevisions(List<CommercialPolicy> policies) {
        if (policies.isEmpty()) return Map.of();
        return policyRepository.findMaximumMaterialRevisionNumbers(policies.stream()
                        .map(CommercialPolicy::getLineageId).collect(Collectors.toSet()),
                        CommercialPolicyStatus.ARCHIVED).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));
    }

    private Pageable historyPage(Pageable pageable, String property) {
        Pageable bounded = boundedPage(pageable);
        return PageRequest.of(bounded.getPageNumber(), bounded.getPageSize(),
                Sort.by(Sort.Direction.DESC, property).and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private Pageable boundedPage(Pageable pageable) {
        if (pageable == null || pageable.getPageNumber() < 0
                || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new InvalidRequestException("Page must be non-negative and size must be between 1 and 100.");
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

    private CommercialPolicy requirePolicy(UUID id) {
        return policyRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialPolicy", "id", id));
    }

    private void requirePolicyExists(UUID id) {
        if (!policyRepository.existsById(id)) {
            throw new ResourceNotFoundException("CommercialPolicy", "id", id);
        }
    }

    private CommercialPolicy requirePolicyForUpdate(UUID id) {
        policyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialPolicy", "id", id));
        return policyRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialPolicy", "id", id));
    }

    private Account requireAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
    }

    private Plan requirePlan(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", id));
    }

    private AddOn requireAddOn(UUID id) {
        return addOnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", id));
    }

    private QuotaPackage requireQuotaPackage(UUID id) {
        return quotaPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuotaPackage", "id", id));
    }

    private AdminUser currentOwner() {
        UUID adminUserId = adminMutationAuthorizer.currentActorAdminUserId();
        return adminUserRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "id", adminUserId));
    }

    private String nextCode(String name) {
        return CommercialCodeGenerator.generate(name, "POLICY", policyRepository::existsByCode);
    }

    private void requireVersion(CommercialPolicy policy, long expected) {
        if (policy.getVersion() != expected) {
            throw new StaleResourceVersionException(
                    "Commercial policy changed since it was read. Reload it and retry.");
        }
    }

    private void validateTerms(Instant from, Instant until, int priority) {
        if (from == null) throw new InvalidRequestException("Policy effectiveFrom is required.");
        if (until != null && !until.isAfter(from)) {
            throw new InvalidRequestException("Policy effectiveUntil must be after effectiveFrom.");
        }
        if (priority < 0 || priority > 1000) {
            throw new InvalidRequestException("Policy priority must be between 0 and 1000.");
        }
    }

    private Money money(BigDecimal amount, String currency, String label) {
        if (amount == null && currency == null) return null;
        if (amount == null || currency == null) {
            throw new InvalidRequestException(label + " requires amount and currencyCode together.");
        }
        try {
            return Money.of(amount, currency);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(exception.getMessage());
        }
    }

    private boolean supportedCycle(com.hiveapp.platform.client.plan.domain.constant.BillingCycle cycle) {
        return cycle == com.hiveapp.platform.client.plan.domain.constant.BillingCycle.MONTHLY
                || cycle == com.hiveapp.platform.client.plan.domain.constant.BillingCycle.YEARLY;
    }

    private boolean positive(Money money) {
        return money != null && money.amount().signum() > 0;
    }

    private void requireOnly(boolean condition, String message) {
        if (!condition) throw new InvalidRequestException(message);
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

    private record EffectReferences(Plan plan, AddOn addOn, QuotaPackage quotaPackage, Feature feature) {
        boolean hasProduct() {
            return plan != null || addOn != null || quotaPackage != null;
        }
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
