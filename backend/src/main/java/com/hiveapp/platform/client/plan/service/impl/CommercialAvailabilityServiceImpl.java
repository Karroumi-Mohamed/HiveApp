package com.hiveapp.platform.client.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAvailabilityAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAvailabilityBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductType;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionResolutionSource;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
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
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.PlanAdminReadModels;
import com.hiveapp.platform.registry.definition.CommercialAvailabilityFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
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

    private static final String AUDIT_RESOURCE_TYPE = "COMMERCIAL_AVAILABILITY";
    private static final int MAX_PREVIEW_CHANGES = 100;

    private final CommercialCatalogResolver catalogResolver;
    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanAdminReadModels readModels;
    private final AuditLogRepository auditLogRepository;
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;

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
        List<ExtensionCompatibilityDto> items = extensionDtos(resolution).stream()
                .filter(item -> type == null || item.productType() == type)
                .filter(item -> available == null || item.operatorSelectable() == available)
                .filter(item -> matches(item, search))
                .sorted(Comparator.comparing(ExtensionCompatibilityDto::productType)
                        .thenComparing(ExtensionCompatibilityDto::code))
                .toList();
        Pageable bounded = bounded(pageable);
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
        Plan plan = requirePlan(planId);
        return buildPlanPreview(plan, request.extensionPolicy(), request.salesVisibility());
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_plan_policy", description = "Apply a previewed Plan availability change")
    public PlanDto updatePlan(UUID planId, PlanAvailabilityMutationRequest request) {
        Plan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        requireVersion(plan.getVersion(), request.expectedVersion(), "Plan availability");
        requireReason(request.reason());
        PlanAvailabilityPreviewDto preview = buildPlanPreview(
                plan, request.extensionPolicy(), request.salesVisibility());
        if (!preview.previewToken().equals(request.previewToken())) {
            throw new StaleResourceVersionException(
                    "Plan availability preview is stale. Reload the preview and retry.");
        }
        requireApplicable(preview.applicable(), preview.blockers());
        plan.setExtensionPolicy(request.extensionPolicy());
        plan.setSalesVisibility(request.salesVisibility());
        return readModels.toDto(planRepository.saveAndFlush(plan));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_add_on_visibility", description = "Preview AddOn sales visibility")
    public ProductVisibilityPreviewDto previewAddOn(
            UUID addOnId, ProductVisibilityPreviewRequest request) {
        return buildAddOnVisibilityPreview(requireAddOn(addOnId), request.salesVisibility());
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_add_on_visibility", description = "Change AddOn sales visibility")
    public AddOnDto updateAddOn(UUID addOnId, ProductVisibilityMutationRequest request) {
        AddOn addOn = addOnRepository.findByIdForUpdate(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
        requireVersion(addOn.getRowVersion(), request.expectedVersion(), "AddOn visibility");
        requireReason(request.reason());
        ProductVisibilityPreviewDto preview = buildAddOnVisibilityPreview(
                addOn, request.salesVisibility());
        if (!preview.previewToken().equals(request.previewToken())) {
            throw new StaleResourceVersionException(
                    "AddOn visibility preview is stale. Reload the preview and retry.");
        }
        requireApplicable(preview.applicable(), preview.blockers());
        addOn.setSalesVisibility(request.salesVisibility());
        addOnRepository.saveAndFlush(addOn);
        return readModels.toDto(addOnRepository.findDetailedById(addOnId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_quota_visibility", description = "Preview quota-package sales visibility")
    public ProductVisibilityPreviewDto previewQuotaPackage(
            UUID quotaPackageId, ProductVisibilityPreviewRequest request) {
        return buildQuotaVisibilityPreview(requireQuotaPackage(quotaPackageId), request.salesVisibility());
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_quota_visibility", description = "Change quota-package sales visibility")
    public QuotaPackageDto updateQuotaPackage(
            UUID quotaPackageId, ProductVisibilityMutationRequest request) {
        QuotaPackage item = quotaPackageRepository.findByIdForUpdate(quotaPackageId)
                .orElseThrow(() -> new ResourceNotFoundException("QuotaPackage", "id", quotaPackageId));
        requireVersion(item.getRowVersion(), request.expectedVersion(), "Quota-package visibility");
        requireReason(request.reason());
        ProductVisibilityPreviewDto preview = buildQuotaVisibilityPreview(
                item, request.salesVisibility());
        if (!preview.previewToken().equals(request.previewToken())) {
            throw new StaleResourceVersionException(
                    "Quota-package visibility preview is stale. Reload the preview and retry.");
        }
        requireApplicable(preview.applicable(), preview.blockers());
        item.setSalesVisibility(request.salesVisibility());
        quotaPackageRepository.saveAndFlush(item);
        return readModels.toDto(quotaPackageRepository.findDetailedById(quotaPackageId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_history", description = "Read commercial availability history")
    public Page<CommercialAvailabilityHistoryEntryDto> history(UUID productId, Pageable pageable) {
        Pageable bounded = historyPage(pageable);
        Page<AuditLog> logs = auditLogRepository.findAllByResourceTypeAndResourceId(
                AUDIT_RESOURCE_TYPE, productId.toString(), bounded);
        Set<UUID> actorIds = logs.stream().map(AuditLog::getActorUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> actorEmails = actorIds.isEmpty() ? Map.of()
                : adminUserRepository.findAllWithUserByUserIdIn(actorIds).stream()
                        .collect(Collectors.toMap(
                                admin -> admin.getUser().getId(), admin -> admin.getUser().getEmail()));
        return logs.map(log -> new CommercialAvailabilityHistoryEntryDto(
                log.getId(), log.getOccurredAt(), log.getActorUserId(),
                actorEmails.get(log.getActorUserId()), log.getAction(), log.getOutcome(),
                log.getFailureType(), historyReason(log)));
    }

    private PlanAvailabilityPreviewDto buildPlanPreview(
            Plan plan,
            com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy targetPolicy,
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
        return new PlanAvailabilityPreviewDto(
                plan.getId(), plan.getCode(), plan.getVersion(), plan.getExtensionPolicy(), targetPolicy,
                plan.getSalesVisibility(), targetVisibility, subscribers, total,
                beforeOperator, afterOperator, beforeClient, afterClient, changed.size(),
                changed.size() > MAX_PREVIEW_CHANGES,
                changed.stream().limit(MAX_PREVIEW_CHANGES).toList(), applicable,
                blockers, applicable
                        ? Set.of(CommercialAvailabilityAction.APPLY_PLAN_AVAILABILITY) : Set.of(),
                sha256(state));
    }

    private ProductVisibilityPreviewDto buildAddOnVisibilityPreview(
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
        String token = visibilityToken(CommercialProductType.ADD_ON, addOn.getId(), addOn.getRowVersion(),
                target, catalogFingerprint(catalog));
        return new ProductVisibilityPreviewDto(
                CommercialProductType.ADD_ON, addOn.getId(), addOn.getCode(), addOn.getRowVersion(),
                addOn.getSalesVisibility(), target, compatible, before, after, applicable, blockers,
                applicable ? Set.of(CommercialAvailabilityAction.APPLY_SALES_VISIBILITY) : Set.of(), token);
    }

    private ProductVisibilityPreviewDto buildQuotaVisibilityPreview(
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
        String token = visibilityToken(
                CommercialProductType.QUOTA_PACKAGE, item.getId(), item.getRowVersion(),
                target, catalogFingerprint(catalog));
        return new ProductVisibilityPreviewDto(
                CommercialProductType.QUOTA_PACKAGE, item.getId(), item.getCode(), item.getRowVersion(),
                item.getSalesVisibility(), target, compatible, before, after, applicable, blockers,
                applicable ? Set.of(CommercialAvailabilityAction.APPLY_SALES_VISIBILITY) : Set.of(), token);
    }

    private String visibilityToken(
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
                    operatorSelectable ? result.addOn().getDependencyCodes() : Set.of()));
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
                    operatorSelectable ? result.requiredAddOnCodes() : Set.of()));
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

    private Pageable bounded(Pageable pageable) {
        int page = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        int size = pageable == null ? 20 : Math.min(100, Math.max(1, pageable.getPageSize()));
        return PageRequest.of(page, size, Sort.by("productType").and(Sort.by("code")));
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
            var reason = objectMapper.readTree(log.getRequestData()).get("request");
            if (reason != null) reason = reason.get("reason");
            if (reason == null) reason = objectMapper.readTree(log.getRequestData()).get("reason");
            return reason != null && reason.isTextual() && !reason.textValue().isBlank()
                    ? reason.textValue().trim() : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return null;
        }
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
