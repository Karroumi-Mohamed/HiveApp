package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageActivationBlocker;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.dto.ProductActivationPriceDto;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.quota.QuotaLimitMode;
import com.hiveapp.shared.quota.QuotaSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes a capacity-package activation decision over the exact dependency snapshot loaded for
 * the caller. The mutation path requests locked dependencies; preview uses the same ordering
 * without locks and is fenced by the commercial catalogue revision.
 */
@Service
@RequiredArgsConstructor
public class QuotaPackageActivationAssessor {

    private static final UUID EMPTY_QUERY_SENTINEL = new UUID(0L, 0L);

    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final AddOnFeatureRepository addOnFeatureRepository;
    private final FeatureRepository featureRepository;
    private final ProductPriceRepository productPriceRepository;

    public Assessment assessPreview(
            QuotaPackage item,
            List<QuotaPackage> lineage,
            List<ProductPrice> prices,
            Instant evaluatedAt
    ) {
        return assess(item, lineage, prices, false, evaluatedAt);
    }

    /**
     * Rebuilds the decision from dependency rows locked for the enclosing catalogue mutation.
     * The caller remains responsible for acquiring the global catalogue lock first.
     */
    public Assessment assessForMutation(
            QuotaPackage item,
            List<QuotaPackage> lineage,
            List<ProductPrice> prices,
            Instant evaluatedAt
    ) {
        return assess(item, lineage, prices, true, evaluatedAt);
    }

    private Assessment assess(
            QuotaPackage item,
            List<QuotaPackage> lineage,
            List<ProductPrice> prices,
            boolean lockDependencies,
            Instant evaluatedAt
    ) {
        Dependencies dependencies = loadDependencies(item, lockDependencies);
        EnumSet<QuotaPackageActivationBlocker> blockers =
                EnumSet.noneOf(QuotaPackageActivationBlocker.class);
        if (item.getStatus() == QuotaPackageStatus.ARCHIVED) {
            blockers.add(QuotaPackageActivationBlocker.ARCHIVED_TERMINAL);
        } else if (item.getStatus() != QuotaPackageStatus.DRAFT
                && item.getStatus() != QuotaPackageStatus.INACTIVE) {
            blockers.add(QuotaPackageActivationBlocker.WRONG_LIFECYCLE_STATE);
        }
        int maximumRevision = lineage.stream()
                .mapToInt(QuotaPackage::getRevisionNumber).max().orElse(0);
        if (item.getRevisionNumber() != maximumRevision) {
            blockers.add(QuotaPackageActivationBlocker.NOT_LATEST_REVISION);
        }

        List<ProductPrice> draftPrices = prices.stream()
                .filter(price -> price.getStatus() == ProductPriceStatus.DRAFT)
                .sorted(Comparator.comparing(ProductPrice::getId))
                .toList();
        List<ProductPrice> activePrices = prices.stream()
                .filter(price -> price.getStatus() == ProductPriceStatus.ACTIVE)
                .filter(price -> price.getEffectiveUntil() == null
                        || price.getEffectiveUntil().isAfter(evaluatedAt))
                .sorted(Comparator.comparing(ProductPrice::getId))
                .toList();
        List<ProductPrice> reviewed = !draftPrices.isEmpty() ? draftPrices : activePrices;
        if (reviewed.isEmpty()) blockers.add(QuotaPackageActivationBlocker.NO_REVIEWABLE_PRICE);

        boolean applicableAfterPublication = prices.stream().anyMatch(price ->
                (price.getStatus() == ProductPriceStatus.ACTIVE
                        || price.getStatus() == ProductPriceStatus.DRAFT)
                        && !price.getEffectiveFrom().isAfter(evaluatedAt)
                        && (price.getEffectiveUntil() == null
                            || price.getEffectiveUntil().isAfter(evaluatedAt)));
        if (!applicableAfterPublication) {
            blockers.add(QuotaPackageActivationBlocker.NO_APPLICABLE_PRICE);
        }
        if (reviewed.stream().anyMatch(price -> price.getEffectiveUntil() != null
                && !price.getEffectiveUntil().isAfter(evaluatedAt))) {
            blockers.add(QuotaPackageActivationBlocker.EXPIRED_PRICE_WINDOW);
        }
        if (hasPriceOverlap(reviewed, prices)) {
            blockers.add(QuotaPackageActivationBlocker.OVERLAPPING_PRICE_DRAFTS);
        }

        if (!reviewed.isEmpty()
                && !blockers.contains(QuotaPackageActivationBlocker.EXPIRED_PRICE_WINDOW)
                && !blockers.contains(QuotaPackageActivationBlocker.OVERLAPPING_PRICE_DRAFTS)) {
            List<ProductPrice> catalog = dependencies.prices().stream()
                    .filter(price -> price.isApplicableAt(evaluatedAt))
                    .collect(Collectors.toCollection(ArrayList::new));
            prices.stream().filter(price -> price.isApplicableAt(evaluatedAt)).forEach(catalog::add);
            reviewed.stream()
                    .filter(price -> price.getStatus() == ProductPriceStatus.DRAFT)
                    .forEach(catalog::add);
            try {
                validateCompatibility(item, catalog, dependencies);
            } catch (BusinessException exception) {
                blockers.add(QuotaPackageActivationBlocker.TARGET_COMPATIBILITY_INVALID);
            }
        }

        List<UUID> packagesToDeactivate = lineage.stream()
                .filter(candidate -> !candidate.getId().equals(item.getId()))
                .filter(candidate -> candidate.getStatus() == QuotaPackageStatus.ACTIVE)
                .map(QuotaPackage::getId)
                .sorted()
                .toList();
        String fingerprint = fingerprint(
                item, lineage, prices, dependencies, blockers, evaluatedAt);
        return new Assessment(
                List.copyOf(blockers),
                reviewed.stream().map(ActivationAssessmentFingerprint::toDto).toList(),
                packagesToDeactivate,
                fingerprint);
    }

    private Dependencies loadDependencies(QuotaPackage item, boolean forUpdate) {
        List<String> requestedPlanCodes = item.getAllowedPlanCodes().stream().sorted().toList();
        List<String> requestedAddOnCodes = item.getAllowedAddOnCodes().stream().sorted().toList();
        List<String> addOnClosureCodes = discoverAddOnClosureCodes(requestedAddOnCodes);

        List<Plan> plans = requestedPlanCodes.isEmpty() ? List.of()
                : forUpdate
                        ? planRepository.findAllByCodeInForUpdate(requestedPlanCodes)
                        : planRepository.findAllByCodeInOrderByIdAsc(requestedPlanCodes);
        List<AddOn> addOns = addOnClosureCodes.isEmpty() ? List.of()
                : forUpdate
                        ? addOnRepository.findAllByCodeInForUpdate(addOnClosureCodes)
                        : addOnRepository.findAllByCodeIn(addOnClosureCodes);
        plans = plans.stream().sorted(Comparator.comparing(plan -> plan.getId().toString())).toList();
        addOns = addOns.stream().sorted(Comparator.comparing(addOn -> addOn.getId().toString())).toList();

        List<UUID> planIds = plans.stream().map(Plan::getId).toList();
        List<UUID> addOnIds = addOns.stream().map(AddOn::getId).toList();
        List<PlanFeature> planFeatures = planIds.isEmpty() ? List.of()
                : forUpdate
                        ? planFeatureRepository.findAllByPlanIdsForUpdate(planIds)
                        : planFeatureRepository.findAllByPlanIds(planIds);
        List<AddOnFeature> addOnFeatures = addOnIds.isEmpty() ? List.of()
                : forUpdate
                        ? addOnFeatureRepository.findAllByAddOnIdsForUpdate(addOnIds)
                        : addOnFeatureRepository.findAllByAddOnIds(addOnIds);

        Set<UUID> featureIds = new LinkedHashSet<>();
        featureIds.add(item.getFeature().getId());
        planFeatures.stream().map(feature -> feature.getFeature().getId()).forEach(featureIds::add);
        addOnFeatures.stream().map(feature -> feature.getFeature().getId()).forEach(featureIds::add);
        List<Feature> features = forUpdate
                ? featureRepository.findAllByIdInForUpdate(featureIds)
                : featureRepository.findAllById(featureIds);
        features = features.stream()
                .sorted(Comparator.comparing(feature -> feature.getId().toString())).toList();

        Collection<UUID> pricePlanIds = planIds.isEmpty() ? List.of(EMPTY_QUERY_SENTINEL) : planIds;
        Collection<UUID> priceAddOnIds = addOnIds.isEmpty() ? List.of(EMPTY_QUERY_SENTINEL) : addOnIds;
        List<ProductPrice> dependencyPrices = forUpdate
                ? productPriceRepository.findAllCompatibilityDependencyPricesForUpdate(
                        pricePlanIds, priceAddOnIds)
                : productPriceRepository.findAllCompatibilityDependencyPrices(
                        pricePlanIds, priceAddOnIds);

        Map<String, Plan> plansByCode = plans.stream().collect(Collectors.toUnmodifiableMap(
                Plan::getCode, plan -> plan));
        Map<String, AddOn> addOnsByCode = addOns.stream().collect(Collectors.toUnmodifiableMap(
                AddOn::getCode, addOn -> addOn));
        String dependencyFingerprint = dependencyFingerprint(
                requestedPlanCodes, requestedAddOnCodes, addOnClosureCodes,
                plans, addOns, planFeatures, addOnFeatures, features, dependencyPrices);
        return new Dependencies(
                List.copyOf(planFeatures), List.copyOf(addOnFeatures),
                List.copyOf(dependencyPrices), plansByCode, addOnsByCode, dependencyFingerprint);
    }

    private List<String> discoverAddOnClosureCodes(Collection<String> rootCodes) {
        Set<String> discovered = new TreeSet<>(rootCodes);
        Set<String> expanded = new LinkedHashSet<>();
        while (expanded.size() < discovered.size()) {
            List<String> batch = discovered.stream()
                    .filter(code -> !expanded.contains(code)).toList();
            List<AddOn> found = addOnRepository.findAllByCodeIn(batch);
            expanded.addAll(batch);
            found.stream().flatMap(addOn -> addOn.getDependencyCodes().stream())
                    .forEach(discovered::add);
        }
        return List.copyOf(discovered);
    }

    private String dependencyFingerprint(
            List<String> requestedPlanCodes,
            List<String> requestedAddOnCodes,
            List<String> addOnClosureCodes,
            List<Plan> plans,
            List<AddOn> addOns,
            List<PlanFeature> planFeatures,
            List<AddOnFeature> addOnFeatures,
            List<Feature> features,
            List<ProductPrice> prices
    ) {
        StringBuilder state = new StringBuilder();
        ActivationAssessmentFingerprint.appendValues(state, "requested-plans", requestedPlanCodes);
        ActivationAssessmentFingerprint.appendValues(state, "requested-add-ons", requestedAddOnCodes);
        ActivationAssessmentFingerprint.appendValues(state, "add-on-closure", addOnClosureCodes);
        plans.stream().sorted(Comparator.comparing(plan -> plan.getId().toString())).forEach(plan -> {
            ActivationAssessmentFingerprint.append(state, "plan");
            ActivationAssessmentFingerprint.append(state, plan.getId());
            ActivationAssessmentFingerprint.append(state, plan.getVersion());
            ActivationAssessmentFingerprint.append(state, plan.getCode());
            ActivationAssessmentFingerprint.append(state, plan.getStatus());
            ActivationAssessmentFingerprint.append(state, plan.getExtensionPolicy());
            ActivationAssessmentFingerprint.append(state, plan.getSalesVisibility());
            ActivationAssessmentFingerprint.append(state, plan.getCurrencyCode());
            ActivationAssessmentFingerprint.append(state, plan.getBillingCycle());
        });
        addOns.stream().sorted(Comparator.comparing(addOn -> addOn.getId().toString())).forEach(addOn -> {
            ActivationAssessmentFingerprint.append(state, "add-on");
            ActivationAssessmentFingerprint.append(state, addOn.getId());
            ActivationAssessmentFingerprint.append(state, addOn.getRowVersion());
            ActivationAssessmentFingerprint.append(state, addOn.getDefinitionVersion());
            ActivationAssessmentFingerprint.append(state, addOn.getCode());
            ActivationAssessmentFingerprint.append(state, addOn.getStatus());
            ActivationAssessmentFingerprint.append(state, addOn.getSalesVisibility());
            ActivationAssessmentFingerprint.append(state, addOn.getCurrencyCode());
            ActivationAssessmentFingerprint.append(state, addOn.getBillingCycle());
            ActivationAssessmentFingerprint.appendValues(
                    state, "allowed-plans", addOn.getAllowedPlanCodes().stream().sorted().toList());
            ActivationAssessmentFingerprint.appendValues(
                    state, "blocked-plans", addOn.getBlockedPlanCodes().stream().sorted().toList());
            ActivationAssessmentFingerprint.appendValues(
                    state, "dependencies", addOn.getDependencyCodes().stream().sorted().toList());
            ActivationAssessmentFingerprint.appendValues(
                    state, "exclusions", addOn.getExclusionCodes().stream().sorted().toList());
        });
        planFeatures.stream().sorted(Comparator.comparing(feature -> feature.getId().toString()))
                .forEach(feature -> {
                    ActivationAssessmentFingerprint.append(state, "plan-feature");
                    ActivationAssessmentFingerprint.append(state, feature.getId());
                    ActivationAssessmentFingerprint.append(state, feature.getPlan().getId());
                    ActivationAssessmentFingerprint.append(state, feature.getFeature().getId());
                    ActivationAssessmentFingerprint.append(state, feature.getFeature().getCode());
                    ActivationAssessmentFingerprint.append(state, feature.getMode());
                    ActivationAssessmentFingerprint.appendQuotaEntries(state, feature.getQuotaConfigs());
                });
        addOnFeatures.stream().sorted(Comparator.comparing(feature -> feature.getId().toString()))
                .forEach(feature -> {
                    ActivationAssessmentFingerprint.append(state, "add-on-feature");
                    ActivationAssessmentFingerprint.append(state, feature.getId());
                    ActivationAssessmentFingerprint.append(state, feature.getAddOn().getId());
                    ActivationAssessmentFingerprint.append(state, feature.getFeature().getId());
                    ActivationAssessmentFingerprint.append(state, feature.getFeature().getCode());
                    ActivationAssessmentFingerprint.appendQuotaEntries(state, feature.getQuotaConfigs());
                });
        features.stream().sorted(Comparator.comparing(feature -> feature.getId().toString()))
                .forEach(feature -> {
                    ActivationAssessmentFingerprint.append(state, "registry-feature");
                    ActivationAssessmentFingerprint.append(state, feature.getId());
                    ActivationAssessmentFingerprint.append(state, feature.getCode());
                    ActivationAssessmentFingerprint.append(state, feature.getStatus());
                    ActivationAssessmentFingerprint.append(state, feature.getSortOrder());
                    ActivationAssessmentFingerprint.append(state, feature.isPublicVisible());
                    ActivationAssessmentFingerprint.append(state, feature.isNewSalesEnabled());
                    ActivationAssessmentFingerprint.append(state, feature.isNewGrantsEnabled());
                    ActivationAssessmentFingerprint.append(state, feature.isRuntimeEnabled());
                    ActivationAssessmentFingerprint.append(state, feature.getUpdatedAt());
                    List<QuotaSlot> schema = feature.getQuotaSchema() == null ? List.of()
                            : feature.getQuotaSchema().stream()
                                    .sorted(Comparator.comparing(QuotaSlot::resource)
                                            .thenComparing(slot -> slot.type().name())
                                            .thenComparing(QuotaSlot::unit))
                                    .toList();
                    for (QuotaSlot slot : schema) {
                        ActivationAssessmentFingerprint.append(state, slot.resource());
                        ActivationAssessmentFingerprint.append(state, slot.type());
                        ActivationAssessmentFingerprint.append(state, slot.unit());
                    }
                });
        prices.stream().sorted(Comparator.comparing(price -> price.getId().toString())).forEach(price -> {
            ActivationAssessmentFingerprint.append(state, "dependency-price");
            ActivationAssessmentFingerprint.append(state, price.getId());
            ActivationAssessmentFingerprint.append(state, price.getVersion());
            ActivationAssessmentFingerprint.append(state, price.getOwnerType());
            ActivationAssessmentFingerprint.append(state, price.ownerId());
            ActivationAssessmentFingerprint.append(state, price.getStatus());
            ActivationAssessmentFingerprint.append(state, price.getAmount().toPlainString());
            ActivationAssessmentFingerprint.append(state, price.getCurrencyCode());
            ActivationAssessmentFingerprint.append(state, price.getBillingCycle());
            ActivationAssessmentFingerprint.append(state, price.getEffectiveFrom());
            ActivationAssessmentFingerprint.append(state, price.getEffectiveUntil());
            ActivationAssessmentFingerprint.append(state, price.getLineageId());
            ActivationAssessmentFingerprint.append(state, price.getRevisionNumber());
            ActivationAssessmentFingerprint.append(state, price.isCompatibilityDefault());
        });
        return ActivationAssessmentFingerprint.digest(state.toString());
    }

    private String fingerprint(
            QuotaPackage item,
            List<QuotaPackage> lineage,
            List<ProductPrice> prices,
            Dependencies dependencies,
            Collection<QuotaPackageActivationBlocker> blockers,
            Instant evaluatedAt
    ) {
        String lineageState = lineage.stream()
                .sorted(Comparator.comparing(QuotaPackage::getId))
                .map(candidate -> candidate.getId() + ":" + candidate.getRowVersion() + ":"
                        + candidate.getStatus() + ":" + candidate.getRevisionNumber())
                .collect(Collectors.joining(","));
        String priceState = prices.stream()
                .sorted(Comparator.comparing(ProductPrice::getId))
                .map(price -> String.join(":",
                        price.getId().toString(), Long.toString(price.getVersion()),
                        price.getStatus().name(), price.getAmount().toPlainString(),
                        price.getCurrencyCode(), price.getBillingCycle().name(),
                        price.getEffectiveFrom().toString(),
                        price.getEffectiveUntil() == null ? "OPEN" : price.getEffectiveUntil().toString()))
                .collect(Collectors.joining(","));
        String temporalState = temporalPriceFingerprint(
                java.util.stream.Stream.concat(prices.stream(), dependencies.prices().stream())
                        .distinct().toList(),
                evaluatedAt);
        String blockerState = blockers.stream().map(Enum::name).sorted()
                .collect(Collectors.joining(","));
        return ActivationAssessmentFingerprint.digest(String.join("|",
                item.getId().toString(), Long.toString(item.getRowVersion()), item.getStatus().name(),
                item.getLineageId().toString(), Integer.toString(item.getRevisionNumber()),
                lineageState, priceState, dependencies.fingerprint(), temporalState, blockerState));
    }

    public static String temporalPriceFingerprint(
            Collection<ProductPrice> prices,
            Instant evaluatedAt
    ) {
        return prices.stream()
                .sorted(Comparator.comparing(ProductPrice::getId))
                .map(price -> price.getId() + ":"
                        + ActivationAssessmentFingerprint.priceWindowState(price, evaluatedAt))
                .collect(Collectors.joining(","));
    }

    private boolean hasPriceOverlap(List<ProductPrice> reviewed, List<ProductPrice> allPrices) {
        List<ProductPrice> candidates = new ArrayList<>(reviewed);
        allPrices.stream()
                .filter(price -> price.getStatus() == ProductPriceStatus.ACTIVE)
                .filter(price -> reviewed.stream().noneMatch(
                        selected -> selected.getId().equals(price.getId())))
                .forEach(candidates::add);
        for (int left = 0; left < candidates.size(); left++) {
            for (int right = left + 1; right < candidates.size(); right++) {
                ProductPrice first = candidates.get(left);
                ProductPrice second = candidates.get(right);
                if (first.getCurrencyCode().equals(second.getCurrencyCode())
                        && first.getBillingCycle() == second.getBillingCycle()
                        && windowsOverlap(first, second)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean windowsOverlap(ProductPrice left, ProductPrice right) {
        return (right.getEffectiveUntil() == null
                    || left.getEffectiveFrom().isBefore(right.getEffectiveUntil()))
                && (left.getEffectiveUntil() == null
                    || left.getEffectiveUntil().isAfter(right.getEffectiveFrom()));
    }

    private void validateCompatibility(
            QuotaPackage item,
            List<ProductPrice> catalogPrices,
            Dependencies dependencies
    ) {
        Set<CommercialCatalogResolver.PriceTuple> packageTuples = priceTuples(
                catalogPrices, ProductPriceOwnerType.QUOTA_PACKAGE, item.getId());
        if (packageTuples.isEmpty()) {
            throw new BusinessException(
                    "A quota package requires at least one active price before activation.");
        }
        for (String planCode : item.getAllowedPlanCodes()) {
            Plan plan = dependencies.plansByCode().get(planCode);
            if (plan == null) throw new BusinessException("Allowed Plan no longer exists: " + planCode);
            if (!plan.isActive() || Collections.disjoint(
                    packageTuples, priceTuples(catalogPrices, ProductPriceOwnerType.PLAN, plan.getId()))) {
                throw new BusinessException(
                        "Plan " + planCode + " is not active or has no compatible active price tuple.");
            }
            PlanFeature owner = dependencies.planFeatures().stream()
                    .filter(feature -> feature.getPlan().getId().equals(plan.getId()))
                    .filter(feature -> feature.getFeature().getCode()
                            .equals(item.getFeature().getCode()))
                    .findFirst()
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
            AddOn addOn = dependencies.addOnsByCode().get(addOnCode);
            if (addOn == null) throw new BusinessException("Allowed AddOn no longer exists: " + addOnCode);
            Set<CommercialCatalogResolver.PriceTuple> addOnTuples = compatibleAddOnPriceTuples(
                    addOn, catalogPrices, new LinkedHashSet<>(), dependencies.addOnsByCode());
            if (!addOn.isActive() || Collections.disjoint(packageTuples, addOnTuples)) {
                throw new BusinessException(
                        "AddOn " + addOnCode + " is not active or has no compatible active price tuple.");
            }
            AddOnFeature owner = dependencies.addOnFeatures().stream()
                    .filter(feature -> feature.getAddOn().getId().equals(addOn.getId()))
                    .filter(feature -> feature.getFeature().getCode()
                            .equals(item.getFeature().getCode()))
                    .findFirst()
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

    private Set<CommercialCatalogResolver.PriceTuple> compatibleAddOnPriceTuples(
            AddOn addOn,
            List<ProductPrice> catalogPrices,
            Set<String> visited,
            Map<String, AddOn> addOnsByCode
    ) {
        if (!visited.add(addOn.getCode())) {
            throw new BusinessException("Cyclic AddOn dependency detected at " + addOn.getCode() + ".");
        }
        if (addOn.getStatus() != AddOnStatus.ACTIVE) {
            throw new BusinessException("AddOn dependency " + addOn.getCode() + " must be ACTIVE.");
        }
        Set<CommercialCatalogResolver.PriceTuple> supported = new LinkedHashSet<>(priceTuples(
                catalogPrices, ProductPriceOwnerType.ADD_ON, addOn.getId()));
        for (String dependencyCode : addOn.getDependencyCodes()) {
            AddOn dependency = addOnsByCode.get(dependencyCode);
            if (dependency == null) {
                throw new BusinessException("Missing AddOn dependency " + dependencyCode + ".");
            }
            supported.retainAll(compatibleAddOnPriceTuples(
                    dependency, catalogPrices, visited, addOnsByCode));
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
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasFiniteQuota(
            List<com.hiveapp.shared.quota.QuotaLimitEntry> quotas,
            String resource
    ) {
        return quotas != null && quotas.stream()
                .anyMatch(quota -> quota.resource().equals(resource)
                        && quota.mode() == QuotaLimitMode.FINITE);
    }

    private record Dependencies(
            List<PlanFeature> planFeatures,
            List<AddOnFeature> addOnFeatures,
            List<ProductPrice> prices,
            Map<String, Plan> plansByCode,
            Map<String, AddOn> addOnsByCode,
            String fingerprint
    ) {}

    public record Assessment(
            List<QuotaPackageActivationBlocker> blockers,
            List<ProductActivationPriceDto> reviewedPrices,
            List<UUID> packagesToDeactivate,
            String fingerprint
    ) {
        public Assessment {
            blockers = List.copyOf(blockers);
            reviewedPrices = List.copyOf(reviewedPrices);
            packagesToDeactivate = List.copyOf(packagesToDeactivate);
        }
    }
}
