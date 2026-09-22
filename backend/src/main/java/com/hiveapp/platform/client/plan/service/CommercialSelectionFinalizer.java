package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 * Closes the validation-to-snapshot race for commercial writes. All mutable inputs are locked in
 * a fixed order, the central resolver is run before and after exact price locks, and identity-based
 * pins reject equal-count replacement churn rather than silently materialising a different sale.
 */
@Component
@RequiredArgsConstructor
public class CommercialSelectionFinalizer {

    private final RegistryCatalogVersionService registryCatalogVersionService;
    private final CommercialCatalogVersionService commercialCatalogVersionService;
    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ProductPriceResolver productPriceResolver;
    private final CommercialCatalogResolver catalogResolver;
    private final SubscriptionSnapshotFactory subscriptionSnapshotFactory;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public FinalizedSelection finalizeSelection(
            String planCode,
            ProductPriceSelectionRequest requestedPlanPrice,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages,
            CommercialCatalogResolver.Audience audience,
            CommercialCatalogResolver.RetainedSelection retained,
            SubscriptionEntitlementSnapshot currentSnapshot
    ) {
        return finalizeSelection(
                planCode, requestedPlanPrice, addOnCodes, quotaPackages, audience,
                retained, currentSnapshot,
                CommercialPolicyEvaluator.Evaluation.empty(java.time.Instant.EPOCH));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FinalizedSelection finalizeSelection(
            String planCode,
            ProductPriceSelectionRequest requestedPlanPrice,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages,
            CommercialCatalogResolver.Audience audience,
            CommercialCatalogResolver.RetainedSelection retained,
            SubscriptionEntitlementSnapshot currentSnapshot,
            CommercialPolicyEvaluator.Evaluation policyEvaluation
    ) {
        return finalizeSelection(planCode, requestedPlanPrice, addOnCodes, quotaPackages, audience,
                retained, currentSnapshot, policyEvaluation, Map.of(), Map.of());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FinalizedSelection finalizeSelection(
            String planCode, ProductPriceSelectionRequest requestedPlanPrice,
            Set<String> addOnCodes, List<QuotaPackageSelection> quotaPackages,
            CommercialCatalogResolver.Audience audience,
            CommercialCatalogResolver.RetainedSelection retained,
            SubscriptionEntitlementSnapshot currentSnapshot,
            CommercialPolicyEvaluator.Evaluation policyEvaluation,
            Map<String, UUID> exactAddOnPrices,
            Map<String, UUID> exactPackagePrices
    ) {
        // Keep the catalogue revision stable through the final resolver pass and the caller's
        // transaction commit. Exact product-row locks alone do not cover unselected dependencies
        // or availability policy changes that can still alter the resolved selection.
        commercialCatalogVersionService.lockForMutation();
        registryCatalogVersionService.lockForMutation();
        var plan = planRepository.findByCodeForUpdate(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "code", planCode));

        Set<String> selectedAddOns = Set.copyOf(addOnCodes == null ? Set.of() : addOnCodes);
        List<QuotaPackageSelection> selectedPackages = List.copyOf(
                quotaPackages == null ? List.of() : quotaPackages);
        Set<String> selectedPackageCodes = selectedPackages.stream()
                .map(QuotaPackageSelection::packageCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!selectedAddOns.isEmpty()) {
            addOnRepository.findAllByCodeInForUpdate(selectedAddOns);
        }
        if (!selectedPackageCodes.isEmpty()) {
            quotaPackageRepository.findAllByCodeInForUpdate(selectedPackageCodes);
        }

        ProductPrice preliminaryPlanPrice = selectPlanPrice(plan, requestedPlanPrice, currentSnapshot);
        CommercialCatalogResolver.RetainedSelection effectiveRetained = effectiveRetained(
                retained, preliminaryPlanPrice, currentSnapshot);
        CommercialCatalogResolver.SelectionResolution preliminary =
                CommercialPolicySelectionRules.adjustSelection(catalogResolver.resolveSelection(
                plan, CommercialCatalogResolver.PriceTuple.from(preliminaryPlanPrice),
                selectedAddOns, selectedPackages, audience, effectiveRetained),
                audience, policyEvaluation);
        SelectionPin pin = SelectionPin.capture(
                preliminary, preliminaryPlanPrice, selectedAddOns, selectedPackages, effectiveRetained);

        Set<UUID> priceIds = new LinkedHashSet<>(pin.newSalePriceIds().values());
        priceIds.addAll(exactAddOnPrices.values());
        priceIds.addAll(exactPackagePrices.values());
        addRetainedPriceIds(
                priceIds, currentSnapshot, selectedAddOns, selectedPackageCodes, effectiveRetained);

        // A lock query may otherwise return an already-managed stale ProductPrice. Clearing the
        // persistence context retains database locks while forcing authoritative re-reads.
        entityManager.clear();
        if (!priceIds.isEmpty()) {
            List<ProductPrice> lockedPrices = productPriceRepository.findAllByIdInForUpdate(priceIds);
            if (lockedPrices.size() != priceIds.size()) {
                throw stale();
            }
        }

        var finalPlan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "code", planCode));
        ProductPrice finalPlanPrice = selectPlanPrice(finalPlan, requestedPlanPrice, currentSnapshot);
        CommercialCatalogResolver.SelectionResolution resolved =
                CommercialPolicySelectionRules.adjustSelection(catalogResolver.resolveSelection(
                finalPlan, CommercialCatalogResolver.PriceTuple.from(finalPlanPrice),
                selectedAddOns, selectedPackages, audience, effectiveRetained),
                audience, policyEvaluation);
        SelectionPin finalPin = SelectionPin.capture(
                resolved, finalPlanPrice, selectedAddOns, selectedPackages, effectiveRetained);
        if (!pin.equals(finalPin)) {
            throw stale();
        }
        CommercialCatalogResolver.requireSelectable(resolved, audience);
        SubscriptionEntitlementSnapshot snapshot = subscriptionSnapshotFactory.fromResolvedSelection(
                finalPlan, finalPlanPrice, resolved, currentSnapshot);
        requireExactPrices(snapshot, exactAddOnPrices, exactPackagePrices);
        return new FinalizedSelection(finalPlan, finalPlanPrice, resolved, snapshot);
    }

    private void requireExactPrices(SubscriptionEntitlementSnapshot snapshot,
            Map<String, UUID> exactAddOnPrices, Map<String, UUID> exactPackagePrices) {
        Map<String, UUID> actualAddOns = snapshot.addOns().stream().collect(Collectors.toMap(
                com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::code,
                com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::priceEntryId));
        Map<String, UUID> actualPackages = snapshot.quotaPackages().stream().collect(Collectors.toMap(
                com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::code,
                com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::priceEntryId));
        exactAddOnPrices.forEach((code, id) -> {
            if (!Objects.equals(actualAddOns.get(code), id)) throw stale();
        });
        exactPackagePrices.forEach((code, id) -> {
            if (!Objects.equals(actualPackages.get(code), id)) throw stale();
        });
    }

    private CommercialCatalogResolver.RetainedSelection effectiveRetained(
            CommercialCatalogResolver.RetainedSelection retained,
            ProductPrice selectedPlanPrice,
            SubscriptionEntitlementSnapshot currentSnapshot
    ) {
        if (retained == null || currentSnapshot == null
                || !Objects.equals(currentSnapshot.planPriceEntryId(), selectedPlanPrice.getId())
                || !Objects.equals(retained.heldPlanPriceEntryId(), selectedPlanPrice.getId())) {
            return CommercialCatalogResolver.RetainedSelection.none();
        }
        return retained;
    }

    private ProductPrice selectPlanPrice(
            com.hiveapp.platform.client.plan.domain.entity.Plan plan,
            ProductPriceSelectionRequest requested,
            SubscriptionEntitlementSnapshot currentSnapshot
    ) {
        boolean sameHeldPlan = currentSnapshot != null
                && plan.getCode().equals(currentSnapshot.planCode())
                && currentSnapshot.planPriceEntryId() != null;
        boolean preserveHeldPrice = sameHeldPlan && (requested == null
                || (requested.priceEntryId() != null
                && requested.priceEntryId().equals(currentSnapshot.planPriceEntryId())));
        if (!preserveHeldPrice) {
            return productPriceResolver.resolvePlan(plan, requested);
        }
        return productPriceRepository.findOwned(
                        com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN,
                        currentSnapshot.financialPlanSource() == null ? plan.getId()
                                : currentSnapshot.financialPlanSource().planId(), currentSnapshot.planPriceEntryId())
                .orElseThrow(this::stale);
    }

    private void addRetainedPriceIds(
            Set<UUID> priceIds,
            SubscriptionEntitlementSnapshot snapshot,
            Set<String> selectedAddOns,
            Set<String> selectedPackages,
            CommercialCatalogResolver.RetainedSelection retained
    ) {
        if (snapshot == null) return;
        snapshot.addOns().stream()
                .filter(item -> selectedAddOns.contains(item.code()))
                .filter(item -> retained.addOnCodes().contains(item.code()))
                .map(item -> item.priceEntryId())
                .filter(Objects::nonNull)
                .forEach(priceIds::add);
        snapshot.quotaPackages().stream()
                .filter(item -> selectedPackages.contains(item.code()))
                .filter(item -> retained.quotaPackageQuantities().get(item.code()) != null)
                .map(item -> item.priceEntryId())
                .filter(Objects::nonNull)
                .forEach(priceIds::add);
    }

    private StaleResourceVersionException stale() {
        return new StaleResourceVersionException(
                "Commercial selection changed while it was being finalized. Reload and retry.");
    }

    public record FinalizedSelection(
            com.hiveapp.platform.client.plan.domain.entity.Plan plan,
            ProductPrice planPrice,
            CommercialCatalogResolver.SelectionResolution resolution,
            SubscriptionEntitlementSnapshot snapshot
    ) {}

    private record ProductPin(UUID id, long version) {}

    private record SelectionPin(
            ProductPin plan,
            Map<String, ProductPin> addOns,
            Map<String, ProductPin> quotaPackages,
            Map<String, UUID> newSalePriceIds
    ) {
        private static SelectionPin capture(
                CommercialCatalogResolver.SelectionResolution resolution,
                ProductPrice planPrice,
                Set<String> selectedAddOns,
                List<QuotaPackageSelection> selectedPackages,
                CommercialCatalogResolver.RetainedSelection retained
        ) {
            Map<String, CommercialCatalogResolver.AddOnResolution> addOnResults = resolution.plan().addOns().stream()
                    .filter(item -> selectedAddOns.contains(item.code()))
                    .collect(Collectors.toMap(CommercialCatalogResolver.AddOnResolution::code, Function.identity()));
            Map<String, CommercialCatalogResolver.QuotaPackageResolution> packageResults =
                    resolution.plan().quotaPackages().stream()
                            .filter(item -> selectedPackages.stream()
                                    .anyMatch(selection -> selection.packageCode().equals(item.code())))
                            .collect(Collectors.toMap(
                                    CommercialCatalogResolver.QuotaPackageResolution::code,
                                    Function.identity()));

            Map<String, ProductPin> addOnPins = new LinkedHashMap<>();
            Map<String, ProductPin> packagePins = new LinkedHashMap<>();
            Map<String, UUID> priceIds = new LinkedHashMap<>();
            priceIds.put("PLAN", planPrice.getId());

            selectedAddOns.stream().sorted().forEach(code -> {
                CommercialCatalogResolver.AddOnResolution item = addOnResults.get(code);
                if (item == null) return;
                addOnPins.put(code, new ProductPin(item.addOn().getId(), item.addOn().getRowVersion()));
                if (!retained.addOnCodes().contains(code)) {
                    exactPrice(item.prices()).ifPresent(id -> priceIds.put("ADD_ON:" + code, id));
                }
            });
            selectedPackages.stream()
                    .sorted(java.util.Comparator.comparing(QuotaPackageSelection::packageCode))
                    .forEach(selection -> {
                        CommercialCatalogResolver.QuotaPackageResolution item =
                                packageResults.get(selection.packageCode());
                        if (item == null) return;
                        packagePins.put(item.code(), new ProductPin(
                                item.quotaPackage().getId(), item.quotaPackage().getRowVersion()));
                        Integer retainedQuantity = retained.quotaPackageQuantities().get(item.code());
                        if (retainedQuantity == null || retainedQuantity != selection.quantity()) {
                            exactPrice(item.prices()).ifPresent(
                                    id -> priceIds.put("QUOTA_PACKAGE:" + item.code(), id));
                        }
                    });

            return new SelectionPin(
                    new ProductPin(resolution.plan().plan().getId(), resolution.plan().plan().getVersion()),
                    Map.copyOf(addOnPins), Map.copyOf(packagePins), Map.copyOf(priceIds));
        }

        private static java.util.Optional<UUID> exactPrice(List<ProductPrice> prices) {
            if (prices.size() != 1) return java.util.Optional.empty();
            return java.util.Optional.of(prices.getFirst().getId());
        }
    }
}
