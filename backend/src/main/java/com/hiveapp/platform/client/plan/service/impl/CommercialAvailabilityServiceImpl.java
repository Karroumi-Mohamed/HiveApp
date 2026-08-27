package com.hiveapp.platform.client.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAvailabilityAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAvailabilityBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionResolutionSource;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialAvailabilityAuditRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
import com.hiveapp.platform.client.plan.dto.CommercialAvailabilityHistoryEntryDto;
import com.hiveapp.platform.client.plan.dto.ExtensionAvailabilityIssue;
import com.hiveapp.platform.client.plan.dto.ExtensionCompatibilityDto;
import com.hiveapp.platform.client.plan.dto.PlanAvailabilityMutationRequest;
import com.hiveapp.platform.client.plan.dto.PlanAvailabilityPreviewDto;
import com.hiveapp.platform.client.plan.dto.PlanAvailabilityPreviewRequest;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.ProductVisibilityMutationRequest;
import com.hiveapp.platform.client.plan.dto.ProductVisibilityPreviewDto;
import com.hiveapp.platform.client.plan.dto.ProductVisibilityPreviewRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import com.hiveapp.platform.client.plan.service.CommercialAvailabilityService;
import com.hiveapp.platform.client.plan.service.CommercialAvailabilityAuditContract;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.service.PlanAdminReadModels;
import com.hiveapp.platform.registry.definition.CommercialAvailabilityFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.OperationBlockedException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
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
@PermissionNode(key = CommercialAvailabilityFeature.KEY,
        description = "Commercial availability and compatibility management",
        guard = PermissionNode.Guard.ON)
public class CommercialAvailabilityServiceImpl extends PlatformControlFeatureService
        implements CommercialAvailabilityService {

    private static final int MAX_PREVIEW_CHANGES = 100;
    private static final Set<String> DETAILED_SUCCESS_ACTIONS = Set.of(
            CommercialAvailabilityAuditContract.PLAN_CHANGE_ACTION,
            CommercialAvailabilityAuditContract.ADD_ON_CHANGE_ACTION,
            CommercialAvailabilityAuditContract.QUOTA_CHANGE_ACTION);
    private static final Set<String> COMPATIBILITY_SORTS = Set.of(
            "code", "name", "productType", "available");

    private final CommercialCatalogResolver catalogResolver;
    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanAdminReadModels readModels;
    private final CommercialAvailabilityAuditRepository availabilityAuditRepository;
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;
    private final AuditTrail auditTrail;
    private final AdminMutationAuthorizer adminMutationAuthorizer;
    private final CommercialCatalogVersionService commercialCatalogVersionService;
    private final RegistryCatalogVersionService registryCatalogVersionService;
    private final CommercialPreviewTokenService previewTokenService;
    private final Clock clock;

    @Override
    protected FeatureDefinition featureDefinition() {
        return CommercialAvailabilityFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "inspect_compatibility", description = "Inspect reasoned extension compatibility")
    public Page<ExtensionCompatibilityDto> inspect(
            UUID planId,
            String search,
            CommercialProductType type,
            Boolean available,
            String currencyCode,
            BillingCycle billingCycle,
            Pageable pageable
    ) {
        CommercialCatalogResolver.PriceTuple tuple = priceTuple(currencyCode, billingCycle);
        CommercialCatalogResolver.PlanResolution resolution = catalogResolver.resolvePlan(
                planId, CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR, tuple);
        Pageable bounded = compatibilityPage(pageable);
        List<ExtensionCompatibilityDto> items = extensionDtos(resolution).stream()
                .filter(item -> type == null || item.productType() == type)
                .filter(item -> available == null || item.operatorSelectable() == available)
                .filter(item -> matches(item, search))
                .sorted(compatibilityComparator(bounded.getSort()))
                .toList();
        int from = bounded.getOffset() >= items.size()
                ? items.size() : (int) bounded.getOffset();
        int to = Math.min(from + bounded.getPageSize(), items.size());
        return new PageImpl<>(items.subList(from, to), bounded, items.size());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_plan_policy", description = "Preview a Plan availability change")
    public PlanAvailabilityPreviewDto previewPlan(
            UUID planId, PlanAvailabilityPreviewRequest request) {
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        String registryVersion = registryCatalogVersionService.currentVersion();
        return commercialCatalogVersionService.readConsistently(catalogRevision -> {
            Plan plan = requirePlan(planId);
            PlanAvailabilityAssessment assessment = assessPlanAvailability(
                    plan, request.extensionPolicy(), request.salesVisibility());
            Instant evaluatedAt = clock.instant();
            var evidence = previewTokenService.issue(
                    CommercialPreviewKind.PLAN_AVAILABILITY, plan.getId(), plan.getVersion(),
                    actorUserId, catalogRevision, registryVersion,
                    assessment.fingerprint(), evaluatedAt);
            return assessment.toDto(catalogRevision, registryVersion, evidence);
        });
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_plan_policy", description = "Apply a previewed Plan availability change")
    public PlanDto updatePlan(UUID planId, PlanAvailabilityMutationRequest request) {
        Plan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        var previousPolicy = plan.getExtensionPolicy();
        var previousVisibility = plan.getSalesVisibility();
        requireVersion(plan.getVersion(), request.expectedVersion(), "Plan availability");
        requireReason(request.reason());
        long catalogRevision = commercialCatalogVersionService.currentRevision();
        String registryVersion = registryCatalogVersionService.currentVersion();
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        PlanAvailabilityAssessment assessment = assessPlanAvailability(
                plan, request.extensionPolicy(), request.salesVisibility());
        previewTokenService.requireValid(
                request.previewToken(), CommercialPreviewKind.PLAN_AVAILABILITY,
                plan.getId(), plan.getVersion(), actorUserId, catalogRevision,
                registryVersion, assessment.fingerprint(), this::staleAvailabilityPreview);
        requireApplicable(assessment.applicable(), assessment.blockers());
        plan.setExtensionPolicy(request.extensionPolicy());
        plan.setSalesVisibility(request.salesVisibility());
        PlanDto result = readModels.toDto(planRepository.saveAndFlush(plan));
        recordAvailabilityChange(
                CommercialAvailabilityAuditContract.PLAN_CHANGE_ACTION,
                planId, CommercialProductType.PLAN, result.code(),
                previousPolicy, result.extensionPolicy(), previousVisibility,
                result.salesVisibility(), request.reason());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_add_on_visibility", description = "Preview AddOn sales visibility")
    public ProductVisibilityPreviewDto previewAddOn(
            UUID addOnId, ProductVisibilityPreviewRequest request) {
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        String registryVersion = registryCatalogVersionService.currentVersion();
        return commercialCatalogVersionService.readConsistently(catalogRevision -> {
            AddOn addOn = requireAddOn(addOnId);
            ProductVisibilityAssessment assessment = assessAddOnVisibility(
                    addOn, request.salesVisibility());
            Instant evaluatedAt = clock.instant();
            var evidence = previewTokenService.issue(
                    CommercialPreviewKind.ADD_ON_VISIBILITY, addOn.getId(), addOn.getRowVersion(),
                    actorUserId, catalogRevision, registryVersion,
                    assessment.fingerprint(), evaluatedAt);
            return assessment.toDto(catalogRevision, registryVersion, evidence);
        });
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_add_on_visibility", description = "Change AddOn sales visibility")
    public AddOnDto updateAddOn(UUID addOnId, ProductVisibilityMutationRequest request) {
        AddOn addOn = addOnRepository.findByIdForUpdate(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
        var previousVisibility = addOn.getSalesVisibility();
        requireVersion(addOn.getRowVersion(), request.expectedVersion(), "AddOn visibility");
        requireReason(request.reason());
        long catalogRevision = commercialCatalogVersionService.currentRevision();
        String registryVersion = registryCatalogVersionService.currentVersion();
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        ProductVisibilityAssessment assessment = assessAddOnVisibility(
                addOn, request.salesVisibility());
        previewTokenService.requireValid(
                request.previewToken(), CommercialPreviewKind.ADD_ON_VISIBILITY,
                addOn.getId(), addOn.getRowVersion(), actorUserId, catalogRevision,
                registryVersion, assessment.fingerprint(), this::staleAvailabilityPreview);
        requireApplicable(assessment.applicable(), assessment.blockers());
        addOn.setSalesVisibility(request.salesVisibility());
        addOnRepository.saveAndFlush(addOn);
        AddOnDto result = readModels.toDto(addOnRepository.findDetailedById(addOnId).orElseThrow());
        recordAvailabilityChange(
                CommercialAvailabilityAuditContract.ADD_ON_CHANGE_ACTION,
                addOnId, CommercialProductType.ADD_ON, result.code(),
                null, null, previousVisibility, result.salesVisibility(), request.reason());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_quota_visibility", description = "Preview quota-package sales visibility")
    public ProductVisibilityPreviewDto previewQuotaPackage(
            UUID quotaPackageId, ProductVisibilityPreviewRequest request) {
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        String registryVersion = registryCatalogVersionService.currentVersion();
        return commercialCatalogVersionService.readConsistently(catalogRevision -> {
            QuotaPackage item = requireQuotaPackage(quotaPackageId);
            ProductVisibilityAssessment assessment = assessQuotaVisibility(
                    item, request.salesVisibility());
            Instant evaluatedAt = clock.instant();
            var evidence = previewTokenService.issue(
                    CommercialPreviewKind.QUOTA_PACKAGE_VISIBILITY,
                    item.getId(), item.getRowVersion(), actorUserId, catalogRevision,
                    registryVersion, assessment.fingerprint(), evaluatedAt);
            return assessment.toDto(catalogRevision, registryVersion, evidence);
        });
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_quota_visibility", description = "Change quota-package sales visibility")
    public QuotaPackageDto updateQuotaPackage(
            UUID quotaPackageId, ProductVisibilityMutationRequest request) {
        QuotaPackage item = quotaPackageRepository.findByIdForUpdate(quotaPackageId)
                .orElseThrow(() -> new ResourceNotFoundException("QuotaPackage", "id", quotaPackageId));
        var previousVisibility = item.getSalesVisibility();
        requireVersion(item.getRowVersion(), request.expectedVersion(), "Quota-package visibility");
        requireReason(request.reason());
        long catalogRevision = commercialCatalogVersionService.currentRevision();
        String registryVersion = registryCatalogVersionService.currentVersion();
        UUID actorUserId = adminMutationAuthorizer.currentActorUserId();
        ProductVisibilityAssessment assessment = assessQuotaVisibility(
                item, request.salesVisibility());
        previewTokenService.requireValid(
                request.previewToken(), CommercialPreviewKind.QUOTA_PACKAGE_VISIBILITY,
                item.getId(), item.getRowVersion(), actorUserId, catalogRevision,
                registryVersion, assessment.fingerprint(), this::staleAvailabilityPreview);
        requireApplicable(assessment.applicable(), assessment.blockers());
        item.setSalesVisibility(request.salesVisibility());
        quotaPackageRepository.saveAndFlush(item);
        QuotaPackageDto result = readModels.toDto(
                quotaPackageRepository.findDetailedById(quotaPackageId).orElseThrow());
        recordAvailabilityChange(
                CommercialAvailabilityAuditContract.QUOTA_CHANGE_ACTION,
                quotaPackageId, CommercialProductType.QUOTA_PACKAGE,
                result.code(), null, null, previousVisibility, result.salesVisibility(), request.reason());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_history", description = "Read commercial availability history")
    public Page<CommercialAvailabilityHistoryEntryDto> history(UUID productId, Pageable pageable) {
        Pageable bounded = historyPage(pageable);
        Page<AuditLog> logs = availabilityAuditRepository.findHistory(
                CommercialAvailabilityAuditContract.RESOURCE_TYPE, productId.toString(),
                AuditOutcome.SUCCEEDED, DETAILED_SUCCESS_ACTIONS, bounded);
        Set<UUID> actorIds = logs.stream().map(AuditLog::getActorUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> actorEmails = actorIds.isEmpty() ? Map.of()
                : adminUserRepository.findAllWithUserByUserIdIn(actorIds).stream()
                        .collect(Collectors.toMap(
                                admin -> admin.getUser().getId(), admin -> admin.getUser().getEmail()));
        ProductIdentity productIdentity = historyProductIdentity(productId, logs);
        return logs.map(log -> historyEntry(
                log, actorEmails.get(log.getActorUserId()), productIdentity));
    }

    private PlanAvailabilityAssessment assessPlanAvailability(
            Plan plan,
            PlanExtensionPolicy targetPolicy,
            ProductSalesVisibility targetVisibility
    ) {
        CommercialCatalogResolver.PlanResolution before = catalogResolver.resolvePlan(
                plan.getId(), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR, null);
        CommercialCatalogResolver.PlanResolution after = catalogResolver.previewPlan(
                plan.getId(), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR, null,
                targetPolicy, targetVisibility);
        Map<String, ExtensionCompatibilityDto> beforeByKey = extensionDtos(before).stream()
                .collect(Collectors.toMap(this::key, Function.identity()));
        List<ExtensionCompatibilityDto> afterItems = extensionDtos(after);
        List<ExtensionCompatibilityDto> changed = afterItems.stream()
                .filter(item -> !equivalent(beforeByKey.get(key(item)), item))
                .toList();
        int total = afterItems.size();
        int beforeOperator = (int) beforeByKey.values().stream()
                .filter(ExtensionCompatibilityDto::operatorSelectable).count();
        int afterOperator = (int) afterItems.stream()
                .filter(ExtensionCompatibilityDto::operatorSelectable).count();
        int beforeClient = before.clientVisible()
                ? (int) beforeByKey.values().stream().filter(ExtensionCompatibilityDto::clientCatalogVisible).count()
                : 0;
        int afterClient = targetVisibility == ProductSalesVisibility.PUBLIC && after.selectable()
                ? (int) afterItems.stream().filter(ExtensionCompatibilityDto::clientCatalogVisible).count()
                : 0;
        long subscribers = subscriptionRepository.countByPlan_IdAndStatusIn(
                plan.getId(), List.of(com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus.ACTIVE,
                        com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus.TRIALING,
                        com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus.PAST_DUE,
                        com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus.SUSPENDED));
        String state = String.join("|", plan.getId().toString(), Long.toString(plan.getVersion()),
                targetPolicy.name(), targetVisibility.name(), Long.toString(subscribers),
                resolutionFingerprint(before), resolutionFingerprint(after));
        List<CommercialAvailabilityBlocker> blockers = new ArrayList<>();
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            blockers.add(CommercialAvailabilityBlocker.ARCHIVED_PRODUCT);
        }
        if (plan.getExtensionPolicy() == targetPolicy
                && plan.getSalesVisibility() == targetVisibility) {
            blockers.add(CommercialAvailabilityBlocker.NO_CHANGE);
        }
        boolean applicable = blockers.isEmpty();
        return new PlanAvailabilityAssessment(
                plan.getId(), plan.getCode(), plan.getVersion(), plan.getExtensionPolicy(),
                targetPolicy, plan.getSalesVisibility(), targetVisibility, subscribers, total,
                beforeOperator, afterOperator, beforeClient, afterClient, changed.size(),
                changed.size() > MAX_PREVIEW_CHANGES,
                changed.stream().limit(MAX_PREVIEW_CHANGES).toList(), applicable,
                blockers, applicable
                        ? Set.of(CommercialAvailabilityAction.APPLY_PLAN_AVAILABILITY) : Set.of(),
                sha256(state));
    }

    private ProductVisibilityAssessment assessAddOnVisibility(
            AddOn addOn, ProductSalesVisibility target) {
        var catalog = catalogResolver.resolveCatalog(
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        int compatible = 0;
        int before = 0;
        int compatiblePublicPlans = 0;
        for (var plan : catalog.plans()) {
            var item = plan.addOns().stream().filter(candidate -> candidate.code().equals(addOn.getCode()))
                    .findFirst().orElse(null);
            if (item != null && item.selectable() && plan.selectable()) {
                compatible++;
                if (plan.plan().getSalesVisibility() == ProductSalesVisibility.PUBLIC) {
                    compatiblePublicPlans++;
                    if (addOn.getSalesVisibility() == ProductSalesVisibility.PUBLIC) before++;
                }
            }
        }
        int after = target == ProductSalesVisibility.PUBLIC ? compatiblePublicPlans : 0;
        List<CommercialAvailabilityBlocker> blockers = visibilityBlockers(
                addOn.getStatus() == AddOnStatus.ARCHIVED,
                addOn.getSalesVisibility(), target);
        boolean applicable = blockers.isEmpty();
        String fingerprint = visibilityFingerprint(
                CommercialProductType.ADD_ON, addOn.getId(), addOn.getRowVersion(),
                target, catalogFingerprint(catalog));
        return new ProductVisibilityAssessment(
                CommercialProductType.ADD_ON, addOn.getId(), addOn.getCode(), addOn.getRowVersion(),
                addOn.getSalesVisibility(), target, compatible, before, after, applicable, blockers,
                applicable ? Set.of(CommercialAvailabilityAction.APPLY_SALES_VISIBILITY) : Set.of(),
                fingerprint);
    }

    private ProductVisibilityAssessment assessQuotaVisibility(
            QuotaPackage item, ProductSalesVisibility target) {
        var catalog = catalogResolver.resolveCatalog(
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        int compatible = 0;
        int before = 0;
        int compatiblePublicPlans = 0;
        for (var plan : catalog.plans()) {
            var candidate = plan.quotaPackages().stream()
                    .filter(result -> result.code().equals(item.getCode())).findFirst().orElse(null);
            if (candidate != null && candidate.selectable() && plan.selectable()) {
                compatible++;
                if (plan.plan().getSalesVisibility() == ProductSalesVisibility.PUBLIC) {
                    compatiblePublicPlans++;
                    if (item.getSalesVisibility() == ProductSalesVisibility.PUBLIC) before++;
                }
            }
        }
        int after = target == ProductSalesVisibility.PUBLIC ? compatiblePublicPlans : 0;
        List<CommercialAvailabilityBlocker> blockers = visibilityBlockers(
                item.getStatus() == QuotaPackageStatus.ARCHIVED,
                item.getSalesVisibility(), target);
        boolean applicable = blockers.isEmpty();
        String fingerprint = visibilityFingerprint(
                CommercialProductType.QUOTA_PACKAGE, item.getId(), item.getRowVersion(),
                target, catalogFingerprint(catalog));
        return new ProductVisibilityAssessment(
                CommercialProductType.QUOTA_PACKAGE, item.getId(), item.getCode(), item.getRowVersion(),
                item.getSalesVisibility(), target, compatible, before, after, applicable, blockers,
                applicable ? Set.of(CommercialAvailabilityAction.APPLY_SALES_VISIBILITY) : Set.of(),
                fingerprint);
    }

    private String visibilityFingerprint(
            CommercialProductType type, UUID id, long version, ProductSalesVisibility target,
            String catalogFingerprint) {
        return sha256(String.join("|", type.name(), id.toString(), Long.toString(version),
                target.name(), catalogFingerprint));
    }

    private List<ExtensionCompatibilityDto> extensionDtos(
            CommercialCatalogResolver.PlanResolution resolution) {
        List<ExtensionCompatibilityDto> items = new ArrayList<>();
        resolution.addOns().forEach(result -> {
            boolean operatorSelectable = resolution.selectable() && result.selectable();
            boolean clientVisible = resolution.clientVisible() && result.clientVisible();
            List<ExtensionAvailabilityIssue> issues = combinedIssues(
                    resolution.issues(), result.issues(), result.addOn().getSalesVisibility(),
                    result.addOn().getCode());
            items.add(new ExtensionCompatibilityDto(
                    CommercialProductType.ADD_ON, result.addOn().getId(), result.addOn().getCode(),
                    result.addOn().getName(), result.addOn().getSalesVisibility(), operatorSelectable,
                    clientVisible, issues, result.prices().size(),
                    operatorSelectable && result.addOn().getDependencyCodes().isEmpty(),
                    operatorSelectable ? result.addOn().getDependencyCodes() : Set.of(),
                    result.addOn().getFeatures().stream()
                            .map(feature -> feature.getFeature().getCode())
                            .collect(Collectors.toCollection(java.util.TreeSet::new)),
                    null, null, null, null, null));
        });
        resolution.quotaPackages().forEach(result -> {
            boolean operatorSelectable = resolution.selectable() && result.selectable();
            boolean clientVisible = resolution.clientVisible() && result.clientVisible();
            List<ExtensionAvailabilityIssue> issues = combinedIssues(
                    resolution.issues(), result.issues(), result.quotaPackage().getSalesVisibility(),
                    result.quotaPackage().getCode());
            items.add(new ExtensionCompatibilityDto(
                    CommercialProductType.QUOTA_PACKAGE, result.quotaPackage().getId(),
                    result.quotaPackage().getCode(), result.quotaPackage().getName(),
                    result.quotaPackage().getSalesVisibility(), operatorSelectable, clientVisible,
                    issues, result.prices().size(),
                    operatorSelectable && result.directlySelectable(),
                    operatorSelectable ? result.requiredAddOnCodes() : Set.of(),
                    Set.of(result.quotaPackage().getFeature().getCode()),
                    result.quotaPackage().getFeature().getCode(),
                    result.quotaPackage().getResource(),
                    result.quotaPackage().getCapacityPerUnit(),
                    result.quotaPackage().isRepeatable(),
                    result.quotaPackage().getMaximumQuantity()));
        });
        return List.copyOf(items);
    }

    private List<ExtensionAvailabilityIssue> combinedIssues(
            List<ExtensionAvailabilityIssue> planIssues,
            List<ExtensionAvailabilityIssue> productIssues,
            ProductSalesVisibility visibility,
            String code
    ) {
        List<ExtensionAvailabilityIssue> combined = new ArrayList<>(planIssues);
        combined.addAll(productIssues);
        return operatorIssues(List.copyOf(new LinkedHashSet<>(combined)), visibility, code);
    }

    private List<CommercialAvailabilityBlocker> visibilityBlockers(
            boolean archived,
            ProductSalesVisibility current,
            ProductSalesVisibility target
    ) {
        List<CommercialAvailabilityBlocker> blockers = new ArrayList<>();
        if (archived) blockers.add(CommercialAvailabilityBlocker.ARCHIVED_PRODUCT);
        if (current == target) blockers.add(CommercialAvailabilityBlocker.NO_CHANGE);
        return List.copyOf(blockers);
    }

    private void requireApplicable(boolean applicable, List<CommercialAvailabilityBlocker> blockers) {
        if (applicable) return;
        throw new OperationBlockedException(
                "Commercial availability cannot be changed.",
                blockers.stream().map(Enum::name).toList());
    }

    private StaleResourceVersionException staleAvailabilityPreview() {
        return new StaleResourceVersionException(
                "Commercial availability preview is stale. Reload the preview and retry.");
    }

    private List<ExtensionAvailabilityIssue> operatorIssues(
            List<ExtensionAvailabilityIssue> source,
            ProductSalesVisibility visibility,
            String code
    ) {
        if (visibility != ProductSalesVisibility.DIRECT_ONLY) return source;
        List<ExtensionAvailabilityIssue> issues = new ArrayList<>(source);
        issues.add(new ExtensionAvailabilityIssue(
                ExtensionAvailabilityReason.DIRECT_ONLY,
                ExtensionResolutionSource.PRODUCT_VISIBILITY,
                code));
        return List.copyOf(new LinkedHashSet<>(issues));
    }

    private CommercialCatalogResolver.PriceTuple priceTuple(String currencyCode, BillingCycle cycle) {
        boolean hasCurrency = currencyCode != null && !currencyCode.isBlank();
        if (hasCurrency != (cycle != null)) {
            throw new InvalidRequestException(
                    "currencyCode and billingCycle must be supplied together for compatibility inspection.");
        }
        if (!hasCurrency) return null;
        try {
            return new CommercialCatalogResolver.PriceTuple(currencyCode, cycle);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(exception.getMessage());
        }
    }

    private Pageable compatibilityPage(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by("code"));
        }
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1
                || pageable.getPageSize() > 100) {
            throw new InvalidRequestException(
                    "Compatibility page must be non-negative with a size between 1 and 100.");
        }
        Sort requested = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by("code");
        requested.forEach(order -> {
            if (!COMPATIBILITY_SORTS.contains(order.getProperty())) {
                throw new InvalidRequestException(
                        "Unsupported compatibility sort: " + order.getProperty() + ".");
            }
        });
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), requested);
    }

    private Comparator<ExtensionCompatibilityDto> compatibilityComparator(Sort sort) {
        Comparator<ExtensionCompatibilityDto> result = (left, right) -> 0;
        for (Sort.Order order : sort) {
            Comparator<ExtensionCompatibilityDto> next = switch (order.getProperty()) {
                case "code" -> Comparator.comparing(
                        ExtensionCompatibilityDto::code, String.CASE_INSENSITIVE_ORDER);
                case "name" -> Comparator.comparing(
                        ExtensionCompatibilityDto::name, String.CASE_INSENSITIVE_ORDER);
                case "productType" -> Comparator.comparing(ExtensionCompatibilityDto::productType);
                case "available" -> Comparator.comparing(ExtensionCompatibilityDto::operatorSelectable);
                default -> throw new IllegalStateException(
                        "Validated compatibility sort was not implemented: " + order.getProperty());
            };
            result = result.thenComparing(order.isDescending() ? next.reversed() : next);
        }
        return result.thenComparing(ExtensionCompatibilityDto::productType)
                .thenComparing(ExtensionCompatibilityDto::code, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ExtensionCompatibilityDto::productId);
    }

    private Pageable historyPage(Pageable pageable) {
        if (pageable == null || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new InvalidRequestException("History page size must be between 1 and 100.");
        }
        return PageRequest.of(Math.max(0, pageable.getPageNumber()), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private boolean matches(ExtensionCompatibilityDto item, String search) {
        if (search == null || search.isBlank()) return true;
        String term = search.trim().toLowerCase(Locale.ROOT);
        return item.code().toLowerCase(Locale.ROOT).contains(term)
                || item.name().toLowerCase(Locale.ROOT).contains(term);
    }

    private String key(ExtensionCompatibilityDto item) {
        return item.productType() + ":" + item.productId();
    }

    private boolean equivalent(ExtensionCompatibilityDto before, ExtensionCompatibilityDto after) {
        return before != null
                && before.operatorSelectable() == after.operatorSelectable()
                && before.clientCatalogVisible() == after.clientCatalogVisible()
                && before.issues().equals(after.issues());
    }

    private String fingerprint(ExtensionCompatibilityDto item) {
        return key(item) + ":" + item.operatorSelectable() + ":" + item.clientCatalogVisible()
                + ":" + item.issues().stream().map(issue -> issue.reason().name() + "@" + issue.sourceCode())
                        .sorted().collect(Collectors.joining(","));
    }

    /**
     * Pins identities and versions, not just aggregate counts. An equal-count replacement of a
     * Plan, dependency, or price must invalidate the operator's preview.
     */
    private String catalogFingerprint(CommercialCatalogResolver.CatalogResolution catalog) {
        return catalog.plans().stream()
                .map(this::resolutionFingerprint)
                .sorted()
                .collect(Collectors.joining("||"));
    }

    private String resolutionFingerprint(CommercialCatalogResolver.PlanResolution resolution) {
        String plan = String.join(":",
                resolution.plan().getId().toString(),
                Long.toString(resolution.plan().getVersion()),
                resolution.plan().getStatus().name(),
                resolution.effectiveExtensionPolicy().name(),
                resolution.effectiveSalesVisibility().name(),
                issueFingerprint(resolution.issues()),
                priceFingerprint(resolution.prices()));
        String addOns = resolution.addOns().stream()
                .map(item -> String.join(":",
                        item.addOn().getId().toString(),
                        Long.toString(item.addOn().getRowVersion()),
                        item.addOn().getStatus().name(),
                        item.addOn().getSalesVisibility().name(),
                        String.join(",", new java.util.TreeSet<>(item.addOn().getDependencyCodes())),
                        String.join(",", new java.util.TreeSet<>(item.addOn().getExclusionCodes())),
                        issueFingerprint(item.issues()),
                        priceFingerprint(item.prices()),
                        item.supportedTuples().stream()
                                .map(tuple -> tuple.currencyCode() + "/" + tuple.billingCycle())
                                .sorted().collect(Collectors.joining(","))))
                .sorted().collect(Collectors.joining(";"));
        String packages = resolution.quotaPackages().stream()
                .map(item -> String.join(":",
                        item.quotaPackage().getId().toString(),
                        Long.toString(item.quotaPackage().getRowVersion()),
                        item.quotaPackage().getStatus().name(),
                        item.quotaPackage().getSalesVisibility().name(),
                        issueFingerprint(item.issues()),
                        priceFingerprint(item.prices()),
                        item.supportedTuples().stream()
                                .map(tuple -> tuple.currencyCode() + "/" + tuple.billingCycle())
                                .sorted().collect(Collectors.joining(","))))
                .sorted().collect(Collectors.joining(";"));
        return plan + "|A=" + addOns + "|Q=" + packages;
    }

    private String issueFingerprint(List<ExtensionAvailabilityIssue> issues) {
        return issues.stream()
                .map(issue -> issue.reason().name() + "@" + issue.source().name()
                        + "@" + Objects.toString(issue.sourceCode(), ""))
                .sorted().collect(Collectors.joining(","));
    }

    private String priceFingerprint(List<com.hiveapp.platform.client.plan.domain.entity.ProductPrice> prices) {
        return prices.stream()
                .map(price -> price.getId() + "@" + price.getVersion() + "@"
                        + price.getCurrencyCode() + "/" + price.getBillingCycle())
                .sorted().collect(Collectors.joining(","));
    }

    private void requireVersion(long actual, long expected, String label) {
        if (actual != expected) {
            throw new StaleResourceVersionException(
                    label + " changed since it was read. Reload and retry.");
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new InvalidRequestException("A reason between 1 and 500 characters is required.");
        }
    }

    private String historyReason(AuditLog log) {
        if (log.getRequestData() == null) return null;
        try {
            var root = objectMapper.readTree(log.getRequestData());
            var reason = root.get("request");
            if (reason != null) reason = reason.get("reason");
            if (reason == null) reason = root.path("before").get("reason");
            if (reason == null) reason = root.get("reason");
            return reason != null && reason.isTextual() && !reason.textValue().isBlank()
                    ? reason.textValue().trim() : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return null;
        }
    }

    private void recordAvailabilityChange(
            String action,
            UUID productId,
            CommercialProductType productType,
            String productCode,
            com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy previousPolicy,
            com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy resultingPolicy,
            ProductSalesVisibility previousVisibility,
            ProductSalesVisibility resultingVisibility,
            String reason
    ) {
        Map<String, Object> before = new java.util.LinkedHashMap<>();
        before.put("productType", productType);
        before.put("productCode", productCode);
        if (previousPolicy != null) before.put("extensionPolicy", previousPolicy);
        before.put("salesVisibility", previousVisibility);
        before.put("reason", reason.trim());

        Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("productType", productType);
        after.put("productCode", productCode);
        if (resultingPolicy != null) after.put("extensionPolicy", resultingPolicy);
        after.put("salesVisibility", resultingVisibility);

        auditTrail.recordSuccess(
                action,
                CommercialAvailabilityAuditContract.RESOURCE_TYPE,
                productId,
                AuditActorSurface.PLATFORM_ADMIN,
                adminMutationAuthorizer.currentActorUserId(),
                null,
                before,
                after);
    }

    private CommercialAvailabilityHistoryEntryDto historyEntry(
            AuditLog log,
            String actorEmail,
            ProductIdentity fallbackIdentity
    ) {
        HistoryDetails details = historyDetails(log);
        CommercialProductType productType = details.productType() != null
                ? details.productType() : fallbackIdentity.productType();
        String productCode = details.productCode() != null
                ? details.productCode() : fallbackIdentity.productCode();
        return new CommercialAvailabilityHistoryEntryDto(
                log.getId(), log.getOccurredAt(), log.getActorUserId(), actorEmail,
                log.getAction(), log.getOutcome(), log.getFailureType(), historyReason(log),
                productType, productCode, details.previousPolicy(),
                details.resultingPolicy(), details.previousVisibility(), details.resultingVisibility());
    }

    private ProductIdentity historyProductIdentity(UUID productId, Page<AuditLog> logs) {
        List<HistoryDetails> details = logs.stream().map(this::historyDetails).toList();
        ProductIdentity recorded = details.stream()
                .filter(item -> item.productType() != null && item.productCode() != null)
                .map(item -> new ProductIdentity(item.productType(), item.productCode()))
                .findFirst().orElse(null);
        if (recorded != null) return recorded;

        CommercialProductType type = details.stream().map(HistoryDetails::productType)
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (type == null) return ProductIdentity.EMPTY;
        String code = switch (type) {
            case PLAN -> planRepository.findById(productId).map(Plan::getCode).orElse(null);
            case ADD_ON -> addOnRepository.findById(productId).map(AddOn::getCode).orElse(null);
            case QUOTA_PACKAGE -> quotaPackageRepository.findById(productId)
                    .map(QuotaPackage::getCode).orElse(null);
        };
        return new ProductIdentity(type, code);
    }

    private HistoryDetails historyDetails(AuditLog log) {
        try {
            JsonNode before = log.getRequestData() == null
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(log.getRequestData()).path("before");
            JsonNode after = log.getResultData() == null
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(log.getResultData()).path("after");
            if (after.isObject() && after.hasNonNull("productType")) {
                return new HistoryDetails(
                        enumValue(after.get("productType"), CommercialProductType.class),
                        text(after.get("productCode")),
                        enumValue(before.get("extensionPolicy"),
                                com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy.class),
                        enumValue(after.get("extensionPolicy"),
                                com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy.class),
                        enumValue(before.get("salesVisibility"), ProductSalesVisibility.class),
                        enumValue(after.get("salesVisibility"), ProductSalesVisibility.class));
            }

            // Failed attempts are written by the transactional runner. They contain the requested
            // target but no invented "before" state, because the mutation never committed.
            JsonNode request = log.getRequestData() == null
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(log.getRequestData()).path("request");
            CommercialProductType type = switch (log.getAction()) {
                case CommercialAvailabilityAuditContract.PLAN_CHANGE_ACTION,
                        CommercialAvailabilityAuditContract.PLAN_MUTATION_ACTION ->
                        CommercialProductType.PLAN;
                case CommercialAvailabilityAuditContract.ADD_ON_CHANGE_ACTION,
                        CommercialAvailabilityAuditContract.ADD_ON_MUTATION_ACTION ->
                        CommercialProductType.ADD_ON;
                case CommercialAvailabilityAuditContract.QUOTA_CHANGE_ACTION,
                        CommercialAvailabilityAuditContract.QUOTA_MUTATION_ACTION ->
                        CommercialProductType.QUOTA_PACKAGE;
                default -> null;
            };
            return new HistoryDetails(
                    type, null, null,
                    enumValue(request.get("extensionPolicy"),
                            com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy.class),
                    null, enumValue(request.get("salesVisibility"), ProductSalesVisibility.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return HistoryDetails.EMPTY;
        }
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isBlank()
                ? node.textValue() : null;
    }

    private <E extends Enum<E>> E enumValue(JsonNode node, Class<E> type) {
        if (node == null || !node.isTextual()) return null;
        try {
            return Enum.valueOf(type, node.textValue());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record PlanAvailabilityAssessment(
            UUID planId,
            String planCode,
            long expectedVersion,
            PlanExtensionPolicy currentExtensionPolicy,
            PlanExtensionPolicy targetExtensionPolicy,
            ProductSalesVisibility currentSalesVisibility,
            ProductSalesVisibility targetSalesVisibility,
            long affectedSubscriptionCount,
            int totalExtensions,
            int operatorSelectableBefore,
            int operatorSelectableAfter,
            int clientVisibleBefore,
            int clientVisibleAfter,
            int changedCount,
            boolean changesTruncated,
            List<ExtensionCompatibilityDto> changedExtensions,
            boolean applicable,
            List<CommercialAvailabilityBlocker> blockers,
            Set<CommercialAvailabilityAction> availableActions,
            String fingerprint
    ) {
        private PlanAvailabilityPreviewDto toDto(
                long catalogRevision,
                String registryVersion,
                CommercialPreviewTokenService.IssuedEvidence evidence
        ) {
            return new PlanAvailabilityPreviewDto(
                    planId, planCode, expectedVersion, catalogRevision, registryVersion,
                    evidence.evaluatedAt(), evidence.expiresAt(), currentExtensionPolicy,
                    targetExtensionPolicy, currentSalesVisibility, targetSalesVisibility,
                    affectedSubscriptionCount, totalExtensions, operatorSelectableBefore,
                    operatorSelectableAfter, clientVisibleBefore, clientVisibleAfter,
                    changedCount, changesTruncated, changedExtensions, applicable, blockers,
                    availableActions, evidence.token());
        }
    }

    private record ProductVisibilityAssessment(
            CommercialProductType productType,
            UUID productId,
            String productCode,
            long expectedVersion,
            ProductSalesVisibility currentSalesVisibility,
            ProductSalesVisibility targetSalesVisibility,
            int compatiblePlanCount,
            int clientVisiblePlanCountBefore,
            int clientVisiblePlanCountAfter,
            boolean applicable,
            List<CommercialAvailabilityBlocker> blockers,
            Set<CommercialAvailabilityAction> availableActions,
            String fingerprint
    ) {
        private ProductVisibilityPreviewDto toDto(
                long catalogRevision,
                String registryVersion,
                CommercialPreviewTokenService.IssuedEvidence evidence
        ) {
            return new ProductVisibilityPreviewDto(
                    productType, productId, productCode, expectedVersion,
                    catalogRevision, registryVersion, evidence.evaluatedAt(), evidence.expiresAt(),
                    currentSalesVisibility, targetSalesVisibility, compatiblePlanCount,
                    clientVisiblePlanCountBefore, clientVisiblePlanCountAfter, applicable,
                    blockers, availableActions, evidence.token());
        }
    }

    private record HistoryDetails(
            CommercialProductType productType,
            String productCode,
            com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy previousPolicy,
            com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy resultingPolicy,
            ProductSalesVisibility previousVisibility,
            ProductSalesVisibility resultingVisibility
    ) {
        private static final HistoryDetails EMPTY =
                new HistoryDetails(null, null, null, null, null, null);
    }

    private record ProductIdentity(CommercialProductType productType, String productCode) {
        private static final ProductIdentity EMPTY = new ProductIdentity(null, null);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
}
