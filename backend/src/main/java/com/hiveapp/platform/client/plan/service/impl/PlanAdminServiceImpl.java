package com.hiveapp.platform.client.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.AddOnCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductBlocker;
import com.hiveapp.platform.client.plan.domain.constant.PlanCodes;
import com.hiveapp.platform.client.plan.domain.constant.PlanCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceAudit;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageComparisonField;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CommercialOverviewDto;
import com.hiveapp.platform.client.plan.dto.AssignAddOnFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.DeletePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.PlanDeletionPreview;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberOwnerLookupDto;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.dto.UpdateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.UpdateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.client.plan.service.AddOnActivationAssessor;
import com.hiveapp.platform.client.plan.service.CommercialCodeGenerator;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.service.PlanActivationAssessor;
import com.hiveapp.platform.client.plan.service.QuotaPackageActivationAssessor;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.PlanFeatureDto;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import com.hiveapp.platform.client.plan.dto.PlanOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.AddOnOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.PlanChooserItemDto;
import com.hiveapp.platform.client.plan.dto.AddOnChooserItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageChooserItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageRevisionRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageRevisionResult;
import com.hiveapp.platform.client.plan.dto.QuotaPackageActivationPreviewDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageLifecycleRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageComparisonDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageHistoryEntryDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackagePriceDraftDto;
import com.hiveapp.platform.client.plan.dto.ProductActivationPriceDto;
import com.hiveapp.platform.client.plan.dto.PlanActivationPreviewDto;
import com.hiveapp.platform.client.plan.dto.AddOnActivationPreviewDto;
import com.hiveapp.platform.client.plan.service.CommercialProductOperationsService;
import com.hiveapp.platform.client.plan.service.CrossFeatureCommercialAuthorizer;
import com.hiveapp.platform.client.plan.service.PlanAdminReadModels;
import com.hiveapp.platform.client.plan.service.PlanAdminService;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.platform.registry.definition.CommercialAvailabilityFeature;
import com.hiveapp.platform.registry.definition.PlansFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.DraftSuccessorExistsException;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.OperationBlockedException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import com.hiveapp.shared.exception.StaleActivationPreviewException;
import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.money.Money;
import dev.karroumi.permissionizer.PermissionNode;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = PlansFeature.KEY, description = "Plan Catalogue Management", guard = PermissionNode.Guard.ON)
public class PlanAdminServiceImpl extends PlatformControlFeatureService implements PlanAdminService {

    private static final Pattern COMMERCIAL_CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final String QUOTA_AUDIT_RESOURCE_TYPE = "PLAN_ADMIN";

    private final PlanRepository planRepository;
    private final AdminMutationAuthorizer adminMutationAuthorizer;
    private final PlanAdminReadModels readModels;
    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionChangeOperationRepository subscriptionChangeOperationRepository;
    private final SubscriptionCheckoutRepository subscriptionCheckoutRepository;
    private final BillingConfigurationValidator billingConfigurationValidator;
    private final AddOnRepository addOnRepository;
    private final AddOnFeatureRepository addOnFeatureRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final CommercialProductOperationsService productOperationsService;
    private final ProductPriceRepository productPriceRepository;
    private final CrossFeatureCommercialAuthorizer crossFeatureCommercialAuthorizer;
    private final AuditLogRepository auditLogRepository;
    private final AuditTrail auditTrail;
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CommercialCatalogVersionService commercialCatalogVersionService;
    private final RegistryCatalogVersionService registryCatalogVersionService;
    private final CommercialPreviewTokenService previewTokenService;
    private final PlanActivationAssessor planActivationAssessor;
    private final AddOnActivationAssessor addOnActivationAssessor;
    private final QuotaPackageActivationAssessor quotaPackageActivationAssessor;

    @Override
    @PermissionNode(key = "overview", description = "View commercial operations overview")
    @Transactional(readOnly = true)
    public CommercialOverviewDto getCommercialOverview() {
        long activeSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
        long trialingSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.TRIALING);
        long pastDueSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.PAST_DUE);
        long suspendedSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.SUSPENDED);
        return new CommercialOverviewDto(
                planRepository.count(),
                planRepository.countByStatus(PlanStatus.DRAFT),
                planRepository.countByStatus(PlanStatus.ACTIVE),
                planRepository.countByStatus(PlanStatus.INACTIVE),
                planRepository.countByStatus(PlanStatus.ARCHIVED),
                activeSubscriptions + trialingSubscriptions + pastDueSubscriptions + suspendedSubscriptions,
                activeSubscriptions,
                trialingSubscriptions,
                pastDueSubscriptions,
                suspendedSubscriptions,
                subscriptionCheckoutRepository.countByStatus(SubscriptionCheckoutStatus.PENDING_CONFIRMATION),
                subscriptionChangeOperationRepository.countByStatus(SubscriptionChangeStatus.PENDING),
                subscriptionChangeOperationRepository.countByStatus(SubscriptionChangeStatus.NEEDS_ATTENTION));
    }

    @Override
    protected FeatureDefinition featureDefinition() {
        return PlansFeature.definition();
    }

    @Override
    @PermissionNode(key = "list", description = "List and filter operational Plan revisions")
    @Transactional(readOnly = true)
    public Page<PlanOperationalListItemDto> listPlans(
            String search, PlanStatus status, ProductSalesVisibility salesVisibility,
            PlanExtensionPolicy extensionPolicy, UUID lineageId, Pageable pageable) {
        return productOperationsService.listPlans(
                search, status, salesVisibility, extensionPolicy, lineageId, pageable);
    }

    @Override
    @PermissionNode(key = "read_plan_operations",
            description = "Read authoritative actions and blockers for one Plan revision")
    @Transactional(readOnly = true)
    public PlanOperationalListItemDto getPlanOperations(UUID planId) {
        return productOperationsService.getPlanOperations(planId);
    }

    @Override
    @PermissionNode(key = "choose_plans", description = "Choose an active Plan without full catalogue access")
    @Transactional(readOnly = true)
    public Page<PlanChooserItemDto> choosePlans(
            String search, ProductSalesVisibility salesVisibility, Pageable pageable) {
        return productOperationsService.choosePlans(search, salesVisibility, pageable);
    }

    @Override
    @PermissionNode(key = "resolve_plan_choices",
            description = "Resolve up to 100 persisted Plan chooser selections")
    @Transactional(readOnly = true)
    public List<PlanChooserItemDto> resolvePlanChoices(java.util.Collection<UUID> ids) {
        return productOperationsService.resolvePlanChoices(ids);
    }

    @Override
    @PermissionNode(key = "resolve_plan_choice_codes",
            description = "Resolve up to 100 persisted Plan chooser codes, including unavailable selections")
    @Transactional(readOnly = true)
    public List<PlanChooserItemDto> resolvePlanChoicesByCode(java.util.Collection<String> codes) {
        return productOperationsService.resolvePlanChoicesByCode(codes);
    }

    @Override
    @PermissionNode(key = "read_detail", description = "View plan detail and operational counts")
    @Transactional(readOnly = true)
    public PlanDetailDto getPlanDetail(UUID planId) {
        Plan plan = requirePlan(planId);
        List<PlanFeature> planFeatures = planFeatureRepository.findAllByPlanId(planId);
        long activeSubscribers = subscriptionRepository.countByPlan_IdAndStatus(planId, SubscriptionStatus.ACTIVE);
        long trialingSubscribers = subscriptionRepository.countByPlan_IdAndStatus(planId, SubscriptionStatus.TRIALING);
        long currentSubscribers = activeSubscribers + trialingSubscribers;
        long affectedSubscriptions = currentSubscribers
                + subscriptionRepository.countByPlan_IdAndStatus(planId, SubscriptionStatus.PAST_DUE)
                + subscriptionRepository.countByPlan_IdAndStatus(planId, SubscriptionStatus.SUSPENDED);
        long historicalSubscribers = subscriptionRepository.countByPlan_Id(planId);
        Money currentRecurringPrice = subscriptionRepository
                .findAllByPlan_IdAndStatusInOrderByCreatedAtDesc(planId, usableStatuses())
                .stream()
                .map(subscription -> subscription.currentMoney() != null
                        ? subscription.currentMoney()
                        : Money.zero(plan.getCurrencyCode()))
                .reduce(Money.zero(plan.getCurrencyCode()), Money::add);

        return new PlanDetailDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getCurrencyCode(),
                plan.getBillingCycle(),
                plan.getStatus(),
                plan.getLineageId(),
                plan.getRevisionNumber(),
                plan.getSourcePlan() != null ? plan.getSourcePlan().getId() : null,
                plan.getCreationReason(),
                planFeatures.size(),
                (int) planFeatures.stream()
                        .filter(feature -> feature.getQuotaConfigs() != null && !feature.getQuotaConfigs().isEmpty())
                        .count(),
                activeSubscribers,
                trialingSubscribers,
                currentSubscribers,
                affectedSubscriptions,
                historicalSubscribers,
                currentRecurringPrice.amount(),
                currentRecurringPrice.currencyCode(),
                warnings(plan, planFeatures, affectedSubscriptions, historicalSubscribers),
                plan.getExtensionPolicy(), plan.getSalesVisibility(), plan.getVersion()
        );
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "create",
            description = "Create a new Plan draft and its reviewable initial price draft")
    public PlanDto createPlan(CreatePlanRequest request) {
        requirePriceBookPermission(
                "create", "Creating a Plan and its initial reviewable price draft");
        String code = CommercialCodeGenerator.generate(
                request.name(), "PLAN", candidate -> planRepository.findByCode(candidate).isPresent());
        Money price = validatePlanBasics(
                request.name(), request.price(), request.currencyCode(), request.billingCycle());
        List<AssignPlanFeatureRequest> features =
                request.features() == null ? List.of() : request.features();
        // Composing at creation is the assign-feature operation in bulk; holding plans.create
        // alone must not smuggle it past the dedicated endpoint's permission node.
        if (!features.isEmpty()
                && !adminMutationAuthorizer.currentActorGrantCeiling().allows("platform.plans.assign_feature")) {
            throw new ForbiddenException(
                    "Composing a plan at creation requires the assign-feature permission.");
        }
        var extensionPolicy = request.extensionPolicy() == null
                ? com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy.OPEN_COMPATIBLE
                : request.extensionPolicy();
        var salesVisibility = request.salesVisibility() == null
                ? com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC
                : request.salesVisibility();
        if (extensionPolicy
                != com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy.OPEN_COMPATIBLE
                || salesVisibility
                != com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC) {
            requireCommercialAvailabilityPermission(
                    "update_plan_policy", "set a non-default Plan availability policy at creation");
        }
        Plan plan = saveNewPlan(code, request.name(), request.description(), price, request.billingCycle(),
                null, UUID.randomUUID(), 1, PlanCreationReason.CREATED);
        plan.setExtensionPolicy(extensionPolicy);
        plan.setSalesVisibility(salesVisibility);
        // Same transaction as the plan itself: one invalid feature rolls everything back, so no
        // partial draft can survive that differs from what the operator reviewed.
        for (AssignPlanFeatureRequest featureRequest : features) {
            addFeature(plan, featureRequest);
        }
        createInitialPriceDraft(plan);
        return readModels.toDto(plan);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "duplicate",
            description = "Duplicate Plan configuration and active price schedules into reviewable drafts")
    public PlanDto duplicatePlan(UUID sourcePlanId, long expectedVersion, PlanBranchRequest request) {
        requirePriceBookPermission(
                "create", "Duplicating a Plan and its reviewable price schedules");
        Plan hint = planRepository.findById(sourcePlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", sourcePlanId));
        List<Plan> lineage = planRepository.findLineageForUpdate(hint.getLineageId());
        Plan source = lineage.stream()
                .filter(candidate -> candidate.getId().equals(sourcePlanId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", sourcePlanId));
        requireVersion(source, expectedVersion);
        requireCopiedPlanAvailabilityPermission(source, "duplicate");
        Plan duplicate = createBranch(source, request, UUID.randomUUID(), 1, PlanCreationReason.DUPLICATED);
        copyPlanComposition(source, duplicate);
        copySaleRelevantPrices(source, duplicate);
        return readModels.toDto(duplicate);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "revise",
            description = "Create a Plan successor with reviewable copies of active price schedules")
    public PlanDto revisePlan(UUID sourcePlanId, long expectedVersion, PlanBranchRequest request) {
        requirePriceBookPermission(
                "create", "Revising a Plan and copying its reviewable price schedules");
        Plan hint = planRepository.findById(sourcePlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", sourcePlanId));
        List<Plan> lineage = planRepository.findLineageForUpdate(hint.getLineageId());
        Plan source = lineage.stream()
                .filter(candidate -> candidate.getId().equals(sourcePlanId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", sourcePlanId));
        requireVersion(source, expectedVersion);
        requireCopiedPlanAvailabilityPermission(source, "revise");
        if (source.getStatus() == PlanStatus.DRAFT) {
            throw new BusinessException("A draft can be edited directly and cannot be revised.");
        }
        if (source.getStatus() == PlanStatus.ARCHIVED) {
            throw new InvalidStateException("Archived Plans are terminal and cannot be revised.");
        }
        int maximumRevision = lineage.stream().mapToInt(Plan::getRevisionNumber).max().orElse(0);
        lineage.stream()
                .filter(candidate -> candidate.getStatus() == PlanStatus.DRAFT)
                .findFirst()
                .ifPresent(draft -> {
                    throw new DraftSuccessorExistsException(
                            "Plan revision R" + draft.getRevisionNumber()
                                    + " is already an editable draft.");
                });
        if (source.getRevisionNumber() != maximumRevision) {
            throw new InvalidStateException("Only the latest Plan revision can be revised.");
        }
        Plan revision;
        try {
            revision = createBranch(
                    source, request, source.getLineageId(), maximumRevision + 1,
                    PlanCreationReason.REVISED);
        } catch (DuplicateResourceException exception) {
            throw new DraftSuccessorExistsException(
                    "A competing Plan draft successor already exists; reload the lineage.");
        }
        copyPlanComposition(source, revision);
        copySaleRelevantPrices(source, revision);
        return readModels.toDto(revision);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update", description = "Update plan template basics")
    public PlanDto updatePlan(UUID planId, UpdatePlanRequest request) {
        Plan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        requireVersion(plan, request.expectedVersion());
        requireMutable(plan);
        Money price = validatePlanBasics(
                request.name(), request.price(), request.currencyCode(), request.billingCycle());
        if (!plan.getCurrencyCode().equals(price.currencyCode())
                && subscriptionRepository.countByPlan_Id(planId) > 0) {
            throw new InvalidRequestException(
                    "Plan currency cannot change after subscription history exists.");
        }
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setMoney(price);
        plan.setBillingCycle(request.billingCycle());
        return readModels.toDto(planRepository.saveAndFlush(plan));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "transition_status", description = "Transition a plan lifecycle state")
    public PlanDto transitionStatus(
            UUID planId,
            PlanStatus targetStatus,
            long expectedVersion,
            String reason,
            String activationPreviewToken
    ) {
        String registryVersion = targetStatus == PlanStatus.ACTIVE
                ? lockRegistryVersion() : null;
        var plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        requireVersion(plan, expectedVersion);
        if (targetStatus == null) {
            throw new InvalidRequestException("Target Plan status is required.");
        }
        if (plan.getStatus() == targetStatus) {
            return readModels.toDto(plan);
        }
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new BusinessException("Archived plans are terminal and cannot transition.");
        }
        if (targetStatus == PlanStatus.DRAFT) {
            throw new BusinessException("A Plan cannot transition back to DRAFT.");
        }
        if (targetStatus == PlanStatus.ARCHIVED) {
            requireReason(reason, "Plan archival");
        }
        if (plan.getStatus() == PlanStatus.DRAFT && targetStatus == PlanStatus.INACTIVE) {
            throw new BusinessException("A draft Plan must be activated or archived.");
        }
        if (PlanCodes.DEFAULT.equals(plan.getCode()) && targetStatus != PlanStatus.ACTIVE) {
            throw new BusinessException("The default FREE plan must remain ACTIVE because workspace provisioning requires it.");
        }
        if (targetStatus == PlanStatus.ACTIVE) {
            requireActivationToken(activationPreviewToken, "Plan");
            long catalogRevision = commercialCatalogVersionService.currentRevision();
            UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
            var assessment = planActivationAssessor.assess(plan, clock.instant());
            previewTokenService.requireValid(
                    activationPreviewToken,
                    CommercialPreviewKind.PLAN_ACTIVATION,
                    plan.getId(),
                    plan.getVersion(),
                    actorUserId,
                    catalogRevision,
                    registryVersion,
                    assessment.fingerprint());
            if (!assessment.blockers().isEmpty()) {
                throw new OperationBlockedException(
                        "The Plan cannot be activated.",
                        assessment.blockers().stream().map(Enum::name).toList());
            }
        }
        plan.setStatus(targetStatus);
        Plan saved = planRepository.saveAndFlush(plan);
        return readModels.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_plan_activation",
            description = "Preview exact Plan activation blockers and commercial impact")
    public PlanActivationPreviewDto previewPlanActivation(UUID planId) {
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        String registryVersion = registryCatalogVersionService.currentVersion();
        return commercialCatalogVersionService.readConsistently(catalogRevision -> {
            Plan plan = requirePlan(planId);
            Instant evaluatedAt = clock.instant();
            var assessment = planActivationAssessor.assess(plan, evaluatedAt);
            var evidence = previewTokenService.issue(
                    CommercialPreviewKind.PLAN_ACTIVATION,
                    plan.getId(),
                    plan.getVersion(),
                    actorUserId,
                    catalogRevision,
                    registryVersion,
                    assessment.fingerprint(),
                    evaluatedAt);
            return new PlanActivationPreviewDto(
                    plan.getId(), plan.getVersion(), catalogRevision, registryVersion,
                    evidence.evaluatedAt(), evidence.expiresAt(), evidence.token(),
                    assessment.blockers().isEmpty(), assessment.blockers(),
                    assessment.includedFeatureCount(), assessment.optionalAddOnFeatureCount(),
                    assessment.blockedFeatureCount(), assessment.reviewedPrices());
        }, StaleActivationPreviewException::new);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_delete", description = "Preview plan deletion impact and blockers")
    public PlanDeletionPreview previewPlanDeletion(UUID planId) {
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        String registryVersion = registryCatalogVersionService.currentVersion();
        return commercialCatalogVersionService.readConsistently(catalogRevision -> {
            Plan plan = requirePlan(planId);
            PlanDeletionAssessment assessment = assessDeletion(
                    plan, productPriceRepository.findAllByPlanId(planId));
            Instant evaluatedAt = clock.instant();
            var evidence = previewTokenService.issue(
                    CommercialPreviewKind.PLAN_DELETION,
                    plan.getId(), plan.getVersion(), actorUserId, catalogRevision,
                    registryVersion, assessment.fingerprint(), evaluatedAt);
            return toDeletionPreview(
                    plan, assessment, catalogRevision, registryVersion, evidence);
        });
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "delete",
            description = "Delete a confirmed unused Plan draft and its unpublished price drafts")
    public void deletePlan(UUID planId, DeletePlanRequest request) {
        requirePriceBookPermission(
                "delete_draft", "Deleting a Plan and its owned price drafts");
        String registryVersion = lockRegistryVersion();
        Plan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        List<ProductPrice> prices = productPriceRepository.findAllByPlanIdForUpdate(planId);
        long catalogRevision = commercialCatalogVersionService.currentRevision();
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        PlanDeletionAssessment assessment = assessDeletion(plan, prices);
        if (request.expectedVersion() != plan.getVersion()) throw staleDeletionPreview();
        previewTokenService.requireValid(
                request.previewToken(), CommercialPreviewKind.PLAN_DELETION,
                plan.getId(), plan.getVersion(), actorUserId, catalogRevision,
                registryVersion, assessment.fingerprint(), this::staleDeletionPreview);
        if (!plan.getName().equals(request.confirmationName())) {
            throw new InvalidRequestException("Plan name confirmation does not match.");
        }
        if (!assessment.blockers().isEmpty()) {
            throw new OperationBlockedException(
                    "The Plan draft cannot be deleted.", assessment.blockers());
        }
        planFeatureRepository.deleteAll(planFeatureRepository.findAllByPlanId(planId));
        if (!prices.isEmpty()) {
            productPriceRepository.deleteAllInBatch(prices);
            productPriceRepository.flush();
        }
        try {
            planRepository.delete(plan);
            planRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw staleDeletionPreview();
        }
    }

    @Override
    @PermissionNode(key = "list_features", description = "List features assigned to a plan")
    @Transactional(readOnly = true)
    public List<PlanFeatureDto> listPlanFeatures(UUID planId) {
        if (!planRepository.existsById(planId)) {
            throw new ResourceNotFoundException("Plan", "id", planId);
        }
        return planFeatureRepository.findAllByPlanId(planId).stream().map(readModels::toDto).toList();
    }

    @Override
    @PermissionNode(key = "list_subscribers", description = "List accounts currently subscribed to a plan")
    @Transactional(readOnly = true)
    public Page<PlanSubscriberDto> listPlanSubscribers(
            UUID planId,
            String search,
            SubscriptionStatus status,
            Pageable pageable
    ) {
        Plan plan = requirePlan(planId);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return subscriptionRepository.searchPlanSubscribers(
                        planId, status, normalizedSearch, safeSubscriberPage(pageable))
                .map(subscription -> toSubscriberDto(plan, subscription));
    }

    @Override
    @PermissionNode(
            key = "lookup_subscriber_owner_email",
            description = "Find plan subscribers by Account owner email")
    @Transactional(readOnly = true)
    public Page<PlanSubscriberOwnerLookupDto> findPlanSubscribersByOwnerEmail(
            UUID planId,
            String ownerEmail,
            Pageable pageable
    ) {
        Plan plan = requirePlan(planId);
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new InvalidRequestException("Owner email is required.");
        }
        return subscriptionRepository.findPlanSubscribersByOwnerEmail(
                        planId, ownerEmail.trim(), safeSubscriberPage(pageable))
                .map(subscription -> new PlanSubscriberOwnerLookupDto(
                        subscription.getAccount().getOwner().getEmail(),
                        toSubscriberDto(plan, subscription)));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "assign_feature", description = "Assign a feature to a plan")
    public PlanFeatureDto assignFeature(
            UUID planId, long expectedVersion, AssignPlanFeatureRequest request) {
        var plan = planRepository.findByIdForCompositionUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        requireVersion(plan, expectedVersion);
        requireMutable(plan);
        PlanFeatureDto result = readModels.toDto(addFeature(plan, request));
        advanceCompositionVersion(plan, expectedVersion);
        return result;
    }

    /** The single path that turns a feature request into a PlanFeature row, for any caller. */
    private PlanFeature addFeature(Plan plan, AssignPlanFeatureRequest request) {
        var quotaEntries = request.quotaEntries();
        var feature = billingConfigurationValidator.validatePlanFeature(
                request.featureCode(), request.mode(), quotaEntries, plan.getCurrencyCode());

        planFeatureRepository.findByPlanIdAndFeature_Code(plan.getId(), request.featureCode())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("PlanFeature", "featureCode", request.featureCode());
                });

        PlanFeature pf = new PlanFeature();
        pf.setPlan(plan);
        pf.setFeature(feature);
        pf.setMode(request.mode());
        pf.setQuotaConfigs(new ArrayList<>(quotaEntries));
        try {
            return planFeatureRepository.saveAndFlush(pf);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("PlanFeature", "featureCode", request.featureCode());
        }
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_feature", description = "Update a plan's feature quota/price config")
    public PlanFeatureDto updateFeature(
            UUID planId, UUID planFeatureId, long expectedVersion,
            AssignPlanFeatureRequest request) {
        Plan plan = planRepository.findByIdForCompositionUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        requireVersion(plan, expectedVersion);
        requireMutable(plan);
        var pf = planFeatureRepository.findById(planFeatureId)
                .orElseThrow(() -> new ResourceNotFoundException("PlanFeature", "id", planFeatureId));
        if (!pf.getPlan().getId().equals(planId)) {
            throw new ResourceNotFoundException("PlanFeature", "id", planFeatureId);
        }
        if (!pf.getFeature().getCode().equals(request.featureCode())) {
            throw new InvalidRequestException("A plan feature update cannot change its feature code.");
        }
        var quotaEntries = request.quotaEntries();
        billingConfigurationValidator.validatePlanFeature(
                request.featureCode(), request.mode(), quotaEntries, plan.getCurrencyCode());
        pf.setMode(request.mode());
        pf.setQuotaConfigs(new ArrayList<>(quotaEntries));
        PlanFeatureDto result = readModels.toDto(planFeatureRepository.saveAndFlush(pf));
        advanceCompositionVersion(plan, expectedVersion);
        return result;
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "remove_feature", description = "Remove a feature from a plan")
    public void removeFeature(UUID planId, UUID planFeatureId, long expectedVersion) {
        Plan plan = planRepository.findByIdForCompositionUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        requireVersion(plan, expectedVersion);
        requireMutable(plan);
        var pf = planFeatureRepository.findById(planFeatureId)
                .orElseThrow(() -> new ResourceNotFoundException("PlanFeature", "id", planFeatureId));
        if (!pf.getPlan().getId().equals(planId)) {
            throw new ResourceNotFoundException("PlanFeature", "id", planFeatureId);
        }
        planFeatureRepository.delete(pf);
        planFeatureRepository.flush();
        advanceCompositionVersion(plan, expectedVersion);
    }

    private void advanceCompositionVersion(Plan plan, long expectedVersion) {
        if (planRepository.advanceCompositionVersion(plan.getId(), expectedVersion) != 1) {
            throw new StaleResourceVersionException(
                    "The Plan composition changed concurrently. Reload it and retry.");
        }
    }

    @Override
    @PermissionNode(key = "list_add_ons", description = "List and filter operational AddOn revisions")
    @Transactional(readOnly = true)
    public Page<AddOnOperationalListItemDto> listAddOns(
            String search, AddOnStatus status, ProductSalesVisibility salesVisibility,
            UUID lineageId, String featureCode, String targetPlanCode, Pageable pageable) {
        return productOperationsService.listAddOns(
                search, status, salesVisibility, lineageId, featureCode, targetPlanCode, pageable);
    }

    @Override
    @PermissionNode(key = "read_add_on_operations",
            description = "Read authoritative actions and blockers for one AddOn revision")
    @Transactional(readOnly = true)
    public AddOnOperationalListItemDto getAddOnOperations(UUID addOnId) {
        return productOperationsService.getAddOnOperations(addOnId);
    }

    @Override
    @PermissionNode(key = "choose_add_ons", description = "Choose an active AddOn without full catalogue access")
    @Transactional(readOnly = true)
    public Page<AddOnChooserItemDto> chooseAddOns(
            String search, ProductSalesVisibility salesVisibility, String featureCode, Pageable pageable) {
        return productOperationsService.chooseAddOns(search, salesVisibility, featureCode, pageable);
    }

    @Override
    @PermissionNode(key = "resolve_add_on_choices",
            description = "Resolve up to 100 persisted AddOn chooser selections")
    @Transactional(readOnly = true)
    public List<AddOnChooserItemDto> resolveAddOnChoices(java.util.Collection<UUID> ids) {
        return productOperationsService.resolveAddOnChoices(ids);
    }

    @Override
    @PermissionNode(key = "resolve_add_on_choice_codes",
            description = "Resolve up to 100 persisted AddOn chooser codes, including unavailable selections")
    @Transactional(readOnly = true)
    public List<AddOnChooserItemDto> resolveAddOnChoicesByCode(java.util.Collection<String> codes) {
        return productOperationsService.resolveAddOnChoicesByCode(codes);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_add_on", description = "Read commercial AddOn detail")
    public AddOnDto getAddOn(UUID addOnId) {
        return readModels.toDto(addOnRepository.findDetailedById(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId)));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "create_add_on",
            description = "Create an AddOn draft and its reviewable initial price draft")
    public AddOnDto createAddOn(CreateAddOnRequest request) {
        requirePriceBookPermission(
                "create", "Creating an AddOn and its initial reviewable price draft");
        String code = CommercialCodeGenerator.generate(
                request.name(), "ADD_ON", candidate -> addOnRepository.findByCode(candidate).isPresent());
        AddOn addOn = new AddOn();
        addOn.setCode(code);
        var salesVisibility = request.salesVisibility() == null
                ? com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC
                : request.salesVisibility();
        if (salesVisibility
                != com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC) {
            requireCommercialAvailabilityPermission(
                    "update_add_on_visibility", "set direct-only AddOn visibility at creation");
        }
        addOn.setSalesVisibility(salesVisibility);
        applyAddOnBasics(addOn, request.name(), request.description(), request.price(),
                request.currencyCode(), request.billingCycle(), request.allowedPlanCodes(),
                request.blockedPlanCodes(), request.dependencyCodes(), request.exclusionCodes());
        addOn.setStatus(AddOnStatus.DRAFT);
        AddOn saved = addOnRepository.saveAndFlush(addOn);
        createInitialPriceDraft(saved);
        return readModels.toDto(saved);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "revise_add_on",
            description = "Create an AddOn successor with reviewable copies of active price schedules")
    public AddOnDto reviseAddOn(UUID sourceAddOnId, long expectedVersion) {
        requirePriceBookPermission(
                "create", "Revising an AddOn and copying its reviewable price schedules");
        AddOn hint = addOnRepository.findById(sourceAddOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", sourceAddOnId));
        List<AddOn> lineage = addOnRepository.findLineageForUpdate(hint.getLineageId());
        AddOn source = lineage.stream()
                .filter(candidate -> candidate.getId().equals(sourceAddOnId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", sourceAddOnId));
        requireVersion(source, expectedVersion);
        if (source.getSalesVisibility() != ProductSalesVisibility.PUBLIC) {
            requireCommercialAvailabilityPermission(
                    "update_add_on_visibility", "copy direct-only AddOn visibility while revising");
        }
        if (source.getStatus() == AddOnStatus.DRAFT) {
            throw new BusinessException("An AddOn draft can be edited directly and cannot be revised.");
        }
        if (source.getStatus() == AddOnStatus.ARCHIVED) {
            throw new BusinessException("Archived AddOns are terminal and cannot be revised.");
        }

        int maximumRevision = lineage.stream().mapToInt(AddOn::getRevisionNumber).max().orElse(0);
        lineage.stream()
                .filter(candidate -> candidate.getStatus() == AddOnStatus.DRAFT)
                .findFirst()
                .ifPresent(draft -> {
                    throw new DraftSuccessorExistsException(
                            "AddOn revision R" + draft.getRevisionNumber() + " is already an editable draft.");
                });
        if (source.getRevisionNumber() != maximumRevision) {
            throw new InvalidStateException("Only the latest AddOn revision can be revised.");
        }

        String code = CommercialCodeGenerator.generate(
                source.getName(), "ADD_ON", candidate -> addOnRepository.findByCode(candidate).isPresent());
        AddOn revision = new AddOn();
        revision.setCode(code);
        applyAddOnBasics(revision, source.getName(), source.getDescription(), source.getPrice(),
                source.getCurrencyCode(), source.getBillingCycle(), source.getAllowedPlanCodes(),
                source.getBlockedPlanCodes(), source.getDependencyCodes(), source.getExclusionCodes());
        revision.setStatus(AddOnStatus.DRAFT);
        revision.setLineageId(source.getLineageId());
        revision.setRevisionNumber(maximumRevision + 1);
        revision.setSourceAddOn(source);
        revision.setCreationReason(AddOnCreationReason.REVISED);
        revision.setSalesVisibility(source.getSalesVisibility());

        AddOn savedRevision;
        try {
            savedRevision = addOnRepository.saveAndFlush(revision);
        } catch (DataIntegrityViolationException exception) {
            throw new DraftSuccessorExistsException(
                    "A competing AddOn draft successor already exists; reload the lineage.");
        }

        List<AddOnFeature> copiedFeatures = addOnFeatureRepository.findAllByAddOnId(source.getId()).stream()
                .map(sourceFeature -> {
                    billingConfigurationValidator.validateAddOnFeature(
                            sourceFeature.getFeature().getCode(), sourceFeature.getQuotaConfigs(),
                            savedRevision.getCurrencyCode());
                    AddOnFeature copy = new AddOnFeature();
                    copy.setAddOn(savedRevision);
                    copy.setFeature(sourceFeature.getFeature());
                    copy.setQuotaConfigs(sourceFeature.getQuotaConfigs() == null
                            ? new ArrayList<>()
                            : new ArrayList<>(sourceFeature.getQuotaConfigs()));
                    return copy;
                })
                .toList();
        if (!copiedFeatures.isEmpty()) {
            addOnFeatureRepository.saveAll(copiedFeatures);
        }
        savedRevision.getFeatures().addAll(copiedFeatures);
        copySaleRelevantPrices(source, savedRevision);
        return readModels.toDto(savedRevision);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_add_on", description = "Update a commercial AddOn draft")
    public AddOnDto updateAddOn(UUID addOnId, UpdateAddOnRequest request) {
        AddOn addOn = addOnRepository.findByIdForUpdate(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
        requireVersion(addOn, request.expectedVersion());
        requireEditable(addOn);
        applyAddOnBasics(addOn, request.name(), request.description(), request.price(),
                request.currencyCode(), request.billingCycle(), request.allowedPlanCodes(),
                request.blockedPlanCodes(), request.dependencyCodes(), request.exclusionCodes());
        addOn.touchDefinition();
        addOnRepository.saveAndFlush(addOn);
        return readModels.toDto(addOnRepository.findDetailedById(addOnId).orElseThrow());
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "transition_add_on", description = "Transition a commercial AddOn lifecycle state")
    public AddOnDto transitionAddOnStatus(
            UUID addOnId,
            AddOnStatus targetStatus,
            long expectedVersion,
            String reason,
            String activationPreviewToken
    ) {
        if (targetStatus == null) {
            throw new InvalidRequestException("Target AddOn status is required.");
        }
        String registryVersion = targetStatus == AddOnStatus.ACTIVE
                ? lockRegistryVersion() : null;
        AddOn addOn;
        List<AddOn> lockedLineage = List.of();
        if (targetStatus == AddOnStatus.ACTIVE) {
            AddOn hint = addOnRepository.findById(addOnId)
                    .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
            lockedLineage = addOnRepository.findLineageForUpdate(hint.getLineageId());
            addOn = lockedLineage.stream()
                    .filter(candidate -> candidate.getId().equals(addOnId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
        } else {
            addOn = addOnRepository.findByIdForUpdate(addOnId)
                    .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
        }
        requireVersion(addOn, expectedVersion);
        if (addOn.getStatus() == targetStatus) {
            return readModels.toDto(addOn);
        }
        if (addOn.getStatus() == AddOnStatus.ARCHIVED) {
            throw new BusinessException("Archived AddOns are terminal and cannot transition.");
        }
        if (targetStatus == AddOnStatus.DRAFT) {
            throw new BusinessException("An AddOn cannot transition back to DRAFT.");
        }
        if (targetStatus == AddOnStatus.ARCHIVED) {
            requireReason(reason, "AddOn archival");
        }
        if (addOn.getStatus() == AddOnStatus.DRAFT
                && targetStatus != AddOnStatus.ACTIVE
                && targetStatus != AddOnStatus.ARCHIVED) {
            throw new BusinessException(
                    "An AddOn draft may only be published, archived, or deleted.");
        }
        if (targetStatus == AddOnStatus.ACTIVE) {
            requireActivationToken(activationPreviewToken, "AddOn");
            long catalogRevision = commercialCatalogVersionService.currentRevision();
            UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
            var assessment = addOnActivationAssessor.assess(
                    addOn, lockedLineage, clock.instant());
            previewTokenService.requireValid(
                    activationPreviewToken,
                    CommercialPreviewKind.ADD_ON_ACTIVATION,
                    addOn.getId(),
                    addOn.getRowVersion(),
                    actorUserId,
                    catalogRevision,
                    registryVersion,
                    assessment.fingerprint());
            if (!assessment.blockers().isEmpty()) {
                throw new OperationBlockedException(
                        "The AddOn cannot be activated.",
                        assessment.blockers().stream().map(Enum::name).toList());
            }
            Set<UUID> toDeactivate = Set.copyOf(assessment.addOnsToDeactivate());
            List<AddOn> previouslyActive = lockedLineage.stream()
                    .filter(candidate -> toDeactivate.contains(candidate.getId()))
                    .toList();
            previouslyActive.forEach(candidate -> candidate.setStatus(AddOnStatus.INACTIVE));
            if (!previouslyActive.isEmpty()) {
                addOnRepository.saveAllAndFlush(previouslyActive);
            }
        }
        addOn.setStatus(targetStatus);
        addOnRepository.saveAndFlush(addOn);
        return readModels.toDto(addOnRepository.findDetailedById(addOnId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_add_on_activation",
            description = "Preview exact AddOn activation blockers and compatible Plan impact")
    public AddOnActivationPreviewDto previewAddOnActivation(UUID addOnId) {
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        String registryVersion = registryCatalogVersionService.currentVersion();
        return commercialCatalogVersionService.readConsistently(catalogRevision -> {
            AddOn addOn = requireAddOn(addOnId);
            List<AddOn> lineage = addOnRepository.findAllByLineageId(addOn.getLineageId());
            Instant evaluatedAt = clock.instant();
            var assessment = addOnActivationAssessor.assess(addOn, lineage, evaluatedAt);
            var evidence = previewTokenService.issue(
                    CommercialPreviewKind.ADD_ON_ACTIVATION,
                    addOn.getId(),
                    addOn.getRowVersion(),
                    actorUserId,
                    catalogRevision,
                    registryVersion,
                    assessment.fingerprint(),
                    evaluatedAt);
            return new AddOnActivationPreviewDto(
                    addOn.getId(), addOn.getRowVersion(), catalogRevision, registryVersion,
                    evidence.evaluatedAt(), evidence.expiresAt(), evidence.token(),
                    assessment.blockers().isEmpty(), assessment.blockers(),
                    assessment.featureCount(), assessment.evaluatedPlanCount(),
                    assessment.compatiblePlanCount(), addOn.getAllowedPlanCodes().size(),
                    assessment.reviewedPrices(),
                    assessment.addOnsToDeactivate());
        }, StaleActivationPreviewException::new);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "delete_add_on",
            description = "Delete an unused AddOn draft and its unpublished price drafts")
    public void deleteAddOn(UUID addOnId, long expectedVersion) {
        requirePriceBookPermission(
                "delete_draft", "Deleting an AddOn and its owned price drafts");
        AddOn addOn = addOnRepository.findByIdForUpdate(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
        requireVersion(addOn, expectedVersion);
        if (addOn.getStatus() != AddOnStatus.DRAFT) {
            throw new BusinessException("Only AddOn drafts may be deleted; deactivate or archive published AddOns.");
        }
        List<ProductPrice> prices = productPriceRepository.findAllByAddOnIdForUpdate(addOnId);
        String codePattern = "%\"" + escapeLike(addOn.getCode()) + "\"%";
        boolean referenced = addOnRepository.count((root, query, cb) -> cb.and(
                cb.notEqual(root.get("id"), addOnId),
                cb.or(
                        cb.like(root.get("dependencyCodes").as(String.class), codePattern, '\\'),
                        cb.like(root.get("exclusionCodes").as(String.class), codePattern, '\\')))) > 0;
        boolean referencedByQuotaPackage = quotaPackageRepository.count((root, query, cb) ->
                cb.like(root.get("allowedAddOnCodes").as(String.class), codePattern, '\\')) > 0;
        List<String> blockers = new ArrayList<>();
        if (prices.stream().anyMatch(price -> price.getStatus() != ProductPriceStatus.DRAFT)) {
            blockers.add(CommercialProductBlocker.PUBLISHED_PRICE_HISTORY.name());
        }
        if (referenced) {
            blockers.add(CommercialProductBlocker.REFERENCED_BY_ADD_ON.name());
        }
        if (referencedByQuotaPackage) {
            blockers.add(CommercialProductBlocker.REFERENCED_BY_QUOTA_PACKAGE.name());
        }
        if (!blockers.isEmpty()) {
            throw new OperationBlockedException(
                    "The AddOn draft cannot be deleted while durable commercial references exist.",
                    blockers);
        }
        addOnFeatureRepository.deleteAll(addOnFeatureRepository.findAllByAddOnId(addOnId));
        if (!prices.isEmpty()) {
            productPriceRepository.deleteAllInBatch(prices);
            productPriceRepository.flush();
        }
        addOnRepository.delete(addOn);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "assign_add_on_feature", description = "Assign a feature to an AddOn draft")
    public AddOnDto.FeatureItem assignAddOnFeature(
            UUID addOnId, long expectedVersion, AssignAddOnFeatureRequest request) {
        AddOn addOn = requireEditableAddOnForUpdate(addOnId, expectedVersion);
        var quotaEntries = request.quotaEntries();
        var feature = billingConfigurationValidator.validateAddOnFeature(
                request.featureCode(), quotaEntries, addOn.getCurrencyCode());
        if (addOnFeatureRepository.findByAddOnIdAndFeature_Code(addOnId, request.featureCode()).isPresent()) {
            throw new DuplicateResourceException("AddOnFeature", "featureCode", request.featureCode());
        }
        AddOnFeature item = new AddOnFeature();
        item.setAddOn(addOn);
        item.setFeature(feature);
        item.setQuotaConfigs(new ArrayList<>(quotaEntries));
        addOn.touchDefinition();
        try {
            return readModels.toDto(addOnFeatureRepository.saveAndFlush(item));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("AddOnFeature", "featureCode", request.featureCode());
        }
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_add_on_feature", description = "Update an AddOn feature configuration")
    public AddOnDto.FeatureItem updateAddOnFeature(
            UUID addOnId, UUID addOnFeatureId, long expectedVersion,
            AssignAddOnFeatureRequest request) {
        AddOn addOn = requireEditableAddOnForUpdate(addOnId, expectedVersion);
        AddOnFeature item = requireAddOnFeature(addOnId, addOnFeatureId);
        if (!item.getFeature().getCode().equals(request.featureCode())) {
            throw new InvalidRequestException("An AddOn feature update cannot change its feature code.");
        }
        var quotaEntries = request.quotaEntries();
        billingConfigurationValidator.validateAddOnFeature(
                request.featureCode(), quotaEntries, addOn.getCurrencyCode());
        item.setQuotaConfigs(new ArrayList<>(quotaEntries));
        addOn.touchDefinition();
        return readModels.toDto(addOnFeatureRepository.saveAndFlush(item));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "remove_add_on_feature", description = "Remove a feature from an AddOn draft")
    public void removeAddOnFeature(UUID addOnId, UUID addOnFeatureId, long expectedVersion) {
        AddOn addOn = requireEditableAddOnForUpdate(addOnId, expectedVersion);
        AddOnFeature item = requireAddOnFeature(addOnId, addOnFeatureId);
        addOnFeatureRepository.delete(item);
        addOn.touchDefinition();
        addOnFeatureRepository.flush();
    }

    @Override
    @PermissionNode(key = "list_quota_packages",
            description = "List and filter operational capacity-package revisions")
    @Transactional(readOnly = true)
    public Page<QuotaPackageOperationalListItemDto> listQuotaPackages(
            String search, QuotaPackageStatus status, ProductSalesVisibility salesVisibility,
            UUID lineageId, String featureCode, String resource, String targetPlanCode,
            String targetAddOnCode, Pageable pageable) {
        return productOperationsService.listQuotaPackages(
                search, status, salesVisibility, lineageId, featureCode, resource,
                targetPlanCode, targetAddOnCode, pageable);
    }

    @Override
    @PermissionNode(key = "read_quota_package_operations",
            description = "Read authoritative actions and blockers for one capacity-package revision")
    @Transactional(readOnly = true)
    public QuotaPackageOperationalListItemDto getQuotaPackageOperations(UUID quotaPackageId) {
        return productOperationsService.getQuotaPackageOperations(quotaPackageId);
    }

    @Override
    @PermissionNode(key = "choose_quota_packages",
            description = "Choose an active capacity package without full catalogue access")
    @Transactional(readOnly = true)
    public Page<QuotaPackageChooserItemDto> chooseQuotaPackages(
            String search, ProductSalesVisibility salesVisibility, String featureCode,
            String resource, Pageable pageable) {
        return productOperationsService.chooseQuotaPackages(
                search, salesVisibility, featureCode, resource, pageable);
    }

    @Override
    @PermissionNode(key = "resolve_quota_package_choices",
            description = "Resolve up to 100 persisted capacity-package chooser selections")
    @Transactional(readOnly = true)
    public List<QuotaPackageChooserItemDto> resolveQuotaPackageChoices(
            java.util.Collection<UUID> ids) {
        return productOperationsService.resolveQuotaPackageChoices(ids);
    }

    @Override
    @PermissionNode(key = "resolve_quota_package_choice_codes",
            description = "Resolve up to 100 persisted package chooser codes, including unavailable selections")
    @Transactional(readOnly = true)
    public List<QuotaPackageChooserItemDto> resolveQuotaPackageChoicesByCode(
            java.util.Collection<String> codes) {
        return productOperationsService.resolveQuotaPackageChoicesByCode(codes);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_quota_package", description = "Read commercial quota package detail")
    public QuotaPackageDto getQuotaPackage(UUID quotaPackageId) {
        return readModels.toDto(quotaPackageRepository.findDetailedById(quotaPackageId)
                .orElseThrow(() -> new ResourceNotFoundException("QuotaPackage", "id", quotaPackageId)));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "create_quota_package",
            description = "Create a capacity-package draft and its reviewable initial price draft")
    public QuotaPackageDto createQuotaPackage(CreateQuotaPackageRequest request) {
        crossFeatureCommercialAuthorizer.require(
                "platform.price_books.create",
                "Creating a capacity package and its initial reviewable price draft");
        String code = CommercialCodeGenerator.generate(
                request.name(), "QUOTA_PACKAGE", candidate -> quotaPackageRepository.findByCode(candidate).isPresent());
        QuotaPackage item = new QuotaPackage();
        item.setCode(code);
        var salesVisibility = request.salesVisibility() == null
                ? com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC
                : request.salesVisibility();
        if (salesVisibility
                != com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC) {
            requireCommercialAvailabilityPermission(
                    "update_quota_visibility", "set direct-only quota-package visibility at creation");
        }
        item.setSalesVisibility(salesVisibility);
        applyQuotaPackageBasics(
                item, request.name(), request.description(), request.featureCode(), request.resource(),
                request.capacityPerUnit(), request.price(), request.currencyCode(), request.billingCycle(),
                request.repeatable(), request.maximumQuantity(), request.allowedPlanCodes(),
                request.allowedAddOnCodes());
        item.setStatus(QuotaPackageStatus.DRAFT);
        QuotaPackage saved = quotaPackageRepository.saveAndFlush(item);
        ProductPrice initialPrice = ProductPrice.draft(
                saved, saved.money(), saved.getBillingCycle(), clock.instant(), null);
        initialPrice.markCompatibilityDefault();
        productPriceRepository.saveAndFlush(initialPrice);
        recordCompositePriceAudit(
                ProductPriceAudit.CREATE_ACTION,
                initialPrice,
                "Created with capacity-package draft",
                Map.of(),
                Map.of("status", ProductPriceStatus.DRAFT.name()));
        return readModels.toDto(saved);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "revise_quota_package",
            description = "Create a reasoned successor package and reviewable draft price book")
    public QuotaPackageRevisionResult reviseQuotaPackage(
            UUID sourceQuotaPackageId,
            QuotaPackageRevisionRequest request
    ) {
        requirePriceBookPermission(
                "create", "Revising a capacity package and copying its reviewable price schedules");
        requireReason(request.reason(), "Quota-package revision");
        QuotaPackage hint = quotaPackageRepository.findDetailedById(sourceQuotaPackageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QuotaPackage", "id", sourceQuotaPackageId));
        List<QuotaPackage> lineage = quotaPackageRepository.findLineageForUpdate(hint.getLineageId());
        QuotaPackage source = lineage.stream()
                .filter(candidate -> candidate.getId().equals(sourceQuotaPackageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QuotaPackage", "id", sourceQuotaPackageId));
        requireVersion(source, request.expectedVersion());
        if (source.getSalesVisibility() != ProductSalesVisibility.PUBLIC) {
            requireCommercialAvailabilityPermission(
                    "update_quota_visibility",
                    "copy direct-only capacity-package visibility while revising");
        }
        if (source.getStatus() != QuotaPackageStatus.ACTIVE
                && source.getStatus() != QuotaPackageStatus.INACTIVE) {
            throw new InvalidStateException(
                    "Only a published, non-archived capacity package can be revised.");
        }
        lineage.stream()
                .filter(candidate -> candidate.getStatus() == QuotaPackageStatus.DRAFT)
                .findFirst()
                .ifPresent(draft -> {
                    throw new DraftSuccessorExistsException(
                            "Capacity-package revision R" + draft.getRevisionNumber()
                                    + " is already an editable draft.");
                });
        int maximumRevision = lineage.stream().mapToInt(QuotaPackage::getRevisionNumber).max().orElse(0);
        if (source.getRevisionNumber() != maximumRevision) {
            throw new InvalidStateException("Only the latest capacity-package revision can be revised.");
        }

        List<ProductPrice> sourcePrices = productPriceRepository
                .findAllByQuotaPackageIdForUpdate(sourceQuotaPackageId).stream()
                .filter(price -> price.getStatus() == ProductPriceStatus.ACTIVE)
                .filter(price -> price.getEffectiveUntil() == null
                        || price.getEffectiveUntil().isAfter(clock.instant()))
                .toList();
        String code = CommercialCodeGenerator.generate(
                source.getName(), "QUOTA_PACKAGE",
                candidate -> quotaPackageRepository.findByCode(candidate).isPresent());
        QuotaPackage successor = new QuotaPackage();
        successor.setCode(code);
        successor.setName(source.getName());
        successor.setDescription(source.getDescription());
        successor.setFeature(source.getFeature());
        successor.setResource(source.getResource());
        successor.setCapacityPerUnit(source.getCapacityPerUnit());
        successor.setMoney(source.money());
        successor.setBillingCycle(source.getBillingCycle());
        successor.setRepeatable(source.isRepeatable());
        successor.setMaximumQuantity(source.getMaximumQuantity());
        successor.setAllowedPlanCodes(new LinkedHashSet<>(source.getAllowedPlanCodes()));
        successor.setAllowedAddOnCodes(new LinkedHashSet<>(source.getAllowedAddOnCodes()));
        successor.setSalesVisibility(source.getSalesVisibility());
        successor.setStatus(QuotaPackageStatus.DRAFT);
        successor.setLineageId(source.getLineageId());
        successor.setRevisionNumber(maximumRevision + 1);
        successor.setSourceQuotaPackage(source);
        successor.setCreationReason(QuotaPackageCreationReason.REVISED);

        QuotaPackage saved;
        try {
            saved = quotaPackageRepository.saveAndFlush(successor);
        } catch (DataIntegrityViolationException exception) {
            throw new DraftSuccessorExistsException(
                    "A competing capacity-package draft successor already exists; reload the lineage.");
        }
        List<ProductPrice> copiedPrices = sourcePrices.stream()
                .map(price -> price.copyDraftTo(saved))
                .toList();
        try {
            productPriceRepository.saveAllAndFlush(copiedPrices);
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidStateException(
                    "The successor price starting point conflicts with concurrent catalogue work.");
        }
        copiedPrices.forEach(price -> recordCompositePriceAudit(
                ProductPriceAudit.CREATE_ACTION,
                price,
                request.reason(),
                Map.of("copiedFromQuotaPackageId", source.getId()),
                Map.of("status", ProductPriceStatus.DRAFT.name())));
        return new QuotaPackageRevisionResult(
                readModels.toDto(saved), copiedPrices.stream().map(this::toQuotaPriceDto).toList(),
                copiedPrices.isEmpty() ? List.of("NO_PRICE_STARTING_POINT") : List.of());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "compare_quota_package",
            description = "Compare a capacity-package revision with its direct predecessor or successor")
    public QuotaPackageComparisonDto compareQuotaPackage(
            UUID quotaPackageId,
            UUID againstQuotaPackageId
    ) {
        QuotaPackage candidate = requireDetailedQuotaPackage(quotaPackageId);
        UUID baseId = againstQuotaPackageId;
        if (baseId == null) {
            if (candidate.getSourceQuotaPackage() == null) {
                throw new InvalidRequestException(
                        "A comparison target is required for an original capacity-package revision.");
            }
            baseId = candidate.getSourceQuotaPackage().getId();
        }
        QuotaPackage base = requireDetailedQuotaPackage(baseId);
        if (!candidate.getLineageId().equals(base.getLineageId())) {
            throw new InvalidRequestException("Capacity-package comparisons require the same lineage.");
        }
        boolean direct = isDirectSuccessor(base, candidate) || isDirectSuccessor(candidate, base);
        if (!direct) {
            throw new InvalidRequestException(
                    "Capacity-package comparisons are limited to direct successor revisions.");
        }
        List<ProductPrice> basePrices = productPriceRepository.findAllByQuotaPackageId(base.getId());
        List<ProductPrice> candidatePrices = productPriceRepository.findAllByQuotaPackageId(candidate.getId());
        return new QuotaPackageComparisonDto(
                readModels.toDto(base),
                readModels.toDto(candidate),
                true,
                comparisonFields(base, candidate, basePrices, candidatePrices),
                basePrices.stream().map(this::toQuotaPriceDto).toList(),
                candidatePrices.stream().map(this::toQuotaPriceDto).toList());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_quota_package_activation",
            description = "Preview exact package and price blockers before publication")
    public QuotaPackageActivationPreviewDto previewQuotaPackageActivation(UUID quotaPackageId) {
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        String registryVersion = registryCatalogVersionService.currentVersion();
        return commercialCatalogVersionService.readConsistently(catalogRevision -> {
            QuotaPackage item = requireDetailedQuotaPackage(quotaPackageId);
            List<QuotaPackage> lineage = quotaPackageRepository
                    .findAllByLineageIdOrderByRevisionNumberDesc(item.getLineageId());
            List<ProductPrice> prices = productPriceRepository.findAllByQuotaPackageId(quotaPackageId);
            Instant evaluatedAt = clock.instant();
            var assessment = quotaPackageActivationAssessor.assessPreview(
                    item, lineage, prices, evaluatedAt);
            var evidence = previewTokenService.issue(
                    CommercialPreviewKind.QUOTA_PACKAGE_ACTIVATION,
                    item.getId(), item.getRowVersion(), actorUserId, catalogRevision,
                    registryVersion,
                    assessment.fingerprint(), evaluatedAt);
            return new QuotaPackageActivationPreviewDto(
                    item.getId(), item.getRowVersion(), catalogRevision, registryVersion,
                    evidence.evaluatedAt(), evidence.expiresAt(), evidence.token(),
                    assessment.blockers().isEmpty(), assessment.blockers(),
                    assessment.reviewedPrices(), assessment.packagesToDeactivate());
        }, StaleActivationPreviewException::new);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "lifecycle_quota_package",
            description = "Publish reviewed capacity-package price drafts or change package lifecycle")
    public QuotaPackageDto changeQuotaPackageLifecycle(
            UUID quotaPackageId,
            QuotaPackageLifecycleRequest request
    ) {
        requireReason(request.reason(), "Quota-package lifecycle change");
        String registryVersion = request.action() == QuotaPackageLifecycleAction.ACTIVATE
                ? lockRegistryVersion() : null;
        QuotaPackage hint = requireDetailedQuotaPackage(quotaPackageId);
        List<QuotaPackage> lineage = quotaPackageRepository.findLineageForUpdate(hint.getLineageId());
        QuotaPackage item = lineage.stream()
                .filter(candidate -> candidate.getId().equals(quotaPackageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QuotaPackage", "id", quotaPackageId));
        requireVersion(item, request.expectedVersion());

        switch (request.action()) {
            case ACTIVATE -> activateQuotaPackageRevision(
                    item, lineage, request.activationPreviewToken(), request.reason(), registryVersion);
            case DEACTIVATE -> {
                if (item.getStatus() != QuotaPackageStatus.ACTIVE) {
                    throw new InvalidStateException(
                            "Only an active capacity package can be deactivated.");
                }
                item.setStatus(QuotaPackageStatus.INACTIVE);
                quotaPackageRepository.saveAndFlush(item);
            }
            case ARCHIVE -> {
                if (item.getStatus() != QuotaPackageStatus.INACTIVE
                        && item.getStatus() != QuotaPackageStatus.DRAFT) {
                    throw new InvalidStateException(
                            "Only an inactive or abandoned draft capacity package can be archived.");
                }
                item.setStatus(QuotaPackageStatus.ARCHIVED);
                quotaPackageRepository.saveAndFlush(item);
            }
        }
        return readModels.toDto(requireDetailedQuotaPackage(quotaPackageId));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_quota_package_history",
            description = "Read bounded actor-aware capacity-package lineage history")
    public Page<QuotaPackageHistoryEntryDto> quotaPackageHistory(
            UUID quotaPackageId,
            Pageable pageable
    ) {
        QuotaPackage item = requireDetailedQuotaPackage(quotaPackageId);
        if (pageable == null || pageable.getPageNumber() < 0
                || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new InvalidRequestException(
                    "History page must be non-negative and size must be between 1 and 100.");
        }
        List<QuotaPackage> lineage = quotaPackageRepository
                .findAllByLineageIdOrderByRevisionNumberDesc(item.getLineageId());
        List<String> resourceIds = lineage.stream()
                .map(candidate -> candidate.getId().toString())
                .toList();
        Map<UUID, Integer> revisions = lineage.stream().collect(Collectors.toMap(
                QuotaPackage::getId, QuotaPackage::getRevisionNumber));
        Pageable bounded = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AuditLog> logs = auditLogRepository.findAllByResourceTypeAndResourceIdIn(
                QUOTA_AUDIT_RESOURCE_TYPE, resourceIds, bounded);
        Set<UUID> actorIds = logs.getContent().stream()
                .map(AuditLog::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> actorEmails = actorIds.isEmpty() ? Map.of()
                : adminUserRepository.findAllWithUserByUserIdIn(actorIds).stream()
                        .collect(Collectors.toMap(
                                admin -> admin.getUser().getId(),
                                admin -> admin.getUser().getEmail()));
        return logs.map(log -> {
            UUID resourceId = parseUuid(log.getResourceId());
            QuotaHistoryDetails details = quotaHistoryDetails(
                    log, revisions.get(resourceId));
            return new QuotaPackageHistoryEntryDto(
                    log.getId(), log.getOccurredAt(), log.getActorUserId(),
                    actorEmails.get(log.getActorUserId()), log.getAction(), log.getOutcome(),
                    log.getFailureType(), historyReason(log), resourceId,
                    details.revisionNumber(), details.lifecycleAction(), details.resultingStatus(),
                    details.successorId(), details.successorRevisionNumber());
        });
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_quota_package", description = "Update a commercial quota package draft")
    public QuotaPackageDto updateQuotaPackage(UUID quotaPackageId, UpdateQuotaPackageRequest request) {
        QuotaPackage item = requireEditableQuotaPackage(quotaPackageId);
        requireVersion(item, request.expectedVersion());
        applyQuotaPackageBasics(
                item, request.name(), request.description(), request.featureCode(), request.resource(),
                request.capacityPerUnit(), request.price(), request.currencyCode(), request.billingCycle(),
                request.repeatable(), request.maximumQuantity(), request.allowedPlanCodes(),
                request.allowedAddOnCodes());
        item.touchDefinition();
        quotaPackageRepository.saveAndFlush(item);
        return readModels.toDto(quotaPackageRepository.findDetailedById(quotaPackageId).orElseThrow());
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "delete_quota_package",
            description = "Delete a version-pinned package draft and its unpublished price drafts")
    public void deleteQuotaPackage(UUID quotaPackageId, long expectedVersion) {
        requirePriceBookPermission(
                "delete_draft", "Deleting a capacity package and its owned price drafts");
        QuotaPackage hint = requireDetailedQuotaPackage(quotaPackageId);
        List<QuotaPackage> lineage = quotaPackageRepository.findLineageForUpdate(hint.getLineageId());
        QuotaPackage item = lineage.stream()
                .filter(candidate -> candidate.getId().equals(quotaPackageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QuotaPackage", "id", quotaPackageId));
        requireVersion(item, expectedVersion);
        if (item.getStatus() != QuotaPackageStatus.DRAFT) {
            throw new InvalidStateException(
                    "Only quota package drafts may be deleted; deactivate or archive published packages.");
        }
        List<ProductPrice> prices = productPriceRepository.findAllByQuotaPackageIdForUpdate(quotaPackageId);
        List<String> blockers = new ArrayList<>();
        if (prices.stream().anyMatch(price -> price.getStatus() != ProductPriceStatus.DRAFT)) {
            blockers.add("PUBLISHED_PRICE_HISTORY");
        }
        if (quotaPackageRepository.countBySourceQuotaPackage_Id(quotaPackageId) > 0) {
            blockers.add("LINEAGE_REFERENCE");
        }
        if (!blockers.isEmpty()) {
            throw new OperationBlockedException(
                    "The capacity-package draft cannot be deleted.", blockers);
        }
        if (!prices.isEmpty()) {
            productPriceRepository.deleteAllInBatch(prices);
            productPriceRepository.flush();
        }
        quotaPackageRepository.delete(item);
        quotaPackageRepository.flush();
    }

    private void activateQuotaPackageRevision(
            QuotaPackage item,
            List<QuotaPackage> lineage,
            String previewToken,
            String reason,
            String registryVersion
    ) {
        if (previewToken == null || previewToken.isBlank()) {
            throw new InvalidRequestException(
                    "An activation preview token is required to publish a capacity package.");
        }
        List<ProductPrice> prices = productPriceRepository
                .findAllByQuotaPackageIdForUpdate(item.getId());
        long catalogRevision = commercialCatalogVersionService.currentRevision();
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        Instant evaluatedAt = clock.instant();
        var assessment = quotaPackageActivationAssessor.assessForMutation(
                item, lineage, prices, evaluatedAt);
        previewTokenService.requireValid(
                previewToken,
                CommercialPreviewKind.QUOTA_PACKAGE_ACTIVATION,
                item.getId(),
                item.getRowVersion(),
                actorUserId,
                catalogRevision,
                registryVersion,
                assessment.fingerprint());
        if (!assessment.blockers().isEmpty()) {
            throw new OperationBlockedException(
                    "The capacity-package revision cannot be activated.",
                    assessment.blockers().stream().map(Enum::name).toList());
        }
        // This is a deliberate composite command: it publishes the exact price drafts pinned by
        // the preview. Enforce the complete Price-books policy chain as well as this method's
        // quota lifecycle permission so a disabled runtime feature or narrower role vetoes it.
        crossFeatureCommercialAuthorizer.require(
                "platform.price_books.activate",
                "Publishing a capacity-package revision and its reviewed prices");

        Set<UUID> reviewedIds = assessment.reviewedPrices().stream()
                .map(ProductActivationPriceDto::id)
                .collect(Collectors.toSet());
        List<ProductPrice> draftsToActivate = prices.stream()
                .filter(price -> reviewedIds.contains(price.getId()))
                .filter(price -> price.getStatus() == ProductPriceStatus.DRAFT)
                .toList();
        item.setStatus(QuotaPackageStatus.ACTIVE);
        lineage.stream()
                .filter(candidate -> !candidate.getId().equals(item.getId()))
                .filter(candidate -> candidate.getStatus() == QuotaPackageStatus.ACTIVE)
                .forEach(candidate -> candidate.setStatus(QuotaPackageStatus.INACTIVE));
        draftsToActivate.forEach(ProductPrice::activate);
        quotaPackageRepository.saveAllAndFlush(lineage);
        if (!draftsToActivate.isEmpty()) {
            productPriceRepository.saveAllAndFlush(draftsToActivate);
            draftsToActivate.forEach(price -> recordCompositePriceAudit(
                    ProductPriceAudit.ACTIVATE_ACTION,
                    price,
                    reason,
                    Map.of("status", ProductPriceStatus.DRAFT.name()),
                    Map.of("status", ProductPriceStatus.ACTIVE.name())));
        }
    }

    private List<QuotaPackageComparisonField> comparisonFields(
            QuotaPackage base,
            QuotaPackage candidate,
            List<ProductPrice> basePrices,
            List<ProductPrice> candidatePrices
    ) {
        EnumSet<QuotaPackageComparisonField> changed =
                EnumSet.noneOf(QuotaPackageComparisonField.class);
        if (!Objects.equals(base.getName(), candidate.getName())) changed.add(QuotaPackageComparisonField.NAME);
        if (!Objects.equals(base.getDescription(), candidate.getDescription())) {
            changed.add(QuotaPackageComparisonField.DESCRIPTION);
        }
        if (!base.getFeature().getCode().equals(candidate.getFeature().getCode())) {
            changed.add(QuotaPackageComparisonField.FEATURE);
        }
        if (!base.getResource().equals(candidate.getResource())) changed.add(QuotaPackageComparisonField.RESOURCE);
        if (base.getCapacityPerUnit() != candidate.getCapacityPerUnit()) {
            changed.add(QuotaPackageComparisonField.CAPACITY_PER_UNIT);
        }
        if (base.isRepeatable() != candidate.isRepeatable()) changed.add(QuotaPackageComparisonField.REPEATABLE);
        if (base.getMaximumQuantity() != candidate.getMaximumQuantity()) {
            changed.add(QuotaPackageComparisonField.MAXIMUM_QUANTITY);
        }
        if (!base.getAllowedPlanCodes().equals(candidate.getAllowedPlanCodes())) {
            changed.add(QuotaPackageComparisonField.ALLOWED_PLANS);
        }
        if (!base.getAllowedAddOnCodes().equals(candidate.getAllowedAddOnCodes())) {
            changed.add(QuotaPackageComparisonField.ALLOWED_ADD_ONS);
        }
        if (base.getSalesVisibility() != candidate.getSalesVisibility()) {
            changed.add(QuotaPackageComparisonField.SALES_VISIBILITY);
        }
        if (!priceTerms(basePrices).equals(priceTerms(candidatePrices))) {
            changed.add(QuotaPackageComparisonField.PRICE_BOOK);
        }
        return List.copyOf(changed);
    }

    private List<String> priceTerms(List<ProductPrice> prices) {
        return prices.stream()
                .filter(price -> price.getStatus() != ProductPriceStatus.ARCHIVED)
                .map(price -> String.join("|",
                        price.getAmount().toPlainString(), price.getCurrencyCode(),
                        price.getBillingCycle().name(), price.getEffectiveFrom().toString(),
                        price.getEffectiveUntil() == null ? "OPEN" : price.getEffectiveUntil().toString(),
                        Boolean.toString(price.isCompatibilityDefault())))
                .sorted()
                .toList();
    }

    private boolean isDirectSuccessor(QuotaPackage source, QuotaPackage successor) {
        return successor.getSourceQuotaPackage() != null
                && source.getId().equals(successor.getSourceQuotaPackage().getId())
                && successor.getRevisionNumber() == source.getRevisionNumber() + 1;
    }

    private QuotaPackagePriceDraftDto toQuotaPriceDto(ProductPrice price) {
        return new QuotaPackagePriceDraftDto(
                price.getId(), price.getAmount(), price.getCurrencyCode(), price.getBillingCycle(),
                price.getStatus(), price.getEffectiveFrom(), price.getEffectiveUntil(),
                price.getLineageId(), price.getRevisionNumber(), price.getVersion(),
                price.isCompatibilityDefault());
    }


    private QuotaPackage requireDetailedQuotaPackage(UUID quotaPackageId) {
        return quotaPackageRepository.findDetailedById(quotaPackageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QuotaPackage", "id", quotaPackageId));
    }

    private void requireVersion(QuotaPackage item, long expectedVersion) {
        if (item.getRowVersion() != expectedVersion) {
            throw new StaleResourceVersionException(
                    "The capacity package changed since it was read. Reload it and retry.");
        }
    }

    private void requireVersion(Plan plan, long expectedVersion) {
        if (plan.getVersion() != expectedVersion) {
            throw new StaleResourceVersionException(
                    "The Plan changed since it was read. Reload it and retry.");
        }
    }

    private void requireVersion(AddOn addOn, long expectedVersion) {
        if (addOn.getRowVersion() != expectedVersion) {
            throw new StaleResourceVersionException(
                    "The AddOn changed since it was read. Reload it and retry.");
        }
    }

    private void requireReason(String reason, String operation) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException(operation + " requires an operator reason.");
        }
        if (reason.trim().length() > 500) {
            throw new InvalidRequestException("Operator reasons must not exceed 500 characters.");
        }
    }

    private String historyReason(AuditLog log) {
        if (log.getRequestData() == null) return null;
        try {
            var request = objectMapper.readTree(log.getRequestData());
            var reason = request.get("reason");
            if ((reason == null || !reason.isTextual()) && request.path("request").isObject()) {
                reason = request.path("request").get("reason");
            }
            return reason != null && reason.isTextual() && !reason.textValue().isBlank()
                    ? reason.textValue().trim() : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return null;
        }
    }

    private QuotaHistoryDetails quotaHistoryDetails(AuditLog log, Integer fallbackRevision) {
        Integer revisionNumber = fallbackRevision;
        QuotaPackageLifecycleAction lifecycleAction = null;
        QuotaPackageStatus resultingStatus = null;
        UUID successorId = null;
        Integer successorRevisionNumber = null;
        try {
            if (log.getRequestData() != null) {
                var arguments = objectMapper.readTree(log.getRequestData());
                var request = arguments.path("request").isObject()
                        ? arguments.path("request") : arguments;
                if (log.getAction() != null && (log.getAction().contains("lifecycle_quota_package")
                        || log.getAction().contains("transition_quota_package_status"))) {
                    lifecycleAction = parseLifecycleAction(request.path("action").asText(null));
                    if (lifecycleAction == null) {
                        lifecycleAction = lifecycleActionForStatus(
                                request.path("status").asText(arguments.path("status").asText(null)));
                    }
                }
            }
            if (log.getResultData() != null) {
                var result = objectMapper.readTree(log.getResultData());
                if (result.path("revisionNumber").canConvertToInt()) {
                    revisionNumber = result.path("revisionNumber").intValue();
                }
                resultingStatus = parseQuotaStatus(result.path("status").asText(null));
                var successor = result.path("successor");
                if (successor.isObject()) {
                    successorId = parseUuid(successor.path("id").asText(null));
                    if (successor.path("revisionNumber").canConvertToInt()) {
                        successorRevisionNumber = successor.path("revisionNumber").intValue();
                    }
                    if (resultingStatus == null) {
                        resultingStatus = parseQuotaStatus(successor.path("status").asText(null));
                    }
                }
            }
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // Older or deliberately truncated audit payloads remain readable with nullable details.
        }
        return new QuotaHistoryDetails(
                revisionNumber, lifecycleAction, resultingStatus, successorId, successorRevisionNumber);
    }

    private QuotaPackageLifecycleAction parseLifecycleAction(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return QuotaPackageLifecycleAction.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private QuotaPackageLifecycleAction lifecycleActionForStatus(String value) {
        QuotaPackageStatus status = parseQuotaStatus(value);
        if (status == null) return null;
        return switch (status) {
            case ACTIVE -> QuotaPackageLifecycleAction.ACTIVATE;
            case INACTIVE -> QuotaPackageLifecycleAction.DEACTIVATE;
            case ARCHIVED -> QuotaPackageLifecycleAction.ARCHIVE;
            case DRAFT -> null;
        };
    }

    private QuotaPackageStatus parseQuotaStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return QuotaPackageStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record QuotaHistoryDetails(
            Integer revisionNumber,
            QuotaPackageLifecycleAction lifecycleAction,
            QuotaPackageStatus resultingStatus,
            UUID successorId,
            Integer successorRevisionNumber
    ) {}

    private Plan createBranch(
            Plan source,
            PlanBranchRequest request,
            UUID lineageId,
            int revisionNumber,
            PlanCreationReason creationReason
    ) {
        String code = CommercialCodeGenerator.generate(
                request.name(), "PLAN", candidate -> planRepository.findByCode(candidate).isPresent());
        Money price = validatePlanBasics(
                request.name(), request.price(), request.currencyCode(), request.billingCycle());
        return saveNewPlan(code, request.name(), request.description(), price, request.billingCycle(),
                source, lineageId, revisionNumber, creationReason);
    }

    private Plan saveNewPlan(
            String code,
            String name,
            String description,
            Money price,
            BillingCycle billingCycle,
            Plan source,
            UUID lineageId,
            int revisionNumber,
            PlanCreationReason creationReason
    ) {
        if (planRepository.findByCode(code).isPresent()) {
            throw new DuplicateResourceException("Plan", "code", code);
        }
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(name);
        plan.setDescription(description);
        plan.setMoney(price);
        plan.setBillingCycle(billingCycle);
        plan.setStatus(PlanStatus.DRAFT);
        plan.setSourcePlan(source);
        plan.setLineageId(lineageId);
        plan.setRevisionNumber(revisionNumber);
        plan.setCreationReason(creationReason);
        if (source != null) {
            plan.setExtensionPolicy(source.getExtensionPolicy());
            plan.setSalesVisibility(source.getSalesVisibility());
        }
        try {
            return planRepository.saveAndFlush(plan);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("Plan", "code or lineage revision", code);
        }
    }

    private void copyPlanComposition(Plan sourcePlan, Plan targetPlan) {
        var sourceFeatures = planFeatureRepository.findAllByPlanId(sourcePlan.getId());
        var inheritedFeatures = sourceFeatures.stream()
                .map(sourceFeature -> {
                    billingConfigurationValidator.validatePlanFeature(
                            sourceFeature.getFeature().getCode(),
                            sourceFeature.getMode(),
                            sourceFeature.getQuotaConfigs(),
                            targetPlan.getCurrencyCode());
                    PlanFeature copy = new PlanFeature();
                    copy.setPlan(targetPlan);
                    copy.setFeature(sourceFeature.getFeature());
                    copy.setMode(sourceFeature.getMode());
                    copy.setQuotaConfigs(sourceFeature.getQuotaConfigs() != null
                            ? new ArrayList<>(sourceFeature.getQuotaConfigs())
                            : new ArrayList<>());
                    return copy;
                })
                .toList();

        if (!inheritedFeatures.isEmpty()) {
            planFeatureRepository.saveAll(inheritedFeatures);
        }
    }

    private void requireMutable(Plan plan) {
        if (plan.getStatus() != PlanStatus.DRAFT) {
            throw new BusinessException(
                    "Published plans are commercially immutable; create a draft revision to change them.");
        }
    }

    private Plan requirePlan(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
    }

    private AddOn requireAddOn(UUID addOnId) {
        return addOnRepository.findById(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
    }

    private AddOn requireEditableAddOn(UUID addOnId) {
        AddOn addOn = requireAddOn(addOnId);
        requireEditable(addOn);
        return addOn;
    }

    private AddOn requireEditableAddOnForUpdate(UUID addOnId, long expectedVersion) {
        AddOn addOn = addOnRepository.findByIdForUpdate(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
        requireVersion(addOn, expectedVersion);
        requireEditable(addOn);
        return addOn;
    }

    private void requireEditable(AddOn addOn) {
        if (addOn.getStatus() != AddOnStatus.DRAFT) {
            throw new BusinessException(
                    "Published AddOns are immutable; create and publish a draft revision instead.");
        }
    }

    private AddOnFeature requireAddOnFeature(UUID addOnId, UUID addOnFeatureId) {
        AddOnFeature item = addOnFeatureRepository.findById(addOnFeatureId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnFeature", "id", addOnFeatureId));
        if (!item.getAddOn().getId().equals(addOnId)) {
            throw new ResourceNotFoundException("AddOnFeature", "id", addOnFeatureId);
        }
        return item;
    }

    private QuotaPackage requireQuotaPackage(UUID quotaPackageId) {
        return quotaPackageRepository.findById(quotaPackageId)
                .orElseThrow(() -> new ResourceNotFoundException("QuotaPackage", "id", quotaPackageId));
    }

    private QuotaPackage requireEditableQuotaPackage(UUID quotaPackageId) {
        QuotaPackage item = requireQuotaPackage(quotaPackageId);
        if (item.getStatus() != QuotaPackageStatus.DRAFT) {
            throw new BusinessException(
                    "Published quota packages are immutable; create a new draft product instead.");
        }
        return item;
    }

    private void applyQuotaPackageBasics(
            QuotaPackage item,
            String name,
            String description,
            String featureCode,
            String resource,
            long capacityPerUnit,
            BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle,
            boolean repeatable,
            int maximumQuantity,
            Set<String> allowedPlanCodes,
            Set<String> allowedAddOnCodes
    ) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Quota package name is required.");
        }
        if (capacityPerUnit <= 0) {
            throw new InvalidRequestException("Quota package capacity must be positive.");
        }
        if (maximumQuantity <= 0 || (!repeatable && maximumQuantity != 1)) {
            throw new InvalidRequestException(
                    "A non-repeatable package must have maximum quantity 1; repeatable packages require a positive maximum.");
        }
        Money money;
        try {
            money = Money.of(price, currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Quota package price is invalid: " + exception.getMessage());
        }
        if (money.isNegative()) {
            throw new InvalidRequestException("Quota package price cannot be negative.");
        }
        if (billingCycle == null || billingCycle == BillingCycle.FOREVER) {
            throw new InvalidRequestException("Quota packages require a MONTHLY or YEARLY billing cycle.");
        }
        var feature = billingConfigurationValidator.validateQuotaPackageDefinition(featureCode, resource);
        Set<String> plans = normalizeCodes(allowedPlanCodes, "Allowed Plan code");
        Set<String> addOns = normalizeCodes(allowedAddOnCodes, "Allowed AddOn code");
        requireLockedPlans(plans, "Unknown Plan code in quota package availability: ");
        requireLockedAddOns(addOns, null, "Unknown AddOn code in quota package availability: ");

        item.setName(name);
        item.setDescription(description);
        item.setFeature(feature);
        item.setResource(resource);
        item.setCapacityPerUnit(capacityPerUnit);
        item.setMoney(money);
        item.setBillingCycle(billingCycle);
        item.setRepeatable(repeatable);
        item.setMaximumQuantity(maximumQuantity);
        item.setAllowedPlanCodes(plans);
        item.setAllowedAddOnCodes(addOns);
    }

    private void applyAddOnBasics(
            AddOn addOn,
            String name,
            String description,
            BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle,
            Set<String> allowedPlanCodes,
            Set<String> blockedPlanCodes,
            Set<String> dependencyCodes,
            Set<String> exclusionCodes
    ) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("AddOn name is required.");
        }
        Money money;
        try {
            money = Money.of(price, currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("AddOn price is invalid: " + exception.getMessage());
        }
        if (money.isNegative()) {
            throw new InvalidRequestException("AddOn price cannot be negative.");
        }
        if (billingCycle == null || billingCycle == BillingCycle.FOREVER) {
            throw new InvalidRequestException("AddOns require a MONTHLY or YEARLY billing cycle.");
        }

        Set<String> allowed = normalizeCodes(allowedPlanCodes, "Allowed Plan code");
        Set<String> blocked = normalizeCodes(blockedPlanCodes, "Blocked Plan code");
        Set<String> dependencies = normalizeCodes(dependencyCodes, "Dependency AddOn code");
        Set<String> exclusions = normalizeCodes(exclusionCodes, "Excluded AddOn code");
        if (!java.util.Collections.disjoint(allowed, blocked)) {
            throw new InvalidRequestException("An AddOn cannot both allow and block the same Plan.");
        }
        if (!java.util.Collections.disjoint(dependencies, exclusions)) {
            throw new InvalidRequestException("An AddOn cannot both require and exclude the same AddOn.");
        }
        requireLockedPlans(union(allowed, blocked), "Unknown Plan code in AddOn availability: ");
        Set<String> relatedCodes = union(dependencies, exclusions);
        if (relatedCodes.contains(addOn.getCode())) {
            throw new InvalidRequestException("An AddOn cannot depend on or exclude itself.");
        }
        requireLockedAddOns(relatedCodes, addOn.getCode(), "Unknown related AddOn code: ");

        addOn.setName(name);
        addOn.setDescription(description);
        addOn.setMoney(money);
        addOn.setBillingCycle(billingCycle);
        addOn.setAllowedPlanCodes(allowed);
        addOn.setBlockedPlanCodes(blocked);
        addOn.setDependencyCodes(dependencies);
        addOn.setExclusionCodes(exclusions);
    }

    /**
     * JSON code references have no foreign key. Locking the referenced products before the
     * writer persists them serializes reference creation with product deletion: either deletion
     * observes the committed reference, or this validation re-runs after deletion and rejects the
     * now-missing code. Sorted repository locks keep every multi-target acquisition deterministic.
     */
    private void requireLockedPlans(Collection<String> codes, String missingPrefix) {
        if (codes.isEmpty()) return;
        List<Plan> locked = planRepository.findAllByCodeInForUpdate(codes.stream().sorted().toList());
        Set<String> found = locked.stream().map(Plan::getCode).collect(Collectors.toSet());
        codes.stream().sorted().filter(code -> !found.contains(code)).findFirst()
                .ifPresent(code -> {
                    throw new InvalidRequestException(missingPrefix + code);
                });
    }

    private void requireLockedAddOns(
            Collection<String> codes,
            String currentCode,
            String missingPrefix
    ) {
        List<String> targets = codes.stream()
                .filter(code -> !Objects.equals(code, currentCode))
                .sorted()
                .toList();
        if (targets.isEmpty()) return;
        List<AddOn> locked = addOnRepository.findAllByCodeInForUpdate(targets);
        Set<String> found = locked.stream().map(AddOn::getCode).collect(Collectors.toSet());
        targets.stream().filter(code -> !found.contains(code)).findFirst()
                .ifPresent(code -> {
                    throw new InvalidRequestException(missingPrefix + code);
                });
    }

    private void requirePlanSupportsAddOn(Plan plan, List<AddOnFeature> features) {
        var modes = planFeatureRepository.findAllByPlanId(plan.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        feature -> feature.getFeature().getCode(), PlanFeature::getMode));
        for (AddOnFeature feature : features) {
            if (modes.get(feature.getFeature().getCode()) != PlanFeatureMode.OPTIONAL_ADD_ON) {
                throw new BusinessException(
                        "Plan " + plan.getCode() + " does not permit AddOn feature "
                                + feature.getFeature().getCode() + ".");
            }
        }
    }

    private Set<String> normalizeCodes(Set<String> values, String label) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values == null ? Set.<String>of() : values) {
            normalized.add(normalizeCommercialCode(value, label));
        }
        return normalized;
    }

    private String normalizeCommercialCode(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(label + " is required.");
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!COMMERCIAL_CODE.matcher(normalized).matches()) {
            throw new InvalidRequestException(label + " must use uppercase letters, numbers, and underscores.");
        }
        return normalized;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Set<String> union(Set<String> left, Set<String> right) {
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return union;
    }

    private void requireCommercialAvailabilityPermission(String node, String operation) {
        String permissionCode = CommercialAvailabilityFeature.CODE + "." + node;
        // This is a cross-feature mutation hidden behind a plans.* entry point. A role ceiling
        // check alone would bypass Permissionizer's mandatory runtime veto for the commercial
        // availability feature. Evaluate the complete policy chain just as the dedicated
        // availability service endpoint does.
        if (!PermissionGuard.has(new Permission(permissionCode))) {
            throw new ForbiddenException(operation + " requires " + permissionCode + ".");
        }
    }

    private void requireCopiedPlanAvailabilityPermission(Plan source, String operation) {
        if (source.getExtensionPolicy() != PlanExtensionPolicy.OPEN_COMPATIBLE
                || source.getSalesVisibility() != ProductSalesVisibility.PUBLIC) {
            requireCommercialAvailabilityPermission(
                    "update_plan_policy",
                    operation + " a Plan with non-default availability policy");
        }
    }

    private void requirePriceBookPermission(String node, String operation) {
        String permissionCode = "platform.price_books." + node;
        crossFeatureCommercialAuthorizer.require(permissionCode, operation);
    }

    private void createInitialPriceDraft(Plan plan) {
        ProductPrice price = ProductPrice.draft(
                plan, plan.money(), plan.getBillingCycle(), clock.instant(), null);
        price.markCompatibilityDefault();
        productPriceRepository.saveAndFlush(price);
        recordCompositePriceAudit(
                ProductPriceAudit.CREATE_ACTION,
                price,
                "Created with Plan draft",
                Map.of(),
                Map.of("status", ProductPriceStatus.DRAFT.name()));
    }

    private void createInitialPriceDraft(AddOn addOn) {
        ProductPrice price = ProductPrice.draft(
                addOn, addOn.money(), addOn.getBillingCycle(), clock.instant(), null);
        price.markCompatibilityDefault();
        productPriceRepository.saveAndFlush(price);
        recordCompositePriceAudit(
                ProductPriceAudit.CREATE_ACTION,
                price,
                "Created with AddOn draft",
                Map.of(),
                Map.of("status", ProductPriceStatus.DRAFT.name()));
    }

    private void copySaleRelevantPrices(Plan source, Plan target) {
        List<ProductPrice> copied = saleRelevantSourcePrices(
                        productPriceRepository.findAllByPlanIdForUpdate(source.getId()))
                .stream()
                .map(price -> price.copyDraftTo(target))
                .toList();
        saveCopiedPriceDrafts(copied, "Copied with Plan branch");
    }

    private void copySaleRelevantPrices(AddOn source, AddOn target) {
        List<ProductPrice> copied = saleRelevantSourcePrices(
                        productPriceRepository.findAllByAddOnIdForUpdate(source.getId()))
                .stream()
                .map(price -> price.copyDraftTo(target))
                .toList();
        saveCopiedPriceDrafts(copied, "Copied with AddOn revision");
    }

    private List<ProductPrice> saleRelevantSourcePrices(List<ProductPrice> sourcePrices) {
        return sourcePrices.stream()
                .filter(price -> price.getStatus() == ProductPriceStatus.ACTIVE)
                .filter(price -> price.getEffectiveUntil() == null
                        || price.getEffectiveUntil().isAfter(clock.instant()))
                .filter(price -> price.getBillingCycle() == BillingCycle.MONTHLY
                        || price.getBillingCycle() == BillingCycle.YEARLY)
                .toList();
    }

    private void saveCopiedPriceDrafts(List<ProductPrice> copied, String reason) {
        try {
            productPriceRepository.saveAllAndFlush(copied);
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidStateException(
                    "The copied price starting point conflicts with concurrent catalogue work.");
        }
        copied.forEach(price -> recordCompositePriceAudit(
                ProductPriceAudit.CREATE_ACTION,
                price,
                reason,
                Map.of(),
                Map.of("status", ProductPriceStatus.DRAFT.name())));
    }

    private void recordCompositePriceAudit(
            String action,
            ProductPrice price,
            String reason,
            Map<String, ?> before,
            Map<String, ?> after
    ) {
        Map<String, Object> beforePayload = new LinkedHashMap<>();
        if (before != null) beforePayload.putAll(before);
        beforePayload.put("reason", reason);
        Map<String, Object> afterPayload = new LinkedHashMap<>();
        if (after != null) afterPayload.putAll(after);
        afterPayload.put("ownerType", price.getOwnerType().name());
        afterPayload.put("ownerId", price.ownerId());
        afterPayload.put("priceRevision", price.getRevisionNumber());
        auditTrail.recordSuccess(
                action,
                ProductPriceAudit.RESOURCE_TYPE,
                price.getId(),
                AuditActorSurface.PLATFORM_ADMIN,
                adminMutationAuthorizer.currentActorUserId(),
                null,
                beforePayload,
                afterPayload);
    }

    private void requireActivationToken(String token, String productLabel) {
        if (token == null || token.isBlank()) {
            throw new InvalidRequestException(
                    productLabel + " activation requires a fresh activation preview token.");
        }
    }

    private Money validatePlanBasics(
            String name,
            BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Plan name is required.");
        }
        Money money;
        try {
            money = Money.of(price, currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Plan price is invalid: " + exception.getMessage());
        }
        if (money.isNegative()) {
            throw new InvalidRequestException("Plan price cannot be negative.");
        }
        if (billingCycle == null) {
            throw new InvalidRequestException("Plan billing cycle is required.");
        }
        if (billingCycle == BillingCycle.FOREVER) {
            throw new InvalidRequestException("Perpetual commercial plans are deferred; use MONTHLY or YEARLY.");
        }
        return money;
    }

    private PlanDeletionAssessment assessDeletion(
            Plan plan,
            List<ProductPrice> prices
    ) {
        UUID planId = plan.getId();
        int ownedFeatureCount = planFeatureRepository.findAllByPlanId(planId).size();
        long subscriptionHistory = subscriptionRepository.countByPlan_Id(planId);
        long changeOperationReferences = subscriptionChangeOperationRepository.countByTargetPlan_Id(planId);
        long addOnReferences = countRows(addOnRepository.countPlanReferences(Set.of(planId)), planId);
        long quotaPackageReferences = countRows(
                quotaPackageRepository.countPlanReferences(Set.of(planId)), planId);
        long lineageReferences = planRepository.countBySourcePlan_Id(planId);

        List<String> blockers = new ArrayList<>();
        if (PlanCodes.DEFAULT.equals(plan.getCode())) {
            blockers.add("DEFAULT_PROVISIONING_PLAN");
        }
        if (plan.getStatus() != PlanStatus.DRAFT) {
            blockers.add("NOT_UNUSED_DRAFT");
        }
        if (subscriptionHistory > 0) {
            blockers.add("SUBSCRIPTION_HISTORY");
        }
        if (changeOperationReferences > 0) {
            blockers.add("CHANGE_OPERATION_HISTORY");
        }
        if (addOnReferences > 0) {
            blockers.add("ADDON_REFERENCE");
        }
        if (quotaPackageReferences > 0) {
            blockers.add("QUOTA_PACKAGE_REFERENCE");
        }
        if (lineageReferences > 0) {
            blockers.add("LINEAGE_REFERENCE");
        }
        if (prices.stream().anyMatch(price -> price.getStatus() != ProductPriceStatus.DRAFT)) {
            blockers.add("PUBLISHED_PRICE_HISTORY");
        }

        String priceState = prices.stream()
                .sorted(Comparator.comparing(ProductPrice::getId))
                .map(price -> price.getId() + ":" + price.getVersion() + ":" + price.getStatus())
                .collect(Collectors.joining(","));

        String state = String.join("|",
                planId.toString(),
                plan.getCode(),
                Long.toString(plan.getVersion()),
                plan.getStatus().name(),
                Integer.toString(ownedFeatureCount),
                Long.toString(subscriptionHistory),
                Long.toString(changeOperationReferences),
                Long.toString(addOnReferences),
                Long.toString(quotaPackageReferences),
                Long.toString(lineageReferences),
                priceState);
        return new PlanDeletionAssessment(
                ownedFeatureCount,
                subscriptionHistory,
                changeOperationReferences,
                addOnReferences,
                quotaPackageReferences,
                lineageReferences, List.copyOf(blockers), sha256(state));
    }

    private PlanDeletionPreview toDeletionPreview(
            Plan plan,
            PlanDeletionAssessment assessment,
            long catalogRevision,
            String registryVersion,
            CommercialPreviewTokenService.IssuedEvidence evidence
    ) {
        return new PlanDeletionPreview(
                plan.getId(), plan.getName(), plan.getVersion(), catalogRevision,
                registryVersion, evidence.evaluatedAt(), evidence.expiresAt(), evidence.token(),
                assessment.blockers().isEmpty(), assessment.ownedFeatureCount(),
                assessment.subscriptionHistoryCount(), assessment.changeOperationReferenceCount(),
                assessment.addOnReferenceCount(), assessment.quotaPackageReferenceCount(),
                assessment.lineageReferenceCount(), assessment.blockers());
    }

    private StaleResourceVersionException staleDeletionPreview() {
        return new StaleResourceVersionException(
                "Plan deletion preview is stale; request a fresh preview.");
    }

    /** Pins registry state for the full reviewed mutation while the commercial lock is held. */
    private String lockRegistryVersion() {
        registryCatalogVersionService.lockForMutation();
        return registryCatalogVersionService.currentVersion();
    }

    private record PlanDeletionAssessment(
            int ownedFeatureCount,
            long subscriptionHistoryCount,
            long changeOperationReferenceCount,
            long addOnReferenceCount,
            long quotaPackageReferenceCount,
            long lineageReferenceCount,
            List<String> blockers,
            String fingerprint
    ) {}

    private long countRows(List<Object[]> rows, UUID id) {
        return rows.stream()
                .filter(row -> id.equals(row[0]))
                .mapToLong(row -> ((Number) row[1]).longValue())
                .findFirst()
                .orElse(0L);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Pageable safeSubscriberPage(Pageable pageable) {
        int page = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        int size = pageable == null ? 20 : Math.min(100, Math.max(1, pageable.getPageSize()));
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private PlanSubscriberDto toSubscriberDto(Plan plan, Subscription subscription) {
        return new PlanSubscriberDto(
                subscription.getId(),
                subscription.getAccount().getId(),
                subscription.getAccount().getName(),
                plan.getCode(),
                subscription.getStatus(),
                subscription.getCurrentPrice(),
                subscription.getCurrentPriceCurrencyCode(),
                subscription.getCurrentPeriodEnd());
    }

    private List<SubscriptionStatus> usableStatuses() {
        return List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING);
    }

    private List<String> warnings(
            Plan plan,
            List<PlanFeature> planFeatures,
            long currentSubscribers,
            long historicalSubscribers
    ) {
        List<String> warnings = new ArrayList<>();
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            warnings.add("PLAN_INACTIVE");
        }
        if (planFeatures.isEmpty()) {
            warnings.add("NO_FEATURES");
        }
        if (currentSubscribers > 0) {
            warnings.add("HAS_CURRENT_SUBSCRIBERS");
            warnings.add("SUBSCRIBER_TERMS_CHANGE_ONLY_THROUGH_EXPLICIT_OPERATIONS");
        } else if (historicalSubscribers > 0) {
            warnings.add("HAS_SUBSCRIPTION_HISTORY");
        }
        return warnings;
    }
}
