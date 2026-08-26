package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.AddOnCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanCodes;
import com.hiveapp.platform.client.plan.domain.constant.PlanCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
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
import com.hiveapp.platform.client.plan.service.CommercialCodeGenerator;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.PlanFeatureDto;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import com.hiveapp.platform.client.plan.service.PlanAdminReadModels;
import com.hiveapp.platform.client.plan.service.PlanAdminService;
import com.hiveapp.platform.client.plan.service.ProductPriceResolver;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.CommercialAvailabilityFeature;
import com.hiveapp.platform.registry.definition.PlansFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.quota.QuotaLimitMode;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@PermissionNode(key = PlansFeature.KEY, description = "Plan Catalogue Management", guard = PermissionNode.Guard.ON)
public class PlanAdminServiceImpl extends PlatformControlFeatureService implements PlanAdminService {

    private static final Pattern COMMERCIAL_CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

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
    private final com.hiveapp.platform.client.plan.service.ProductPriceCompatibilityService
            productPriceCompatibilityService;
    private final ProductPriceResolver productPriceResolver;

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
    @PermissionNode(key = "list", description = "List all plans")
    @Transactional(readOnly = true)
    public List<PlanDto> listPlans() {
        return planRepository.findAll().stream().map(readModels::toDto).toList();
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
                historicalSubscribers,
                currentRecurringPrice.amount(),
                currentRecurringPrice.currencyCode(),
                warnings(plan, planFeatures, currentSubscribers, historicalSubscribers),
                plan.getExtensionPolicy(), plan.getSalesVisibility(), plan.getVersion()
        );
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create a new plan")
    public PlanDto createPlan(CreatePlanRequest request) {
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
        return readModels.toDto(plan);
    }

    @Override
    @Transactional
    @PermissionNode(key = "duplicate", description = "Duplicate plan commercial configuration into a draft")
    public PlanDto duplicatePlan(UUID sourcePlanId, PlanBranchRequest request) {
        Plan source = requirePlan(sourcePlanId);
        Plan duplicate = createBranch(source, request, UUID.randomUUID(), 1, PlanCreationReason.DUPLICATED);
        copyPlanComposition(source, duplicate);
        return readModels.toDto(duplicate);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revise", description = "Create the next draft revision of a published plan")
    public PlanDto revisePlan(UUID sourcePlanId, PlanBranchRequest request) {
        Plan source = planRepository.findByIdForUpdate(sourcePlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", sourcePlanId));
        if (source.getStatus() == PlanStatus.DRAFT) {
            throw new BusinessException("A draft can be edited directly and cannot be revised.");
        }
        int nextRevision = planRepository.findMaximumRevisionNumber(source.getLineageId()) + 1;
        Plan revision = createBranch(
                source, request, source.getLineageId(), nextRevision, PlanCreationReason.REVISED);
        copyPlanComposition(source, revision);
        return readModels.toDto(revision);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update", description = "Update plan template basics")
    public PlanDto updatePlan(UUID planId, UpdatePlanRequest request) {
        Plan plan = requirePlan(planId);
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
        return readModels.toDto(planRepository.save(plan));
    }

    @Override
    @Transactional
    @PermissionNode(key = "transition_status", description = "Transition a plan lifecycle state")
    public PlanDto transitionStatus(UUID planId, PlanStatus targetStatus) {
        var plan = requirePlan(planId);
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
        if (plan.getStatus() == PlanStatus.DRAFT && targetStatus == PlanStatus.INACTIVE) {
            throw new BusinessException("A draft Plan must be activated or archived.");
        }
        if (PlanCodes.DEFAULT.equals(plan.getCode()) && targetStatus != PlanStatus.ACTIVE) {
            throw new BusinessException("The default FREE plan must remain ACTIVE because workspace provisioning requires it.");
        }
        if (targetStatus == PlanStatus.ACTIVE) {
            validateActivation(plan);
        }
        plan.setStatus(targetStatus);
        Plan saved = planRepository.saveAndFlush(plan);
        if (targetStatus == PlanStatus.ACTIVE) {
            productPriceCompatibilityService.ensurePublishedDefault(saved);
        }
        return readModels.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_delete", description = "Preview plan deletion impact and blockers")
    public PlanDeletionPreview previewPlanDeletion(UUID planId) {
        return buildDeletionPreview(requirePlan(planId));
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete", description = "Delete a confirmed unused plan draft")
    public void deletePlan(UUID planId, DeletePlanRequest request) {
        Plan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        PlanDeletionPreview preview = buildDeletionPreview(plan);
        if (request.expectedVersion() != preview.expectedVersion()
                || !request.previewToken().equals(preview.previewToken())) {
            throw new InvalidStateException("Plan deletion preview is stale; request a fresh preview.");
        }
        if (!plan.getName().equals(request.confirmationName())) {
            throw new InvalidRequestException("Plan name confirmation does not match.");
        }
        if (!preview.deletable()) {
            throw new BusinessException(
                    "Plan cannot be deleted: " + String.join(", ", preview.blockers()));
        }
        planFeatureRepository.deleteAll(planFeatureRepository.findAllByPlanId(planId));
        try {
            planRepository.delete(plan);
            planRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidStateException(
                    "Plan gained a retained reference during deletion; request a fresh preview.");
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
    @PermissionNode(key = "assign_feature", description = "Assign a feature to a plan")
    public PlanFeatureDto assignFeature(UUID planId, AssignPlanFeatureRequest request) {
        var plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        requireMutable(plan);
        return readModels.toDto(addFeature(plan, request));
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
    @PermissionNode(key = "update_feature", description = "Update a plan's feature quota/price config")
    public PlanFeatureDto updateFeature(UUID planId, UUID planFeatureId, AssignPlanFeatureRequest request) {
        var pf = planFeatureRepository.findById(planFeatureId)
                .orElseThrow(() -> new ResourceNotFoundException("PlanFeature", "id", planFeatureId));
        if (!pf.getPlan().getId().equals(planId)) {
            throw new ResourceNotFoundException("PlanFeature", "id", planFeatureId);
        }
        if (!pf.getFeature().getCode().equals(request.featureCode())) {
            throw new InvalidRequestException("A plan feature update cannot change its feature code.");
        }
        requireMutable(pf.getPlan());
        var quotaEntries = request.quotaEntries();
        billingConfigurationValidator.validatePlanFeature(
                request.featureCode(), request.mode(), quotaEntries, pf.getPlan().getCurrencyCode());
        pf.setMode(request.mode());
        pf.setQuotaConfigs(new ArrayList<>(quotaEntries));
        return readModels.toDto(planFeatureRepository.save(pf));
    }

    @Override
    @Transactional
    @PermissionNode(key = "remove_feature", description = "Remove a feature from a plan")
    public void removeFeature(UUID planId, UUID planFeatureId) {
        var pf = planFeatureRepository.findById(planFeatureId)
                .orElseThrow(() -> new ResourceNotFoundException("PlanFeature", "id", planFeatureId));
        if (!pf.getPlan().getId().equals(planId)) {
            throw new ResourceNotFoundException("PlanFeature", "id", planFeatureId);
        }
        requireMutable(pf.getPlan());
        planFeatureRepository.delete(pf);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list_add_ons", description = "List commercial AddOns")
    public List<AddOnDto> listAddOns() {
        return addOnRepository.findAllByOrderByNameAscRevisionNumberDesc().stream().map(readModels::toDto).toList();
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
    @PermissionNode(key = "create_add_on", description = "Create a commercial AddOn draft")
    public AddOnDto createAddOn(CreateAddOnRequest request) {
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
        return readModels.toDto(addOnRepository.save(addOn));
    }

    @Override
    @Transactional
    @PermissionNode(key = "revise_add_on", description = "Create the next draft revision of a published AddOn")
    public AddOnDto reviseAddOn(UUID sourceAddOnId) {
        AddOn source = addOnRepository.findByIdForUpdate(sourceAddOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", sourceAddOnId));
        if (source.getStatus() == AddOnStatus.DRAFT) {
            throw new BusinessException("An AddOn draft can be edited directly and cannot be revised.");
        }
        if (source.getStatus() == AddOnStatus.ARCHIVED) {
            throw new BusinessException("Archived AddOns are terminal and cannot be revised.");
        }

        addOnRepository.findLineageForUpdate(source.getLineageId()).stream()
                .filter(candidate -> candidate.getStatus() == AddOnStatus.DRAFT)
                .findFirst()
                .ifPresent(draft -> {
                    throw new BusinessException(
                            "AddOn revision R" + draft.getRevisionNumber() + " is already an editable draft.");
                });

        int nextRevision = addOnRepository.findMaximumRevisionNumber(source.getLineageId()) + 1;
        String code = CommercialCodeGenerator.generate(
                source.getName(), "ADD_ON", candidate -> addOnRepository.findByCode(candidate).isPresent());
        AddOn revision = new AddOn();
        revision.setCode(code);
        applyAddOnBasics(revision, source.getName(), source.getDescription(), source.getPrice(),
                source.getCurrencyCode(), source.getBillingCycle(), source.getAllowedPlanCodes(),
                source.getBlockedPlanCodes(), source.getDependencyCodes(), source.getExclusionCodes());
        revision.setStatus(AddOnStatus.DRAFT);
        revision.setLineageId(source.getLineageId());
        revision.setRevisionNumber(nextRevision);
        revision.setSourceAddOn(source);
        revision.setCreationReason(AddOnCreationReason.REVISED);
        revision.setSalesVisibility(source.getSalesVisibility());

        AddOn savedRevision;
        try {
            savedRevision = addOnRepository.saveAndFlush(revision);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("AddOn", "code or lineage revision", code);
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
        return readModels.toDto(savedRevision);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_add_on", description = "Update a commercial AddOn draft")
    public AddOnDto updateAddOn(UUID addOnId, UpdateAddOnRequest request) {
        AddOn addOn = requireEditableAddOn(addOnId);
        applyAddOnBasics(addOn, request.name(), request.description(), request.price(),
                request.currencyCode(), request.billingCycle(), request.allowedPlanCodes(),
                request.blockedPlanCodes(), request.dependencyCodes(), request.exclusionCodes());
        addOn.touchDefinition();
        addOnRepository.saveAndFlush(addOn);
        return readModels.toDto(addOnRepository.findDetailedById(addOnId).orElseThrow());
    }

    @Override
    @Transactional
    @PermissionNode(key = "transition_add_on", description = "Transition a commercial AddOn lifecycle state")
    public AddOnDto transitionAddOnStatus(UUID addOnId, AddOnStatus targetStatus) {
        AddOn addOn = requireAddOn(addOnId);
        if (targetStatus == null) {
            throw new InvalidRequestException("Target AddOn status is required.");
        }
        if (addOn.getStatus() == targetStatus) {
            return readModels.toDto(addOn);
        }
        if (addOn.getStatus() == AddOnStatus.ARCHIVED) {
            throw new BusinessException("Archived AddOns are terminal and cannot transition.");
        }
        if (targetStatus == AddOnStatus.DRAFT) {
            throw new BusinessException("An AddOn cannot transition back to DRAFT.");
        }
        if (addOn.getStatus() == AddOnStatus.DRAFT && targetStatus != AddOnStatus.ACTIVE) {
            throw new BusinessException("An AddOn draft must be published or deleted.");
        }
        if (targetStatus == AddOnStatus.ACTIVE) {
            // The compatibility default is part of the exact price book. Create it first so
            // activation validates ProductPrice tuples, not the mutable legacy projection.
            // A validation failure rolls the row back with this transaction.
            productPriceCompatibilityService.ensurePublishedDefault(addOn);
            validateAddOnActivation(addOn, productPriceResolver.availableCatalogPrices());
            List<AddOn> lineage = addOnRepository.findLineageForUpdate(addOn.getLineageId());
            List<AddOn> previouslyActive = lineage.stream()
                    .filter(candidate -> !candidate.getId().equals(addOn.getId()))
                    .filter(candidate -> candidate.getStatus() == AddOnStatus.ACTIVE)
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
    @Transactional
    @PermissionNode(key = "delete_add_on", description = "Delete an unused AddOn draft")
    public void deleteAddOn(UUID addOnId) {
        AddOn addOn = requireAddOn(addOnId);
        if (addOn.getStatus() != AddOnStatus.DRAFT) {
            throw new BusinessException("Only AddOn drafts may be deleted; deactivate or archive published AddOns.");
        }
        boolean referenced = addOnRepository.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(addOnId))
                .anyMatch(candidate -> candidate.getDependencyCodes().contains(addOn.getCode())
                        || candidate.getExclusionCodes().contains(addOn.getCode()));
        if (referenced) {
            throw new BusinessException(
                    "AddOn " + addOn.getCode() + " is referenced by another AddOn and cannot be deleted.");
        }
        boolean referencedByQuotaPackage = quotaPackageRepository.findAll().stream()
                .anyMatch(item -> item.getAllowedAddOnCodes().contains(addOn.getCode()));
        if (referencedByQuotaPackage) {
            throw new BusinessException(
                    "AddOn " + addOn.getCode() + " is referenced by a quota package and cannot be deleted.");
        }
        addOnFeatureRepository.deleteAll(addOnFeatureRepository.findAllByAddOnId(addOnId));
        addOnRepository.delete(addOn);
    }

    @Override
    @Transactional
    @PermissionNode(key = "assign_add_on_feature", description = "Assign a feature to an AddOn draft")
    public AddOnDto.FeatureItem assignAddOnFeature(UUID addOnId, AssignAddOnFeatureRequest request) {
        AddOn addOn = requireEditableAddOn(addOnId);
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
    @PermissionNode(key = "update_add_on_feature", description = "Update an AddOn feature configuration")
    public AddOnDto.FeatureItem updateAddOnFeature(
            UUID addOnId, UUID addOnFeatureId, AssignAddOnFeatureRequest request) {
        AddOn addOn = requireEditableAddOn(addOnId);
        AddOnFeature item = requireAddOnFeature(addOnId, addOnFeatureId);
        if (!item.getFeature().getCode().equals(request.featureCode())) {
            throw new InvalidRequestException("An AddOn feature update cannot change its feature code.");
        }
        var quotaEntries = request.quotaEntries();
        billingConfigurationValidator.validateAddOnFeature(
                request.featureCode(), quotaEntries, addOn.getCurrencyCode());
        item.setQuotaConfigs(new ArrayList<>(quotaEntries));
        addOn.touchDefinition();
        return readModels.toDto(addOnFeatureRepository.save(item));
    }

    @Override
    @Transactional
    @PermissionNode(key = "remove_add_on_feature", description = "Remove a feature from an AddOn draft")
    public void removeAddOnFeature(UUID addOnId, UUID addOnFeatureId) {
        AddOn addOn = requireEditableAddOn(addOnId);
        AddOnFeature item = requireAddOnFeature(addOnId, addOnFeatureId);
        addOnFeatureRepository.delete(item);
        addOn.touchDefinition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list_quota_packages", description = "List commercial quota packages")
    public List<QuotaPackageDto> listQuotaPackages() {
        return quotaPackageRepository.findAllByOrderByCodeAsc().stream().map(readModels::toDto).toList();
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
    @PermissionNode(key = "create_quota_package", description = "Create a commercial quota package draft")
    public QuotaPackageDto createQuotaPackage(CreateQuotaPackageRequest request) {
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
        return readModels.toDto(quotaPackageRepository.save(item));
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_quota_package", description = "Update a commercial quota package draft")
    public QuotaPackageDto updateQuotaPackage(UUID quotaPackageId, UpdateQuotaPackageRequest request) {
        QuotaPackage item = requireEditableQuotaPackage(quotaPackageId);
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
    @PermissionNode(key = "transition_quota_package", description = "Transition a quota package lifecycle state")
    public QuotaPackageDto transitionQuotaPackageStatus(UUID quotaPackageId, QuotaPackageStatus targetStatus) {
        QuotaPackage item = requireQuotaPackage(quotaPackageId);
        if (targetStatus == null) {
            throw new InvalidRequestException("Target quota package status is required.");
        }
        if (item.getStatus() == targetStatus) {
            return readModels.toDto(item);
        }
        if (item.getStatus() == QuotaPackageStatus.ARCHIVED) {
            throw new BusinessException("Archived quota packages are terminal and cannot transition.");
        }
        if (targetStatus == QuotaPackageStatus.DRAFT) {
            throw new BusinessException("A quota package cannot transition back to DRAFT.");
        }
        if (item.getStatus() == QuotaPackageStatus.DRAFT && targetStatus == QuotaPackageStatus.INACTIVE) {
            throw new BusinessException("A draft quota package must be activated or archived.");
        }
        if (targetStatus == QuotaPackageStatus.ACTIVE) {
            productPriceCompatibilityService.ensurePublishedDefault(item);
            validateQuotaPackageActivation(item, productPriceResolver.availableCatalogPrices());
        }
        item.setStatus(targetStatus);
        quotaPackageRepository.saveAndFlush(item);
        return readModels.toDto(quotaPackageRepository.findDetailedById(quotaPackageId).orElseThrow());
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete_quota_package", description = "Delete an unused quota package draft")
    public void deleteQuotaPackage(UUID quotaPackageId) {
        QuotaPackage item = requireQuotaPackage(quotaPackageId);
        if (item.getStatus() != QuotaPackageStatus.DRAFT) {
            throw new BusinessException(
                    "Only quota package drafts may be deleted; deactivate or archive published packages.");
        }
        quotaPackageRepository.delete(item);
    }

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

    private void validateActivation(Plan plan) {
        List<PlanFeature> features = planFeatureRepository.findAllByPlanId(plan.getId());
        if (features.stream().noneMatch(feature -> feature.getMode() == PlanFeatureMode.INCLUDED)) {
            throw new BusinessException("A Plan requires at least one included feature before activation.");
        }
        for (PlanFeature feature : features) {
            billingConfigurationValidator.validatePlanFeature(
                    feature.getFeature().getCode(), feature.getMode(),
                    feature.getQuotaConfigs(), plan.getCurrencyCode());
            if (feature.getMode() == PlanFeatureMode.INCLUDED) {
                billingConfigurationValidator.requireCompleteQuotaConfiguration(
                        feature.getFeature().getCode(), feature.getQuotaConfigs());
            }
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
        if (addOn.getStatus() != AddOnStatus.DRAFT) {
            throw new BusinessException(
                    "Published AddOns are immutable; create and publish a draft revision instead.");
        }
        return addOn;
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
        for (String planCode : plans) {
            if (planRepository.findByCode(planCode).isEmpty()) {
                throw new InvalidRequestException("Unknown Plan code in quota package availability: " + planCode);
            }
        }
        for (String addOnCode : addOns) {
            if (addOnRepository.findByCode(addOnCode).isEmpty()) {
                throw new InvalidRequestException("Unknown AddOn code in quota package availability: " + addOnCode);
            }
        }

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

    private void validateQuotaPackageActivation(
            QuotaPackage item,
            List<ProductPrice> catalogPrices
    ) {
        // Empty targeting is the OPEN_COMPATIBLE default: the central catalogue resolver still
        // requires an exact already-entitled finite quota before the package can be selected.
        // Non-empty Plan/AddOn targets only narrow that mandatory compatibility set.
        Set<CommercialCatalogResolver.PriceTuple> packageTuples = priceTuples(
                catalogPrices, ProductPriceOwnerType.QUOTA_PACKAGE, item.getId());
        if (packageTuples.isEmpty()) {
            throw new BusinessException("A quota package requires at least one active price before activation.");
        }
        for (String planCode : item.getAllowedPlanCodes()) {
            Plan plan = planRepository.findByCode(planCode)
                    .orElseThrow(() -> new BusinessException("Allowed Plan no longer exists: " + planCode));
            if (!plan.isActive() || java.util.Collections.disjoint(
                    packageTuples, priceTuples(catalogPrices, ProductPriceOwnerType.PLAN, plan.getId()))) {
                throw new BusinessException(
                        "Plan " + planCode + " is not active or has no compatible active price tuple.");
            }
            PlanFeature owner = planFeatureRepository
                    .findByPlanIdAndFeature_Code(plan.getId(), item.getFeature().getCode())
                    .orElseThrow(() -> new BusinessException(
                            "Plan " + planCode + " does not own quota "
                                    + item.getFeature().getCode() + "." + item.getResource() + "."));
            if (owner.getMode() != PlanFeatureMode.INCLUDED
                    || !hasFiniteQuota(owner.getQuotaConfigs(), item.getResource())) {
                throw new BusinessException(
                        "Plan " + planCode + " must include a finite base quota for "
                                + item.getFeature().getCode() + "." + item.getResource() + ".");
            }
        }
        for (String addOnCode : item.getAllowedAddOnCodes()) {
            AddOn addOn = addOnRepository.findByCode(addOnCode)
                    .orElseThrow(() -> new BusinessException("Allowed AddOn no longer exists: " + addOnCode));
            Set<CommercialCatalogResolver.PriceTuple> addOnTuples = compatibleAddOnPriceTuples(
                    addOn, catalogPrices, new LinkedHashSet<>());
            if (!addOn.isActive() || java.util.Collections.disjoint(packageTuples, addOnTuples)) {
                throw new BusinessException(
                        "AddOn " + addOnCode + " is not active or has no compatible active price tuple.");
            }
            AddOnFeature owner = addOnFeatureRepository
                    .findByAddOnIdAndFeature_Code(addOn.getId(), item.getFeature().getCode())
                    .orElseThrow(() -> new BusinessException(
                            "AddOn " + addOnCode + " does not own quota "
                                    + item.getFeature().getCode() + "." + item.getResource() + "."));
            if (!hasFiniteQuota(owner.getQuotaConfigs(), item.getResource())) {
                throw new BusinessException(
                        "AddOn " + addOnCode + " must include a finite base quota for "
                                + item.getFeature().getCode() + "." + item.getResource() + ".");
            }
        }
    }

    private boolean hasFiniteQuota(List<com.hiveapp.shared.quota.QuotaLimitEntry> quotas, String resource) {
        return quotas != null && quotas.stream()
                .anyMatch(quota -> quota.resource().equals(resource)
                        && quota.mode() == QuotaLimitMode.FINITE);
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
        for (String planCode : union(allowed, blocked)) {
            if (planRepository.findByCode(planCode).isEmpty()) {
                throw new InvalidRequestException("Unknown Plan code in AddOn availability: " + planCode);
            }
        }
        for (String relatedCode : union(dependencies, exclusions)) {
            if (relatedCode.equals(addOn.getCode())) {
                throw new InvalidRequestException("An AddOn cannot depend on or exclude itself.");
            }
            if (addOnRepository.findByCode(relatedCode).isEmpty()) {
                throw new InvalidRequestException("Unknown related AddOn code: " + relatedCode);
            }
        }

        addOn.setName(name);
        addOn.setDescription(description);
        addOn.setMoney(money);
        addOn.setBillingCycle(billingCycle);
        addOn.setAllowedPlanCodes(allowed);
        addOn.setBlockedPlanCodes(blocked);
        addOn.setDependencyCodes(dependencies);
        addOn.setExclusionCodes(exclusions);
    }

    private void validateAddOnActivation(AddOn addOn, List<ProductPrice> catalogPrices) {
        List<AddOnFeature> features = addOnFeatureRepository.findAllByAddOnId(addOn.getId());
        if (features.isEmpty()) {
            throw new BusinessException("An AddOn requires at least one feature before activation.");
        }
        for (AddOnFeature feature : features) {
            billingConfigurationValidator.validateAddOnFeature(
                    feature.getFeature().getCode(), feature.getQuotaConfigs(), addOn.getCurrencyCode());
        }
        if (addOn.getAllowedPlanCodes().isEmpty()) {
            List<Plan> candidates = planRepository.findAll().stream()
                    .filter(Plan::isActive)
                    .filter(plan -> !addOn.getBlockedPlanCodes().contains(plan.getCode()))
                    .toList();
            boolean compatible = candidates.stream().anyMatch(plan -> {
                try {
                    requireAddOnAvailableOnPlan(addOn, plan, catalogPrices, new LinkedHashSet<>(), true);
                    return true;
                } catch (BusinessException ignored) {
                    return false;
                }
            });
            if (!compatible) {
                throw new BusinessException(
                        "An AddOn must be available to at least one active Plan with a shared active price tuple.");
            }
            return;
        }
        for (String planCode : addOn.getAllowedPlanCodes()) {
            Plan plan = planRepository.findByCode(planCode)
                    .orElseThrow(() -> new BusinessException("Allowed Plan no longer exists: " + planCode));
            requireAddOnAvailableOnPlan(addOn, plan, catalogPrices, new LinkedHashSet<>(), true);
        }
    }

    private Set<CommercialCatalogResolver.PriceTuple> requireAddOnAvailableOnPlan(
            AddOn addOn,
            Plan plan,
            List<ProductPrice> catalogPrices,
            Set<String> visited,
            boolean root
    ) {
        if (!visited.add(addOn.getCode())) {
            throw new BusinessException("Cyclic AddOn dependency detected at " + addOn.getCode() + ".");
        }
        boolean allowed = addOn.getAllowedPlanCodes().isEmpty()
                || addOn.getAllowedPlanCodes().contains(plan.getCode());
        if (!plan.isActive() || (!root && !addOn.isActive()) || !allowed
                || addOn.getBlockedPlanCodes().contains(plan.getCode())) {
            throw new BusinessException("AddOn " + addOn.getCode()
                    + " is not available for Plan " + plan.getCode() + ".");
        }
        requirePlanSupportsAddOn(plan, addOnFeatureRepository.findAllByAddOnId(addOn.getId()));
        Set<CommercialCatalogResolver.PriceTuple> supported = new LinkedHashSet<>(priceTuples(
                catalogPrices, ProductPriceOwnerType.ADD_ON, addOn.getId()));
        supported.retainAll(priceTuples(catalogPrices, ProductPriceOwnerType.PLAN, plan.getId()));
        for (String dependencyCode : addOn.getDependencyCodes()) {
            AddOn dependency = addOnRepository.findByCode(dependencyCode)
                    .orElseThrow(() -> new BusinessException("Missing AddOn dependency " + dependencyCode + "."));
            supported.retainAll(requireAddOnAvailableOnPlan(
                    dependency, plan, catalogPrices, visited, false));
        }
        visited.remove(addOn.getCode());
        if (supported.isEmpty()) {
            throw new BusinessException("AddOn " + addOn.getCode() + " and Plan " + plan.getCode()
                    + " have no shared active price tuple across their dependency closure.");
        }
        return Set.copyOf(supported);
    }

    private Set<CommercialCatalogResolver.PriceTuple> compatibleAddOnPriceTuples(
            AddOn addOn,
            List<ProductPrice> catalogPrices,
            Set<String> visited
    ) {
        if (!visited.add(addOn.getCode())) {
            throw new BusinessException("Cyclic AddOn dependency detected at " + addOn.getCode() + ".");
        }
        if (!addOn.isActive()) {
            throw new BusinessException("AddOn dependency " + addOn.getCode() + " must be ACTIVE.");
        }
        Set<CommercialCatalogResolver.PriceTuple> supported = new LinkedHashSet<>(priceTuples(
                catalogPrices, ProductPriceOwnerType.ADD_ON, addOn.getId()));
        for (String dependencyCode : addOn.getDependencyCodes()) {
            AddOn dependency = addOnRepository.findByCode(dependencyCode)
                    .orElseThrow(() -> new BusinessException("Missing AddOn dependency " + dependencyCode + "."));
            supported.retainAll(compatibleAddOnPriceTuples(dependency, catalogPrices, visited));
        }
        visited.remove(addOn.getCode());
        return Set.copyOf(supported);
    }

    private Set<CommercialCatalogResolver.PriceTuple> priceTuples(
            List<ProductPrice> catalogPrices,
            ProductPriceOwnerType ownerType,
            UUID ownerId
    ) {
        return catalogPrices.stream()
                .filter(price -> price.getOwnerType() == ownerType && ownerId.equals(price.ownerId()))
                .map(CommercialCatalogResolver.PriceTuple::from)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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

    private Set<String> union(Set<String> left, Set<String> right) {
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return union;
    }

    private void requireCommercialAvailabilityPermission(String node, String operation) {
        String permissionCode = CommercialAvailabilityFeature.CODE + "." + node;
        if (!adminMutationAuthorizer.currentActorGrantCeiling().allows(permissionCode)) {
            throw new ForbiddenException(operation + " requires " + permissionCode + ".");
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

    private PlanDeletionPreview buildDeletionPreview(Plan plan) {
        UUID planId = plan.getId();
        int ownedFeatureCount = planFeatureRepository.findAllByPlanId(planId).size();
        long subscriptionHistory = subscriptionRepository.countByPlan_Id(planId);
        long changeOperationReferences = subscriptionChangeOperationRepository.countByTargetPlan_Id(planId);
        long addOnReferences = addOnRepository.findAll().stream()
                .filter(addOn -> addOn.getAllowedPlanCodes().contains(plan.getCode())
                        || addOn.getBlockedPlanCodes().contains(plan.getCode()))
                .count();
        long quotaPackageReferences = quotaPackageRepository.findAll().stream()
                .filter(item -> item.getAllowedPlanCodes().contains(plan.getCode()))
                .count();
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
                Long.toString(lineageReferences));
        return new PlanDeletionPreview(
                planId,
                plan.getName(),
                plan.getVersion(),
                sha256(state),
                blockers.isEmpty(),
                ownedFeatureCount,
                subscriptionHistory,
                changeOperationReferences,
                addOnReferences,
                quotaPackageReferences,
                lineageReferences,
                List.copyOf(blockers));
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
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
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
