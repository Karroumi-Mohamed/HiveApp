package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductType;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionResolutionSource;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.ExtensionAvailabilityIssue;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single backend authority for new commercial selection. It deliberately loads one bounded
 * catalogue graph and resolves in memory, so callers cannot grow a query chain per Plan, AddOn,
 * dependency, or package.
 */
@Component
@RequiredArgsConstructor
public class CommercialCatalogResolver {

    private static final UUID EMPTY_QUERY_SENTINEL = new UUID(0L, 0L);
    // Full-catalog callers are not paginated yet. Crossing either safety boundary fails the whole
    // operation with INVALID_STATE; it must never return a partial catalogue presented as complete.
    private static final int MAX_SCOPED_PRODUCTS = 300;
    private static final int MAX_CATALOG_PRICES = 1_200;

    public enum Audience { CLIENT_CATALOG, AUTHORIZED_OPERATOR }

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CatalogResolution resolveCatalog(Audience audience) {
        CatalogData data = load();
        List<PlanResolution> plans = data.plans().stream()
                .sorted(Comparator.comparing(Plan::getCode))
                .map(plan -> resolvePlan(data, plan, audience, null,
                        plan.getExtensionPolicy(), plan.getSalesVisibility()))
                .toList();
        return new CatalogResolution(plans);
    }

    /** Code-declared half of the Plan feature eligibility predicate, shared with DB-paged choosers. */
    public Set<String> staticallyEligiblePlanFeatureCodes(Audience audience) {
        return featureDefinitionCollectorProvider.getObject().collectByCode().values().stream()
                .filter(definition -> isStaticallyEligiblePlanFeature(definition, audience))
                .map(FeatureDefinition::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public PlanResolution resolvePlan(UUID planId, Audience audience, PriceTuple requestedTuple) {
        CatalogData data = load();
        Plan plan = data.plansById().get(planId);
        if (plan == null) {
            throw new com.hiveapp.shared.exception.ResourceNotFoundException("Plan", "id", planId);
        }
        return resolvePlan(data, plan, audience, requestedTuple,
                plan.getExtensionPolicy(), plan.getSalesVisibility());
    }

    @Transactional(readOnly = true)
    public PlanResolution previewPlan(
            UUID planId,
            Audience audience,
            PriceTuple requestedTuple,
            PlanExtensionPolicy extensionPolicy,
            ProductSalesVisibility salesVisibility
    ) {
        CatalogData data = load();
        Plan plan = data.plansById().get(planId);
        if (plan == null) {
            throw new com.hiveapp.shared.exception.ResourceNotFoundException("Plan", "id", planId);
        }
        return resolvePlan(data, plan, audience, requestedTuple,
                extensionPolicy == null ? plan.getExtensionPolicy() : extensionPolicy,
                salesVisibility == null ? plan.getSalesVisibility() : salesVisibility);
    }

    /**
     * Evaluates a draft/inactive AddOn against a bounded batch of Plans with the same typed
     * compatibility predicate used by the live catalogue. Only the candidate AddOn's lifecycle
     * issue is waived; Plan policy, registry eligibility, dependency closure, exclusions,
     * duplicate paid capabilities, and price tuples remain authoritative.
     */
    @Transactional(readOnly = true)
    public Map<UUID, AddOnActivationResolution> resolveAddOnActivation(
            UUID addOnId,
            Collection<Plan> candidatePlans
    ) {
        List<Plan> plans = candidatePlans == null ? List.of() : candidatePlans.stream()
                .distinct()
                .sorted(Comparator.comparing(Plan::getId))
                .toList();
        if (plans.size() > 100) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "AddOn activation validates at most 100 Plans per batch.");
        }
        if (plans.isEmpty()) return Map.of();

        AddOn root = addOnRepository.findDetailedById(addOnId)
                .orElseThrow(() -> new com.hiveapp.shared.exception.ResourceNotFoundException(
                        "AddOn", "id", addOnId));
        Map<String, AddOn> addOnsByCode = new LinkedHashMap<>();
        addOnsByCode.put(root.getCode(), root);
        Set<String> pending = new LinkedHashSet<>(root.getDependencyCodes());
        while (!pending.isEmpty()) {
            if (addOnsByCode.size() + pending.size() > MAX_SCOPED_PRODUCTS) {
                throw new com.hiveapp.shared.exception.InvalidRequestException(
                        "The AddOn dependency graph exceeds 300 products.");
            }
            List<String> batch = pending.stream()
                    .filter(code -> !addOnsByCode.containsKey(code))
                    .sorted()
                    .toList();
            pending.clear();
            if (batch.isEmpty()) break;
            addOnRepository.findAllByCodeIn(batch).forEach(item -> {
                addOnsByCode.put(item.getCode(), item);
                item.getDependencyCodes().stream()
                        .filter(code -> !addOnsByCode.containsKey(code))
                        .forEach(pending::add);
            });
        }

        List<UUID> planIds = plans.stream().map(Plan::getId).toList();
        List<PlanFeature> planFeatures = planFeatureRepository.findAllByPlanIds(planIds);
        List<AddOn> addOns = addOnsByCode.values().stream()
                .sorted(Comparator.comparing(AddOn::getCode))
                .toList();
        List<UUID> addOnIds = addOns.stream().map(AddOn::getId).toList();
        List<ProductPrice> prices = boundedApplicablePrices(planIds, addOnIds, List.of());
        Map<PriceOwnerKey, List<ProductPrice>> pricesByOwner = prices.stream()
                .collect(Collectors.groupingBy(
                        price -> new PriceOwnerKey(price.getOwnerType(), price.ownerId()),
                        LinkedHashMap::new, Collectors.toList()));
        CatalogData data = new CatalogData(
                plans,
                plans.stream().collect(Collectors.toMap(Plan::getId, Function.identity())),
                planFeatures.stream().collect(Collectors.groupingBy(item -> item.getPlan().getId())),
                addOns,
                Map.copyOf(addOnsByCode),
                List.of(),
                Map.of(),
                featureDefinitionCollectorProvider.getObject().collectByCode(),
                Map.copyOf(pricesByOwner));

        Map<UUID, AddOnActivationResolution> results = new LinkedHashMap<>();
        for (Plan plan : plans) {
            PlanResolution planResolution = resolvePlan(
                    data, plan, Audience.AUTHORIZED_OPERATOR, null,
                    plan.getExtensionPolicy(), plan.getSalesVisibility(), root.getCode());
            AddOnResolution addOnResolution = planResolution.addOns().stream()
                    .filter(result -> result.code().equals(root.getCode()))
                    .findFirst()
                    .orElseThrow();
            results.put(plan.getId(), new AddOnActivationResolution(
                    planResolution, addOnResolution));
        }
        return Map.copyOf(results);
    }

    @Transactional(readOnly = true)
    public SelectionResolution resolveSelection(
            Plan plan,
            ProductPrice planPrice,
            Set<String> requestedAddOnCodes,
            List<QuotaPackageSelection> requestedPackages,
            Audience audience
    ) {
        return resolveSelection(plan, PriceTuple.from(planPrice), requestedAddOnCodes,
                requestedPackages, audience, RetainedSelection.none());
    }

    @Transactional(readOnly = true)
    public SelectionResolution resolveSelection(
            Plan plan,
            PriceTuple tuple,
            Set<String> requestedAddOnCodes,
            List<QuotaPackageSelection> requestedPackages,
            Audience audience
    ) {
        return resolveSelection(plan, tuple, requestedAddOnCodes, requestedPackages,
                audience, RetainedSelection.none());
    }

    @Transactional(readOnly = true)
    public SelectionResolution resolveSelection(
            Plan plan,
            PriceTuple tuple,
            Set<String> requestedAddOnCodes,
            List<QuotaPackageSelection> requestedPackages,
            Audience audience,
            RetainedSelection retained
    ) {
        return resolveSelection(load(), plan, tuple, requestedAddOnCodes, requestedPackages,
                audience, retained);
    }

    /**
     * Resolves one bounded page of independent exact selections against one shared catalogue
     * snapshot. Query count is independent of the number of candidates.
     */
    @Transactional(readOnly = true)
    public Map<UUID, SelectionResolution> resolveExactSelectionCandidates(
            Collection<ExactSelectionCandidate> candidates,
            Audience audience
    ) {
        List<ExactSelectionCandidate> bounded = candidates == null
                ? List.of() : List.copyOf(candidates);
        if (bounded.size() > 200) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "At most 200 exact commercial selections can be resolved per batch.");
        }
        if (bounded.stream().map(ExactSelectionCandidate::key).distinct().count() != bounded.size()) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Exact commercial selection keys must be unique.");
        }
        CatalogData data = load();
        Map<UUID, SelectionResolution> resolved = new LinkedHashMap<>();
        for (ExactSelectionCandidate candidate : bounded) {
            Plan plan = data.plansById().get(candidate.planId());
            if (plan == null) continue;
            resolved.put(candidate.key(), resolveSelection(
                    data, plan, candidate.tuple(), candidate.addOnCodes(),
                    candidate.quotaPackages(), audience, RetainedSelection.none()));
        }
        return Map.copyOf(resolved);
    }

    /**
     * Resolves one bounded subscription-override candidate page and the Account's retained
     * selections without loading unrelated Plans or the full extension catalogue.
     */
    @Transactional(readOnly = true)
    public OverrideCandidateResolution resolveOverrideCandidates(
            UUID planId,
            PriceTuple tuple,
            Collection<UUID> addOnCandidateIds,
            Collection<UUID> quotaPackageCandidateIds,
            Set<String> selectedAddOnCodes,
            Set<String> retainedQuotaPackageCodes,
            RetainedSelection retained
    ) {
        RetainedSelection retainedSelection = retained == null ? RetainedSelection.none() : retained;
        Set<String> scopedAddOnCodes = new LinkedHashSet<>(
                selectedAddOnCodes == null ? Set.of() : selectedAddOnCodes);
        scopedAddOnCodes.addAll(retainedSelection.addOnCodes());
        CatalogData data = loadScoped(
                planId, addOnCandidateIds, quotaPackageCandidateIds,
                scopedAddOnCodes, retainedQuotaPackageCodes);
        Plan plan = data.plansById().get(planId);
        if (plan == null) {
            throw new com.hiveapp.shared.exception.ResourceNotFoundException("Plan", "id", planId);
        }
        Set<String> selected = selectedAddOnCodes == null ? Set.of() : Set.copyOf(selectedAddOnCodes);
        PlanResolution planResolution = resolvePlan(
                data, plan, Audience.AUTHORIZED_OPERATOR, tuple,
                plan.getExtensionPolicy(), plan.getSalesVisibility());
        Map<UUID, AddOn> candidateAddOns = data.addOns().stream()
                .filter(item -> addOnCandidateIds != null && addOnCandidateIds.contains(item.getId()))
                .collect(Collectors.toMap(AddOn::getId, Function.identity()));
        Map<String, AddOnCandidateDecision> addOnDecisions = new LinkedHashMap<>();
        Map<String, AddOnResolution> addOnResolutions = planResolution.addOns().stream()
                .collect(Collectors.toMap(AddOnResolution::code, Function.identity()));
        RetainedSelection retainedByProposedSelection = new RetainedSelection(
                retainedSelection.addOnCodes().stream()
                        .filter(selected::contains)
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                retainedSelection.quotaPackageQuantities(),
                retainedSelection.heldPlanPriceEntryId());
        candidateAddOns.values().stream().sorted(Comparator.comparing(AddOn::getCode)).forEach(candidate -> {
            AddOnResolution product = addOnResolutions.get(candidate.getCode());
            Set<String> proposed = new LinkedHashSet<>(selected);
            proposed.add(candidate.getCode());
            if (product != null) proposed.addAll(product.dependencyClosureCodes());
            SelectionResolution selection = resolveSelection(
                    data, plan, tuple, proposed, List.of(), Audience.AUTHORIZED_OPERATOR,
                    retainedByProposedSelection);
            addOnDecisions.put(candidate.getCode(),
                    new AddOnCandidateDecision(product, selection, selection.selectable()));
        });

        Map<UUID, QuotaPackage> candidatePackages = data.packages().stream()
                .filter(item -> quotaPackageCandidateIds != null
                        && quotaPackageCandidateIds.contains(item.getId()))
                .collect(Collectors.toMap(QuotaPackage::getId, Function.identity()));
        Map<String, QuotaPackageCandidateDecision> packageDecisions = new LinkedHashMap<>();
        candidatePackages.values().stream().sorted(Comparator.comparing(QuotaPackage::getCode))
                .forEach(candidate -> {
                    SelectionResolution selection = resolveSelection(
                            data, plan, tuple, selected,
                            List.of(new QuotaPackageSelection(candidate.getCode(), 1)),
                            Audience.AUTHORIZED_OPERATOR, retainedSelection);
                    QuotaPackageResolution product = selection.packageResolutions().get(candidate.getCode());
                    packageDecisions.put(candidate.getCode(),
                            new QuotaPackageCandidateDecision(product, selection, selection.selectable()));
                });
        return new OverrideCandidateResolution(
                planResolution, Map.copyOf(addOnDecisions), Map.copyOf(packageDecisions));
    }

    private SelectionResolution resolveSelection(
            CatalogData data,
            Plan plan,
            PriceTuple tuple,
            Set<String> requestedAddOnCodes,
            List<QuotaPackageSelection> requestedPackages,
            Audience audience,
            RetainedSelection retained
    ) {
        Plan authoritativePlan = data.plansById().get(plan.getId());
        if (authoritativePlan == null) {
            throw new com.hiveapp.shared.exception.ResourceNotFoundException("Plan", "id", plan.getId());
        }
        RetainedSelection retainedSelection = retained == null
                ? RetainedSelection.none() : retained;
        PlanResolution planResolution = resolvePlan(data, authoritativePlan, audience, tuple,
                authoritativePlan.getExtensionPolicy(), authoritativePlan.getSalesVisibility());
        if (retainedSelection.heldPlanPriceEntryId() != null) {
            planResolution = new PlanResolution(
                    planResolution.plan(),
                    planResolution.issues().stream()
                            .filter(issue -> issue.reason() != ExtensionAvailabilityReason.PRICE_UNAVAILABLE)
                            .toList(),
                    planResolution.prices(), planResolution.planFeatures(), planResolution.addOns(),
                    planResolution.quotaPackages(), planResolution.effectiveExtensionPolicy(),
                    planResolution.effectiveSalesVisibility());
        }
        Set<String> addOnCodes = requestedAddOnCodes == null
                ? Set.of() : Set.copyOf(requestedAddOnCodes);
        List<QuotaPackageSelection> packages = requestedPackages == null
                ? List.of() : List.copyOf(requestedPackages);

        Map<String, AddOnResolution> addOnsByCode = planResolution.addOns().stream()
                .collect(Collectors.toMap(AddOnResolution::code, Function.identity()));
        List<ExtensionAvailabilityIssue> selectionIssues = new ArrayList<>();
        Set<String> entitledFeatures = data.planFeaturesByPlanId()
                .getOrDefault(plan.getId(), List.of()).stream()
                .filter(item -> item.getMode() == PlanFeatureMode.INCLUDED)
                .map(item -> item.getFeature().getCode())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String code : addOnCodes) {
            AddOnResolution result = addOnsByCode.get(code);
            if (result == null) {
                selectionIssues.add(issue(ExtensionAvailabilityReason.PRODUCT_NOT_FOUND,
                        ExtensionResolutionSource.PRODUCT_LIFECYCLE, code));
                continue;
            }
            List<ExtensionAvailabilityIssue> effectiveIssues = result.issues().stream()
                    .filter(issue -> !waivedForSelection(
                            issue, code, retainedSelection, addOnCodes))
                    .toList();
            if (!effectiveIssues.isEmpty()) {
                selectionIssues.addAll(effectiveIssues);
                continue;
            }
            AddOn addOn = data.addOnsByCode().get(code);
            for (String dependency : addOn.getDependencyCodes()) {
                if (!addOnCodes.contains(dependency)) {
                    selectionIssues.add(issue(ExtensionAvailabilityReason.DEPENDENCY_NOT_SELECTED,
                            ExtensionResolutionSource.DEPENDENCY, dependency));
                }
            }
            for (String excluded : addOn.getExclusionCodes()) {
                if (addOnCodes.contains(excluded)) {
                    selectionIssues.add(issue(ExtensionAvailabilityReason.EXCLUDED_SELECTION,
                            ExtensionResolutionSource.EXCLUSION, excluded));
                }
            }
            for (AddOnFeature feature : addOn.getFeatures()) {
                if (!entitledFeatures.add(feature.getFeature().getCode())) {
                    selectionIssues.add(issue(ExtensionAvailabilityReason.DUPLICATE_PAID_CAPABILITY,
                            ExtensionResolutionSource.ENTITLEMENT, feature.getFeature().getCode()));
                }
            }
        }

        Map<String, QuotaPackageResolution> packageByCode = resolvePackages(
                data, authoritativePlan, tuple, audience, addOnCodes, addOnsByCode,
                authoritativePlan.getExtensionPolicy());
        Set<String> seenPackages = new LinkedHashSet<>();
        for (QuotaPackageSelection selection : packages) {
            if (selection == null || selection.packageCode() == null
                    || !seenPackages.add(selection.packageCode())) {
                selectionIssues.add(issue(ExtensionAvailabilityReason.INVALID_QUANTITY,
                        ExtensionResolutionSource.QUOTA_OWNERSHIP,
                        selection == null ? null : selection.packageCode()));
                continue;
            }
            QuotaPackageResolution result = packageByCode.get(selection.packageCode());
            if (result == null) {
                selectionIssues.add(issue(ExtensionAvailabilityReason.PRODUCT_NOT_FOUND,
                        ExtensionResolutionSource.PRODUCT_LIFECYCLE, selection.packageCode()));
                continue;
            }
            Integer retainedQuantity = retainedSelection.quotaPackageQuantities()
                    .get(selection.packageCode());
            // Retention is not a route to change the quantity of a paused or direct-only
            // package at its historical terms. The exact held quantity may remain; a changed
            // quantity must pass the ordinary new-sale checks (removal remains possible by
            // omitting the package altogether).
            boolean retainedPackage = retainedQuantity != null
                    && selection.quantity() == retainedQuantity;
            List<ExtensionAvailabilityIssue> effectiveIssues = result.issues().stream()
                    .filter(issue -> !retainedPackage || !grandfatherable(issue.reason()))
                    .filter(issue -> !retainedPackage
                            || (issue.reason() != ExtensionAvailabilityReason.QUOTA_NOT_FINITE
                            && issue.reason() != ExtensionAvailabilityReason.QUOTA_OWNER_MISSING))
                    .toList();
            if (retainedPackage && !hasRetainedQuotaOwner(
                    data, authoritativePlan, data.packagesByCode().get(selection.packageCode()), addOnCodes)) {
                effectiveIssues = java.util.stream.Stream.concat(
                                effectiveIssues.stream(),
                                java.util.stream.Stream.of(issue(
                                        ExtensionAvailabilityReason.QUOTA_OWNER_MISSING,
                                        ExtensionResolutionSource.QUOTA_OWNERSHIP,
                                        selection.packageCode())))
                        .toList();
            }
            if (!effectiveIssues.isEmpty()) {
                selectionIssues.addAll(effectiveIssues);
                continue;
            }
            QuotaPackage item = data.packagesByCode().get(selection.packageCode());
            if (selection.quantity() < 1 || selection.quantity() > item.getMaximumQuantity()
                    || (!item.isRepeatable() && selection.quantity() != 1)) {
                selectionIssues.add(issue(ExtensionAvailabilityReason.INVALID_QUANTITY,
                        ExtensionResolutionSource.QUOTA_OWNERSHIP, item.getCode()));
            } else {
                try {
                    Math.multiplyExact(item.getCapacityPerUnit(), selection.quantity());
                } catch (ArithmeticException exception) {
                    selectionIssues.add(issue(ExtensionAvailabilityReason.INVALID_QUANTITY,
                            ExtensionResolutionSource.QUOTA_OWNERSHIP, item.getCode()));
                }
            }
        }

        return new SelectionResolution(planResolution, addOnCodes, packages,
                List.copyOf(distinctIssues(selectionIssues)), Map.copyOf(packageByCode));
    }

    private PlanResolution resolvePlan(
            CatalogData data,
            Plan plan,
            Audience audience,
            PriceTuple requestedTuple,
            PlanExtensionPolicy extensionPolicy,
            ProductSalesVisibility salesVisibility
    ) {
        return resolvePlan(data, plan, audience, requestedTuple,
                extensionPolicy, salesVisibility, null);
    }

    private PlanResolution resolvePlan(
            CatalogData data,
            Plan plan,
            Audience audience,
            PriceTuple requestedTuple,
            PlanExtensionPolicy extensionPolicy,
            ProductSalesVisibility salesVisibility,
            String activationRootCode
    ) {
        List<ExtensionAvailabilityIssue> issues = new ArrayList<>();
        if (!plan.isActive()) {
            issues.add(issue(ExtensionAvailabilityReason.PRODUCT_NOT_ACTIVE,
                    ExtensionResolutionSource.PRODUCT_LIFECYCLE, plan.getCode()));
        }
        if (audience == Audience.CLIENT_CATALOG
                && salesVisibility == ProductSalesVisibility.DIRECT_ONLY) {
            issues.add(issue(ExtensionAvailabilityReason.DIRECT_ONLY,
                    ExtensionResolutionSource.PRODUCT_VISIBILITY, plan.getCode()));
        }
        for (PlanFeature item : data.planFeaturesByPlanId().getOrDefault(plan.getId(), List.of())) {
            if (item.getMode() == PlanFeatureMode.INCLUDED) {
                addFeatureIssues(data, item.getFeature(), audience, issues);
            }
        }
        List<ProductPrice> planPrices = prices(data, ProductPriceOwnerType.PLAN, plan.getId()).stream()
                .filter(price -> requestedTuple == null || requestedTuple.matches(price))
                .toList();
        addPriceIssues(planPrices, plan.getCode(), issues);
        Set<PriceTuple> tuples = planPrices.stream().map(PriceTuple::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedTuple != null) tuples = Set.of(requestedTuple);

        Map<String, AddOnResolution> addOns = resolveAddOns(
                data, plan, tuples, audience, extensionPolicy, activationRootCode);
        Map<String, QuotaPackageResolution> packages = resolvePackages(
                data, plan, requestedTuple, audience, null, addOns, extensionPolicy);
        return new PlanResolution(plan, List.copyOf(issues), planPrices,
                List.copyOf(data.planFeaturesByPlanId().getOrDefault(plan.getId(), List.of())),
                addOns.values().stream().sorted(Comparator.comparing(AddOnResolution::code)).toList(),
                packages.values().stream().sorted(Comparator.comparing(QuotaPackageResolution::code)).toList(),
                extensionPolicy, salesVisibility);
    }

    private Map<String, AddOnResolution> resolveAddOns(
            CatalogData data,
            Plan plan,
            Set<PriceTuple> tuples,
            Audience audience,
            PlanExtensionPolicy extensionPolicy
    ) {
        return resolveAddOns(data, plan, tuples, audience, extensionPolicy, null);
    }

    private Map<String, AddOnResolution> resolveAddOns(
            CatalogData data,
            Plan plan,
            Set<PriceTuple> tuples,
            Audience audience,
            PlanExtensionPolicy extensionPolicy,
            String activationRootCode
    ) {
        Map<String, AddOnResolution> memo = new LinkedHashMap<>();
        for (AddOn addOn : data.addOns()) {
            resolveAddOn(data, plan, addOn, tuples, audience, extensionPolicy,
                    memo, new LinkedHashSet<>(), activationRootCode);
        }
        return memo;
    }

    private AddOnResolution resolveAddOn(
            CatalogData data,
            Plan plan,
            AddOn addOn,
            Set<PriceTuple> tuples,
            Audience audience,
            PlanExtensionPolicy extensionPolicy,
            Map<String, AddOnResolution> memo,
            Set<String> stack
    ) {
        return resolveAddOn(data, plan, addOn, tuples, audience, extensionPolicy,
                memo, stack, null);
    }

    private AddOnResolution resolveAddOn(
            CatalogData data,
            Plan plan,
            AddOn addOn,
            Set<PriceTuple> tuples,
            Audience audience,
            PlanExtensionPolicy extensionPolicy,
            Map<String, AddOnResolution> memo,
            Set<String> stack,
            String activationRootCode
    ) {
        AddOnResolution cached = memo.get(addOn.getCode());
        if (cached != null) return cached;
        List<ExtensionAvailabilityIssue> issues = new ArrayList<>();
        addPlanPolicyIssues(plan, extensionPolicy, addOn.getCode(), addOn.getAllowedPlanCodes(),
                addOn.getBlockedPlanCodes(), issues);
        if (addOn.getStatus() != AddOnStatus.ACTIVE
                && !addOn.getCode().equals(activationRootCode)) {
            issues.add(issue(ExtensionAvailabilityReason.PRODUCT_NOT_ACTIVE,
                    ExtensionResolutionSource.PRODUCT_LIFECYCLE, addOn.getCode()));
        }
        if (audience == Audience.CLIENT_CATALOG
                && addOn.getSalesVisibility() == ProductSalesVisibility.DIRECT_ONLY) {
            issues.add(issue(ExtensionAvailabilityReason.DIRECT_ONLY,
                    ExtensionResolutionSource.PRODUCT_VISIBILITY, addOn.getCode()));
        }
        Map<String, PlanFeature> planFeatures = data.planFeaturesByPlanId()
                .getOrDefault(plan.getId(), List.of()).stream()
                .collect(Collectors.toMap(item -> item.getFeature().getCode(), Function.identity()));
        for (AddOnFeature item : addOn.getFeatures()) {
            addFeatureIssues(data, item.getFeature(), audience, issues);
            PlanFeature planFeature = planFeatures.get(item.getFeature().getCode());
            if (planFeature == null || planFeature.getMode() != PlanFeatureMode.OPTIONAL_ADD_ON) {
                issues.add(issue(ExtensionAvailabilityReason.PLAN_FEATURE_NOT_OPTIONAL,
                        ExtensionResolutionSource.PLAN_COMPOSITION, item.getFeature().getCode()));
            }
        }

        List<ProductPrice> ownApplicablePrices = prices(
                data, ProductPriceOwnerType.ADD_ON, addOn.getId()).stream()
                .filter(price -> tuples.isEmpty()
                        || tuples.stream().anyMatch(tuple -> tuple.matches(price)))
                .toList();
        Set<PriceTuple> supportedTuples = ownApplicablePrices.stream()
                .map(PriceTuple::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> dependencyClosure = new LinkedHashSet<>();

        if (!stack.add(addOn.getCode())) {
            issues.add(issue(ExtensionAvailabilityReason.DEPENDENCY_CYCLE,
                    ExtensionResolutionSource.DEPENDENCY, addOn.getCode()));
        } else {
            for (String dependencyCode : addOn.getDependencyCodes()) {
                AddOn dependency = data.addOnsByCode().get(dependencyCode);
                if (dependency == null) {
                    issues.add(issue(ExtensionAvailabilityReason.DEPENDENCY_MISSING,
                            ExtensionResolutionSource.DEPENDENCY, dependencyCode));
                    continue;
                }
                if (stack.contains(dependencyCode)) {
                    issues.add(issue(ExtensionAvailabilityReason.DEPENDENCY_CYCLE,
                            ExtensionResolutionSource.DEPENDENCY, dependencyCode));
                    continue;
                }
                AddOnResolution dependencyResult = resolveAddOn(
                        data, plan, dependency, tuples, audience, extensionPolicy,
                        memo, stack, activationRootCode);
                dependencyClosure.add(dependencyCode);
                dependencyClosure.addAll(dependencyResult.dependencyClosureCodes());
                if (!dependencyResult.selectable()) {
                    issues.add(issue(ExtensionAvailabilityReason.DEPENDENCY_UNAVAILABLE,
                            ExtensionResolutionSource.DEPENDENCY, dependencyCode));
                }
                supportedTuples.retainAll(dependencyResult.supportedTuples());
            }
            stack.remove(addOn.getCode());
        }

        Set<String> fullClosure = new LinkedHashSet<>(dependencyClosure);
        fullClosure.add(addOn.getCode());
        Map<String, Long> featureOccurrences = fullClosure.stream()
                .map(data.addOnsByCode()::get)
                .filter(Objects::nonNull)
                .flatMap(item -> item.getFeatures().stream())
                .map(item -> item.getFeature().getCode())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        featureOccurrences.forEach((featureCode, count) -> {
            if (count > 1) {
                issues.add(issue(ExtensionAvailabilityReason.DUPLICATE_PAID_CAPABILITY,
                        ExtensionResolutionSource.ENTITLEMENT, featureCode));
            }
        });
        for (String closureCode : fullClosure) {
            AddOn closureItem = data.addOnsByCode().get(closureCode);
            if (closureItem == null) continue;
            closureItem.getExclusionCodes().stream()
                    .filter(fullClosure::contains)
                    .forEach(excluded -> issues.add(issue(
                            ExtensionAvailabilityReason.EXCLUDED_SELECTION,
                            ExtensionResolutionSource.EXCLUSION, excluded)));
        }

        List<ProductPrice> applicablePrices = ownApplicablePrices.stream()
                .filter(price -> supportedTuples.contains(PriceTuple.from(price)))
                .toList();
        addPriceIssues(applicablePrices, addOn.getCode(), issues);
        AddOnResolution result = new AddOnResolution(
                addOn, List.copyOf(distinctIssues(issues)), applicablePrices,
                Set.copyOf(supportedTuples), Set.copyOf(dependencyClosure));
        memo.put(addOn.getCode(), result);
        return result;
    }

    private Map<String, QuotaPackageResolution> resolvePackages(
            CatalogData data,
            Plan plan,
            PriceTuple requestedTuple,
            Audience audience,
            Set<String> selectedAddOnCodes,
            Map<String, AddOnResolution> addOnResults,
            PlanExtensionPolicy extensionPolicy
    ) {
        Set<PriceTuple> tuples = requestedTuple == null
                ? prices(data, ProductPriceOwnerType.PLAN, plan.getId()).stream()
                        .map(PriceTuple::from).collect(Collectors.toSet())
                : Set.of(requestedTuple);
        Map<String, QuotaPackageResolution> results = new LinkedHashMap<>();
        for (QuotaPackage item : data.packages()) {
            List<ExtensionAvailabilityIssue> issues = new ArrayList<>();
            if (extensionPolicy == PlanExtensionPolicy.CLOSED) {
                issues.add(issue(ExtensionAvailabilityReason.PLAN_EXTENSIONS_CLOSED,
                        ExtensionResolutionSource.PLAN_POLICY, plan.getCode()));
            }
            boolean directPlanTarget = item.getAllowedPlanCodes().contains(plan.getCode());
            boolean catalogDiscovery = selectedAddOnCodes == null;
            Set<String> candidateAddOns = catalogDiscovery
                    ? addOnResults.values().stream().filter(AddOnResolution::selectable)
                            .map(AddOnResolution::code).collect(Collectors.toSet())
                    : selectedAddOnCodes;
            boolean addOnTarget = item.getAllowedAddOnCodes().stream().anyMatch(candidateAddOns::contains);
            boolean hasTargeting = !item.getAllowedPlanCodes().isEmpty()
                    || !item.getAllowedAddOnCodes().isEmpty();
            if (extensionPolicy == PlanExtensionPolicy.ALLOW_LIST
                    && !directPlanTarget && !addOnTarget) {
                issues.add(issue(ExtensionAvailabilityReason.NOT_ALLOW_LISTED,
                        ExtensionResolutionSource.PLAN_POLICY, item.getCode()));
            } else if (extensionPolicy == PlanExtensionPolicy.OPEN_COMPATIBLE
                    && hasTargeting
                    && !directPlanTarget && !addOnTarget) {
                issues.add(issue(ExtensionAvailabilityReason.OPTIONAL_TARGETING_EXCLUDED,
                        ExtensionResolutionSource.PRODUCT_TARGETING, item.getCode()));
            }
            if (item.getStatus() != QuotaPackageStatus.ACTIVE) {
                issues.add(issue(ExtensionAvailabilityReason.PRODUCT_NOT_ACTIVE,
                        ExtensionResolutionSource.PRODUCT_LIFECYCLE, item.getCode()));
            }
            if (audience == Audience.CLIENT_CATALOG
                    && item.getSalesVisibility() == ProductSalesVisibility.DIRECT_ONLY) {
                issues.add(issue(ExtensionAvailabilityReason.DIRECT_ONLY,
                        ExtensionResolutionSource.PRODUCT_VISIBILITY, item.getCode()));
            }
            addFeatureIssues(data, item.getFeature(), audience, issues);

            boolean unrestrictedOpen = extensionPolicy == PlanExtensionPolicy.OPEN_COMPATIBLE
                    && !hasTargeting;
            boolean planOwner = (directPlanTarget || unrestrictedOpen)
                    && planOwnsFiniteQuota(data, plan, item);
            List<ProductPrice> ownApplicablePrices = prices(
                    data, ProductPriceOwnerType.QUOTA_PACKAGE, item.getId()).stream()
                    .filter(price -> tuples.isEmpty()
                            || tuples.stream().anyMatch(tuple -> tuple.matches(price)))
                    .toList();
            Set<PriceTuple> packageTuples = ownApplicablePrices.stream()
                    .map(PriceTuple::from)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> addOnOwners = finiteQuotaAddOnOwners(
                    data, item, candidateAddOns, addOnResults, unrestrictedOpen, packageTuples);
            boolean finiteOwner = planOwner || !addOnOwners.isEmpty();
            if (!finiteOwner) {
                boolean matchingOwner = ((directPlanTarget || unrestrictedOpen)
                        && planEntitlesFeature(data, plan, item))
                        || !matchingQuotaAddOnOwners(
                                data, item, candidateAddOns, addOnResults, unrestrictedOpen).isEmpty();
                issues.add(issue(matchingOwner
                                ? ExtensionAvailabilityReason.QUOTA_NOT_FINITE
                                : ExtensionAvailabilityReason.QUOTA_OWNER_MISSING,
                        ExtensionResolutionSource.QUOTA_OWNERSHIP,
                        item.getFeature().getCode() + "." + item.getResource()));
            }
            Set<PriceTuple> supportedTuples = new LinkedHashSet<>();
            if (planOwner) supportedTuples.addAll(packageTuples);
            for (String ownerCode : addOnOwners) {
                Set<PriceTuple> ownerTuples = addOnResults.get(ownerCode).supportedTuples();
                packageTuples.stream().filter(ownerTuples::contains).forEach(supportedTuples::add);
            }
            List<ProductPrice> applicablePrices = ownApplicablePrices.stream()
                    .filter(price -> supportedTuples.contains(PriceTuple.from(price)))
                    .toList();
            addPriceIssues(applicablePrices, item.getCode(), issues);
            List<ExtensionAvailabilityIssue> distinct = List.copyOf(distinctIssues(issues));
            results.put(item.getCode(), new QuotaPackageResolution(
                    item, distinct, applicablePrices,
                    distinct.isEmpty() && planOwner && !applicablePrices.isEmpty(),
                    distinct.isEmpty() ? Set.copyOf(addOnOwners) : Set.of(),
                    Set.copyOf(supportedTuples)));
        }
        return results;
    }

    private boolean planOwnsFiniteQuota(
            CatalogData data,
            Plan plan,
            QuotaPackage item
    ) {
        return data.planFeaturesByPlanId().getOrDefault(plan.getId(), List.of()).stream()
                .filter(feature -> feature.getFeature().getCode().equals(item.getFeature().getCode()))
                .filter(feature -> feature.getMode() == PlanFeatureMode.INCLUDED)
                .anyMatch(feature -> hasFiniteQuota(feature.getQuotaConfigs(), item.getResource()));
    }

    private boolean planEntitlesFeature(CatalogData data, Plan plan, QuotaPackage item) {
        return data.planFeaturesByPlanId().getOrDefault(plan.getId(), List.of()).stream()
                .anyMatch(feature -> feature.getMode() == PlanFeatureMode.INCLUDED
                        && feature.getFeature().getCode().equals(item.getFeature().getCode()));
    }

    private Set<String> finiteQuotaAddOnOwners(
            CatalogData data,
            QuotaPackage item,
            Set<String> candidateAddOns,
            Map<String, AddOnResolution> addOnResults,
            boolean unrestrictedOpen,
            Set<PriceTuple> packageTuples
    ) {
        Set<String> possibleOwners = unrestrictedOpen
                ? candidateAddOns
                : item.getAllowedAddOnCodes();
        Set<String> owners = new LinkedHashSet<>();
        for (String addOnCode : possibleOwners) {
            if (!candidateAddOns.contains(addOnCode)) continue;
            AddOnResolution resolution = addOnResults.get(addOnCode);
            if (resolution == null || !resolution.selectable()) continue;
            if (resolution.supportedTuples().stream().noneMatch(packageTuples::contains)) continue;
            AddOn addOn = data.addOnsByCode().get(addOnCode);
            if (addOn != null && addOn.getFeatures().stream()
                    .filter(feature -> feature.getFeature().getCode().equals(item.getFeature().getCode()))
                    .anyMatch(feature -> hasFiniteQuota(feature.getQuotaConfigs(), item.getResource()))) {
                owners.add(addOnCode);
            }
        }
        return owners;
    }

    private Set<String> matchingQuotaAddOnOwners(
            CatalogData data,
            QuotaPackage item,
            Set<String> candidateAddOns,
            Map<String, AddOnResolution> addOnResults,
            boolean unrestrictedOpen
    ) {
        Set<String> possibleOwners = unrestrictedOpen
                ? candidateAddOns
                : item.getAllowedAddOnCodes();
        Set<String> owners = new LinkedHashSet<>();
        for (String addOnCode : possibleOwners) {
            if (!candidateAddOns.contains(addOnCode)) continue;
            AddOnResolution resolution = addOnResults.get(addOnCode);
            if (resolution == null || !resolution.selectable()) continue;
            AddOn addOn = data.addOnsByCode().get(addOnCode);
            if (addOn != null && addOn.getFeatures().stream()
                    .anyMatch(feature -> feature.getFeature().getCode()
                            .equals(item.getFeature().getCode()))) {
                owners.add(addOnCode);
            }
        }
        return owners;
    }

    private boolean waivedForSelection(
            ExtensionAvailabilityIssue issue,
            String productCode,
            RetainedSelection retained,
            Set<String> selectedAddOns
    ) {
        if (retained.addOnCodes().contains(productCode) && grandfatherable(issue.reason())) {
            return true;
        }
        return issue.reason() == ExtensionAvailabilityReason.DEPENDENCY_UNAVAILABLE
                && issue.sourceCode() != null
                && retained.addOnCodes().contains(issue.sourceCode())
                && selectedAddOns.contains(issue.sourceCode());
    }

    private boolean grandfatherable(ExtensionAvailabilityReason reason) {
        return switch (reason) {
            case PLAN_EXTENSIONS_CLOSED,
                    PLAN_EXPLICITLY_BLOCKED,
                    NOT_ALLOW_LISTED,
                    OPTIONAL_TARGETING_EXCLUDED,
                    PRODUCT_NOT_ACTIVE,
                    DIRECT_ONLY,
                    FEATURE_DEFINITION_MISSING,
                    FEATURE_NOT_PLAN_ASSIGNABLE,
                    FEATURE_NOT_CLIENT_FACING,
                    FEATURE_NEW_SALES_DISABLED,
                    FEATURE_RUNTIME_DISABLED,
                    PRICE_UNAVAILABLE,
                    DEPENDENCY_UNAVAILABLE,
                    QUOTA_NOT_FINITE,
                    QUOTA_OWNER_MISSING -> true;
            default -> false;
        };
    }

    private boolean hasRetainedQuotaOwner(
            CatalogData data,
            Plan plan,
            QuotaPackage item,
            Set<String> selectedAddOns
    ) {
        if (item == null) return false;
        boolean targeted = !item.getAllowedPlanCodes().isEmpty()
                || !item.getAllowedAddOnCodes().isEmpty();
        if ((!targeted || item.getAllowedPlanCodes().contains(plan.getCode()))
                && planOwnsFiniteQuota(data, plan, item)) {
            return true;
        }
        for (String addOnCode : selectedAddOns) {
            if (targeted && !item.getAllowedAddOnCodes().contains(addOnCode)) continue;
            AddOn addOn = data.addOnsByCode().get(addOnCode);
            if (addOn != null && addOn.getFeatures().stream()
                    .filter(feature -> feature.getFeature().getCode()
                            .equals(item.getFeature().getCode()))
                    .anyMatch(feature -> hasFiniteQuota(
                            feature.getQuotaConfigs(), item.getResource()))) {
                return true;
            }
        }
        return false;
    }

    private void addPlanPolicyIssues(
            Plan plan,
            PlanExtensionPolicy extensionPolicy,
            String productCode,
            Set<String> allowedPlanCodes,
            Set<String> blockedPlanCodes,
            List<ExtensionAvailabilityIssue> issues
    ) {
        if (extensionPolicy == PlanExtensionPolicy.CLOSED) {
            issues.add(issue(ExtensionAvailabilityReason.PLAN_EXTENSIONS_CLOSED,
                    ExtensionResolutionSource.PLAN_POLICY, plan.getCode()));
        }
        if (blockedPlanCodes != null && blockedPlanCodes.contains(plan.getCode())) {
            issues.add(issue(ExtensionAvailabilityReason.PLAN_EXPLICITLY_BLOCKED,
                    ExtensionResolutionSource.PRODUCT_TARGETING, productCode));
        }
        boolean explicitlyAllowed = allowedPlanCodes != null && allowedPlanCodes.contains(plan.getCode());
        if (extensionPolicy == PlanExtensionPolicy.ALLOW_LIST && !explicitlyAllowed) {
            issues.add(issue(ExtensionAvailabilityReason.NOT_ALLOW_LISTED,
                    ExtensionResolutionSource.PLAN_POLICY, productCode));
        } else if (extensionPolicy == PlanExtensionPolicy.OPEN_COMPATIBLE
                && allowedPlanCodes != null && !allowedPlanCodes.isEmpty() && !explicitlyAllowed) {
            issues.add(issue(ExtensionAvailabilityReason.OPTIONAL_TARGETING_EXCLUDED,
                    ExtensionResolutionSource.PRODUCT_TARGETING, productCode));
        }
    }

    private void addFeatureIssues(
            CatalogData data,
            Feature feature,
            Audience audience,
            List<ExtensionAvailabilityIssue> issues
    ) {
        FeatureDefinition definition = data.definitionsByCode().get(feature.getCode());
        if (definition == null) {
            issues.add(issue(ExtensionAvailabilityReason.FEATURE_DEFINITION_MISSING,
                    ExtensionResolutionSource.REGISTRY, feature.getCode()));
            return;
        }
        if (!definition.planAssignable()) {
            issues.add(issue(ExtensionAvailabilityReason.FEATURE_NOT_PLAN_ASSIGNABLE,
                    ExtensionResolutionSource.REGISTRY, feature.getCode()));
        }
        if ((feature.getStatus() != FeatureStatus.PUBLIC && feature.getStatus() != FeatureStatus.BETA)
                || (audience == Audience.CLIENT_CATALOG
                    && (!definition.publicCatalogVisible() || !feature.isPublicVisible()))) {
            issues.add(issue(ExtensionAvailabilityReason.FEATURE_NOT_CLIENT_FACING,
                    ExtensionResolutionSource.REGISTRY, feature.getCode()));
        }
        if (!feature.isNewSalesEnabled()) {
            issues.add(issue(ExtensionAvailabilityReason.FEATURE_NEW_SALES_DISABLED,
                    ExtensionResolutionSource.REGISTRY, feature.getCode()));
        }
        if (!feature.isRuntimeEnabled()) {
            issues.add(issue(ExtensionAvailabilityReason.FEATURE_RUNTIME_DISABLED,
                    ExtensionResolutionSource.REGISTRY, feature.getCode()));
        }
    }

    private boolean isStaticallyEligiblePlanFeature(FeatureDefinition definition, Audience audience) {
        return definition.planAssignable()
                && (audience == Audience.AUTHORIZED_OPERATOR || definition.publicCatalogVisible());
    }

    private CatalogData load() {
        PageRequest productBound = PageRequest.of(0, MAX_SCOPED_PRODUCTS + 1,
                Sort.by(Sort.Direction.ASC, "id"));
        List<Plan> plans = planRepository.findAllByOrderByIdAsc(productBound);
        List<AddOn> addOnRows = addOnRepository.findAllByOrderByIdAsc(productBound);
        List<QuotaPackage> packageRows = quotaPackageRepository.findAllByOrderByIdAsc(productBound);
        requireCatalogProductBound(plans.size(), addOnRows.size(), packageRows.size());

        List<UUID> planIds = plans.stream().map(Plan::getId).toList();
        List<UUID> addOnIds = addOnRows.stream().map(AddOn::getId).toList();
        List<UUID> packageIds = packageRows.stream().map(QuotaPackage::getId).toList();
        List<PlanFeature> planFeatures = planFeatureRepository.findAllByPlanIds(
                idsOrSentinel(planIds));
        List<AddOn> addOns = addOnRepository.findAllDetailedByIdIn(idsOrSentinel(addOnIds));
        List<QuotaPackage> packages = quotaPackageRepository.findAllDetailedByIdIn(
                idsOrSentinel(packageIds));
        List<ProductPrice> prices = boundedApplicablePrices(planIds, addOnIds, packageIds);
        Map<String, FeatureDefinition> definitions =
                featureDefinitionCollectorProvider.getObject().collectByCode();
        Map<UUID, List<PlanFeature>> featuresByPlan = planFeatures.stream()
                .collect(Collectors.groupingBy(item -> item.getPlan().getId()));
        Map<PriceOwnerKey, List<ProductPrice>> pricesByOwner = prices.stream()
                .collect(Collectors.groupingBy(
                        price -> new PriceOwnerKey(price.getOwnerType(), price.ownerId()),
                        LinkedHashMap::new, Collectors.toList()));
        return new CatalogData(
                plans,
                plans.stream().collect(Collectors.toMap(Plan::getId, Function.identity())),
                Map.copyOf(featuresByPlan),
                addOns,
                addOns.stream().collect(Collectors.toMap(AddOn::getCode, Function.identity())),
                packages,
                packages.stream().collect(Collectors.toMap(QuotaPackage::getCode, Function.identity())),
                definitions,
                Map.copyOf(pricesByOwner));
    }

    private CatalogData loadScoped(
            UUID planId,
            Collection<UUID> addOnCandidateIds,
            Collection<UUID> quotaPackageCandidateIds,
            Set<String> selectedAddOnCodes,
            Set<String> retainedQuotaPackageCodes
    ) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new com.hiveapp.shared.exception.ResourceNotFoundException(
                        "Plan", "id", planId));
        List<PlanFeature> planFeatures = planFeatureRepository.findAllByPlanId(planId);

        Map<String, AddOn> addOnsByCode = new LinkedHashMap<>();
        Collection<UUID> candidateIds = addOnCandidateIds == null ? List.of() : addOnCandidateIds;
        addOnRepository.findAllDetailedByIdIn(
                        candidateIds.isEmpty() ? List.of(EMPTY_QUERY_SENTINEL) : candidateIds)
                .forEach(item -> addOnsByCode.put(item.getCode(), item));
        Set<String> pendingCodes = new LinkedHashSet<>(
                selectedAddOnCodes == null ? Set.of() : selectedAddOnCodes);
        addOnsByCode.values().forEach(item -> pendingCodes.addAll(item.getDependencyCodes()));
        while (!pendingCodes.isEmpty()) {
            if (addOnsByCode.size() + pendingCodes.size() > MAX_SCOPED_PRODUCTS) {
                throw new com.hiveapp.shared.exception.InvalidRequestException(
                        "The override candidate dependency graph exceeds 300 products.");
            }
            List<String> batch = pendingCodes.stream()
                    .filter(code -> !addOnsByCode.containsKey(code))
                    .sorted().toList();
            pendingCodes.clear();
            if (batch.isEmpty()) break;
            List<AddOn> found = addOnRepository.findAllByCodeIn(batch);
            found.forEach(item -> {
                addOnsByCode.put(item.getCode(), item);
                item.getDependencyCodes().stream()
                        .filter(code -> !addOnsByCode.containsKey(code))
                        .forEach(pendingCodes::add);
            });
        }
        List<AddOn> addOns = addOnsByCode.values().stream()
                .sorted(Comparator.comparing(AddOn::getCode)).toList();

        Map<String, QuotaPackage> packagesByCode = new LinkedHashMap<>();
        Collection<UUID> packageIds = quotaPackageCandidateIds == null
                ? List.of() : quotaPackageCandidateIds;
        quotaPackageRepository.findAllDetailedByIdIn(
                        packageIds.isEmpty() ? List.of(EMPTY_QUERY_SENTINEL) : packageIds)
                .forEach(item -> packagesByCode.put(item.getCode(), item));
        Set<String> retainedCodes = retainedQuotaPackageCodes == null
                ? Set.of() : retainedQuotaPackageCodes;
        if (retainedCodes.size() + packagesByCode.size() > MAX_SCOPED_PRODUCTS) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "The override package candidate graph exceeds 300 products.");
        }
        if (!retainedCodes.isEmpty()) {
            quotaPackageRepository.findAllByCodeIn(retainedCodes)
                    .forEach(item -> packagesByCode.putIfAbsent(item.getCode(), item));
        }
        List<QuotaPackage> packages = packagesByCode.values().stream()
                .sorted(Comparator.comparing(QuotaPackage::getCode)).toList();

        List<UUID> addOnIds = addOns.stream().map(AddOn::getId).toList();
        List<UUID> scopedPackageIds = packages.stream().map(QuotaPackage::getId).toList();
        List<ProductPrice> prices = boundedApplicablePrices(
                List.of(planId), addOnIds, scopedPackageIds);
        Map<PriceOwnerKey, List<ProductPrice>> pricesByOwner = prices.stream()
                .collect(Collectors.groupingBy(
                        price -> new PriceOwnerKey(price.getOwnerType(), price.ownerId()),
                        LinkedHashMap::new, Collectors.toList()));
        return new CatalogData(
                List.of(plan), Map.of(planId, plan), Map.of(planId, List.copyOf(planFeatures)),
                List.copyOf(addOns), Map.copyOf(addOnsByCode),
                List.copyOf(packages), Map.copyOf(packagesByCode),
                featureDefinitionCollectorProvider.getObject().collectByCode(),
                Map.copyOf(pricesByOwner));
    }

    private void requireCatalogProductBound(int plans, int addOns, int packages) {
        if (plans + addOns + packages > MAX_SCOPED_PRODUCTS) {
            throw new com.hiveapp.shared.exception.InvalidStateException(
                    "The commercial catalogue exceeds the supported 300-product operation bound; "
                            + "use a scoped operation or add catalogue pagination before proceeding.");
        }
    }

    private Collection<UUID> idsOrSentinel(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of(EMPTY_QUERY_SENTINEL) : ids;
    }

    private List<ProductPrice> boundedApplicablePrices(
            Collection<UUID> planIds,
            Collection<UUID> addOnIds,
            Collection<UUID> quotaPackageIds
    ) {
        List<ProductPrice> prices = productPriceRepository.findAllApplicableForOwnersBounded(
                idsOrSentinel(planIds), idsOrSentinel(addOnIds), idsOrSentinel(quotaPackageIds),
                clock.instant(), PageRequest.of(0, MAX_CATALOG_PRICES + 1,
                        Sort.by(Sort.Direction.ASC, "id")));
        if (prices.size() > MAX_CATALOG_PRICES) {
            throw new com.hiveapp.shared.exception.InvalidStateException(
                    "The commercial catalogue exceeds the supported 1200-active-price operation bound; "
                            + "narrow the operation or add catalogue pagination before proceeding.");
        }
        return prices;
    }

    private void addPriceIssues(
            List<ProductPrice> prices,
            String productCode,
            List<ExtensionAvailabilityIssue> issues
    ) {
        if (prices.isEmpty()) {
            issues.add(issue(ExtensionAvailabilityReason.PRICE_UNAVAILABLE,
                    ExtensionResolutionSource.PRICE_BOOK, productCode));
            return;
        }
    }

    private List<ProductPrice> prices(
            CatalogData data, ProductPriceOwnerType type, UUID ownerId) {
        return data.pricesByOwner().getOrDefault(new PriceOwnerKey(type, ownerId), List.of());
    }

    private boolean hasFiniteQuota(List<QuotaLimitEntry> quotas, String resource) {
        return quotas != null && quotas.stream().anyMatch(quota -> quota.resource().equals(resource)
                && quota.mode() == QuotaLimitMode.FINITE);
    }

    private static ExtensionAvailabilityIssue issue(
            ExtensionAvailabilityReason reason, ExtensionResolutionSource source, String sourceCode) {
        return new ExtensionAvailabilityIssue(reason, source, sourceCode);
    }

    private List<ExtensionAvailabilityIssue> distinctIssues(List<ExtensionAvailabilityIssue> issues) {
        return new ArrayList<>(new LinkedHashSet<>(issues));
    }

    public record PriceTuple(String currencyCode, BillingCycle billingCycle) {
        public PriceTuple {
            currencyCode = com.hiveapp.shared.money.Money.normalizeCurrencyCode(currencyCode);
            if (billingCycle != BillingCycle.MONTHLY && billingCycle != BillingCycle.YEARLY) {
                throw new IllegalArgumentException("Commercial selection supports MONTHLY or YEARLY only");
            }
        }

        public static PriceTuple from(ProductPrice price) {
            return new PriceTuple(price.getCurrencyCode(), price.getBillingCycle());
        }

        public boolean matches(ProductPrice price) {
            return currencyCode.equals(price.getCurrencyCode()) && billingCycle == price.getBillingCycle();
        }
    }

    public record CatalogResolution(List<PlanResolution> plans) {}

    public record OverrideCandidateResolution(
            PlanResolution plan,
            Map<String, AddOnCandidateDecision> addOns,
            Map<String, QuotaPackageCandidateDecision> quotaPackages
    ) {}

    public record AddOnCandidateDecision(
            AddOnResolution product,
            SelectionResolution proposedSelection,
            boolean selectable
    ) {}

    public record AddOnActivationResolution(
            PlanResolution plan,
            AddOnResolution addOn
    ) {
        public boolean selectable() {
            return plan.selectable() && addOn.selectable();
        }

        public List<ExtensionAvailabilityIssue> issues() {
            return java.util.stream.Stream.concat(
                            plan.issues().stream(), addOn.issues().stream())
                    .distinct()
                    .toList();
        }
    }

    public record QuotaPackageCandidateDecision(
            QuotaPackageResolution product,
            SelectionResolution proposedSelection,
            boolean selectable
    ) {}

    public record ExactSelectionCandidate(
            UUID key,
            UUID planId,
            PriceTuple tuple,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages
    ) {
        public ExactSelectionCandidate {
            java.util.Objects.requireNonNull(key, "Candidate key is required");
            java.util.Objects.requireNonNull(planId, "Candidate Plan is required");
            java.util.Objects.requireNonNull(tuple, "Candidate price tuple is required");
            addOnCodes = Set.copyOf(addOnCodes == null ? Set.of() : addOnCodes);
            quotaPackages = List.copyOf(quotaPackages == null ? List.of() : quotaPackages);
        }
    }

    public record PlanResolution(
            Plan plan,
            List<ExtensionAvailabilityIssue> issues,
            List<ProductPrice> prices,
            List<PlanFeature> planFeatures,
            List<AddOnResolution> addOns,
            List<QuotaPackageResolution> quotaPackages,
            PlanExtensionPolicy effectiveExtensionPolicy,
            ProductSalesVisibility effectiveSalesVisibility
    ) {
        public boolean selectable() { return issues.isEmpty(); }
        public boolean clientVisible() {
            return selectable() && effectiveSalesVisibility == ProductSalesVisibility.PUBLIC;
        }
    }

    public record AddOnResolution(
            AddOn addOn,
            List<ExtensionAvailabilityIssue> issues,
            List<ProductPrice> prices,
            Set<PriceTuple> supportedTuples,
            Set<String> dependencyClosureCodes
    ) {
        public AddOnResolution {
            issues = List.copyOf(issues);
            prices = List.copyOf(prices);
            supportedTuples = Set.copyOf(supportedTuples);
            dependencyClosureCodes = Set.copyOf(dependencyClosureCodes);
        }
        public String code() { return addOn.getCode(); }
        public boolean selectable() { return issues.isEmpty(); }
        public boolean clientVisible() {
            return selectable() && addOn.getSalesVisibility() == ProductSalesVisibility.PUBLIC;
        }
    }

    public record QuotaPackageResolution(
            QuotaPackage quotaPackage,
            List<ExtensionAvailabilityIssue> issues,
            List<ProductPrice> prices,
            boolean directlySelectable,
            Set<String> requiredAddOnCodes,
            Set<PriceTuple> supportedTuples
    ) {
        public QuotaPackageResolution {
            issues = List.copyOf(issues);
            prices = List.copyOf(prices);
            requiredAddOnCodes = Set.copyOf(requiredAddOnCodes);
            supportedTuples = Set.copyOf(supportedTuples);
        }
        public String code() { return quotaPackage.getCode(); }
        public boolean selectable() { return issues.isEmpty(); }
        public boolean clientVisible() {
            return selectable() && quotaPackage.getSalesVisibility() == ProductSalesVisibility.PUBLIC;
        }
    }

    public record SelectionResolution(
            PlanResolution plan,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages,
            List<ExtensionAvailabilityIssue> issues,
            Map<String, QuotaPackageResolution> packageResolutions
    ) {
        public boolean selectable() {
            return plan.selectable() && issues.isEmpty();
        }
    }

    public record RetainedSelection(
            Set<String> addOnCodes,
            Map<String, Integer> quotaPackageQuantities,
            UUID heldPlanPriceEntryId
    ) {
        public RetainedSelection {
            addOnCodes = Set.copyOf(addOnCodes == null ? Set.of() : addOnCodes);
            quotaPackageQuantities = Map.copyOf(
                    quotaPackageQuantities == null ? Map.of() : quotaPackageQuantities);
            if (quotaPackageQuantities.values().stream()
                    .anyMatch(quantity -> quantity == null || quantity < 1)) {
                throw new IllegalArgumentException("Retained quota-package quantities must be positive");
            }
        }

        public RetainedSelection(Set<String> addOnCodes, Map<String, Integer> quotaPackageQuantities) {
            this(addOnCodes, quotaPackageQuantities, null);
        }

        public static RetainedSelection none() {
            return new RetainedSelection(Set.of(), Map.of(), null);
        }
    }

    /** Maps a resolver decision to the audience-safe API failure contract. */
    public static void requireSelectable(SelectionResolution resolved, Audience audience) {
        if (resolved.selectable()) return;
        if (audience == Audience.CLIENT_CATALOG) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "The requested commercial selection is unavailable.");
        }
        throw new com.hiveapp.shared.exception.OperationBlockedException(
                "The requested commercial selection is unavailable.",
                java.util.stream.Stream.concat(
                                resolved.plan().issues().stream(), resolved.issues().stream())
                        .map(issue -> issue.reason().name() + ":" + issue.source().name())
                        .distinct().toList());
    }

    private record PriceOwnerKey(ProductPriceOwnerType type, UUID id) {}

    private record CatalogData(
            List<Plan> plans,
            Map<UUID, Plan> plansById,
            Map<UUID, List<PlanFeature>> planFeaturesByPlanId,
            List<AddOn> addOns,
            Map<String, AddOn> addOnsByCode,
            List<QuotaPackage> packages,
            Map<String, QuotaPackage> packagesByCode,
            Map<String, FeatureDefinition> definitionsByCode,
            Map<PriceOwnerKey, List<ProductPrice>> pricesByOwner
    ) {}
}
