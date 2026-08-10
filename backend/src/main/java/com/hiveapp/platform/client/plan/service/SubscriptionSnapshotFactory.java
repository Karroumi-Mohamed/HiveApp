package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SubscriptionSnapshotFactory {

    private final PlanFeatureRepository planFeatureRepository;
    private final AddOnRepository addOnRepository;
    private final AddOnFeatureRepository addOnFeatureRepository;

    public SubscriptionEntitlementSnapshot fromPlan(Plan plan) {
        return fromPlan(plan, Set.of());
    }

    public SubscriptionEntitlementSnapshot fromPlan(Plan plan, Set<String> selectedAddOnCodes) {
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
                    return new SubscriptionAddOnSnapshot(
                            addOn.getCode(), addOn.getName(), addOn.getDefinitionVersion(),
                            addOn.getPrice(), addOn.getCurrencyCode(), addOn.getBillingCycle(), featureCodes);
                })
                .toList();

        if (addOns.size() != requestedCodes.size()) {
            throw new IllegalStateException("One or more selected AddOns no longer exist");
        }

        return new SubscriptionEntitlementSnapshot(
                plan.getCode(),
                plan.getPrice(),
                plan.getCurrencyCode(),
                plan.getBillingCycle(),
                features.values().stream()
                        .sorted(Comparator.comparing(SubscriptionFeatureSnapshot::featureCode))
                        .toList(),
                addOns
        );
    }
}
