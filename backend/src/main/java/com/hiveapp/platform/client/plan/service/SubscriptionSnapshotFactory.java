package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionSnapshotFactory {

    private final PlanFeatureRepository planFeatureRepository;
    private final AddOnRepository addOnRepository;
    private final AddOnFeatureRepository addOnFeatureRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final ProductPriceResolver productPriceResolver;

    public SubscriptionEntitlementSnapshot fromPlan(Plan plan) {
        return fromPlan(plan, Set.of(), List.of());
    }

    public SubscriptionEntitlementSnapshot fromPlan(Plan plan, Set<String> selectedAddOnCodes) {
        return fromPlan(plan, selectedAddOnCodes, List.of());
    }

    public SubscriptionEntitlementSnapshot fromPlan(
            Plan plan,
            Set<String> selectedAddOnCodes,
            List<QuotaPackageSelection> selectedQuotaPackages
    ) {
        return fromPlan(plan, selectedAddOnCodes, selectedQuotaPackages,
                productPriceResolver.resolvePlan(plan, null));
    }

    public SubscriptionEntitlementSnapshot fromPlan(
            Plan plan,
            Set<String> selectedAddOnCodes,
            List<QuotaPackageSelection> selectedQuotaPackages,
            ProductPrice planPrice
    ) {
        return fromPlan(plan, selectedAddOnCodes, selectedQuotaPackages,
                PriceTerms.from(planPrice), Map.of(), Map.of());
    }

    /**
     * Rebuilds entitlements without repricing commercial items already present in a subscription.
     * Newly selected add-ons or quota packages still resolve against the current plan price tuple.
     */
    public SubscriptionEntitlementSnapshot fromPlanPreservingPrices(
            Plan plan,
            Set<String> selectedAddOnCodes,
            List<QuotaPackageSelection> selectedQuotaPackages,
            SubscriptionEntitlementSnapshot currentSnapshot
    ) {
        if (!plan.getCode().equals(currentSnapshot.planCode())) {
            throw new IllegalArgumentException("Existing snapshot does not belong to the supplied plan.");
        }
        Map<String, SubscriptionAddOnSnapshot> existingAddOns = currentSnapshot.addOns().stream()
                .collect(java.util.stream.Collectors.toMap(
                        SubscriptionAddOnSnapshot::code, java.util.function.Function.identity()));
        Map<String, SubscriptionQuotaPackageSnapshot> existingPackages = currentSnapshot.quotaPackages().stream()
                .collect(java.util.stream.Collectors.toMap(
                        SubscriptionQuotaPackageSnapshot::code, java.util.function.Function.identity()));
        PriceTerms planPrice = new PriceTerms(
                currentSnapshot.basePrice(), currentSnapshot.currencyCode(), currentSnapshot.billingCycle(),
                currentSnapshot.planPriceEntryId());
        return fromPlan(plan, selectedAddOnCodes, selectedQuotaPackages,
                planPrice, existingAddOns, existingPackages);
    }

    private SubscriptionEntitlementSnapshot fromPlan(
            Plan plan,
            Set<String> selectedAddOnCodes,
            List<QuotaPackageSelection> selectedQuotaPackages,
            PriceTerms planPrice,
            Map<String, SubscriptionAddOnSnapshot> existingAddOns,
            Map<String, SubscriptionQuotaPackageSnapshot> existingPackages
    ) {
        Set<String> requestedCodes = selectedAddOnCodes != null ? selectedAddOnCodes : Set.of();
        Map<String, SubscriptionFeatureSnapshot> features = new LinkedHashMap<>();
        planFeatureRepository.findAllByPlanId(plan.getId()).stream()
                .filter(planFeature -> planFeature.getMode() == PlanFeatureMode.INCLUDED)
                .forEach(planFeature -> features.put(
                        planFeature.getFeature().getCode(),
                        new SubscriptionFeatureSnapshot(
                                planFeature.getFeature().getCode(),
                                planFeature.getQuotaConfigs() != null
                                        ? planFeature.getQuotaConfigs()
                                        : List.of())));

        var addOns = addOnRepository.findAllByCodeIn(requestedCodes).stream()
                .sorted(Comparator.comparing(com.hiveapp.platform.client.plan.domain.entity.AddOn::getCode))
                .map(addOn -> {
                    List<String> featureCodes = addOnFeatureRepository.findAllByAddOnId(addOn.getId()).stream()
                            .sorted(Comparator.comparing(feature -> feature.getFeature().getCode()))
                            .map(addOnFeature -> {
                                String featureCode = addOnFeature.getFeature().getCode();
                                if (features.putIfAbsent(featureCode, new SubscriptionFeatureSnapshot(
                                        featureCode,
                                        addOnFeature.getQuotaConfigs() != null
                                                ? addOnFeature.getQuotaConfigs()
                                                : List.of())) != null) {
                                    throw new IllegalStateException(
                                            "Selected AddOns overlap entitlement for feature " + featureCode);
                                }
                                return featureCode;
                            })
                            .toList();
                    SubscriptionAddOnSnapshot existing = existingAddOns.get(addOn.getCode());
                    PriceTerms addOnPrice = existing == null
                            ? PriceTerms.from(productPriceResolver.resolveAddOn(
                                    addOn, planPrice.currencyCode(), planPrice.billingCycle()))
                            : PriceTerms.from(existing);
                    return new SubscriptionAddOnSnapshot(
                            addOn.getCode(), addOn.getName(), addOn.getDefinitionVersion(),
                            addOnPrice.amount(), addOnPrice.currencyCode(), addOnPrice.billingCycle(),
                            featureCodes, addOnPrice.priceEntryId());
                })
                .toList();

        if (addOns.size() != requestedCodes.size()) {
            throw new IllegalStateException("One or more selected AddOns no longer exist");
        }

        List<QuotaPackageSelection> packageSelections = selectedQuotaPackages != null
                ? selectedQuotaPackages
                : List.of();
        Set<String> packageCodes = packageSelections.stream()
                .map(QuotaPackageSelection::packageCode)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, com.hiveapp.platform.client.plan.domain.entity.QuotaPackage> packagesByCode =
                quotaPackageRepository.findAllByCodeIn(packageCodes).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.hiveapp.platform.client.plan.domain.entity.QuotaPackage::getCode,
                                java.util.function.Function.identity()));
        if (packagesByCode.size() != packageCodes.size()) {
            throw new IllegalStateException("One or more selected quota packages no longer exist");
        }
        var quotaPackages = packageSelections.stream()
                .sorted(Comparator.comparing(QuotaPackageSelection::packageCode))
                .map(selection -> {
                    var item = packagesByCode.get(selection.packageCode());
                    SubscriptionQuotaPackageSnapshot existing = existingPackages.get(item.getCode());
                    PriceTerms packagePrice = existing == null
                            ? PriceTerms.from(productPriceResolver.resolveQuotaPackage(
                                    item, planPrice.currencyCode(), planPrice.billingCycle()))
                            : PriceTerms.from(existing);
                    return new SubscriptionQuotaPackageSnapshot(
                            item.getCode(), item.getName(), item.getDefinitionVersion(),
                            item.getFeature().getCode(), item.getResource(), item.getCapacityPerUnit(),
                            selection.quantity(), packagePrice.amount(), packagePrice.currencyCode(),
                            packagePrice.billingCycle(), packagePrice.priceEntryId());
                })
                .toList();

        return new SubscriptionEntitlementSnapshot(
                SubscriptionEntitlementSnapshot.CURRENT_SCHEMA_VERSION,
                plan.getCode(),
                plan.getName(),
                plan.getRevisionNumber(),
                planPrice.amount(),
                planPrice.currencyCode(),
                planPrice.billingCycle(),
                null,
                null,
                features.values().stream()
                        .sorted(Comparator.comparing(SubscriptionFeatureSnapshot::featureCode))
                        .toList(),
                addOns,
                quotaPackages,
                planPrice.priceEntryId()
        );
    }

    private record PriceTerms(
            BigDecimal amount,
            String currencyCode,
            BillingCycle billingCycle,
            UUID priceEntryId
    ) {
        private static PriceTerms from(ProductPrice price) {
            return new PriceTerms(price.getAmount(), price.getCurrencyCode(), price.getBillingCycle(), price.getId());
        }

        private static PriceTerms from(SubscriptionAddOnSnapshot price) {
            return new PriceTerms(price.price(), price.currencyCode(), price.billingCycle(), price.priceEntryId());
        }

        private static PriceTerms from(SubscriptionQuotaPackageSnapshot price) {
            return new PriceTerms(
                    price.unitPrice(), price.currencyCode(), price.billingCycle(), price.priceEntryId());
        }
    }
}
