package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SubscriptionSnapshotFactory {

    private final PlanFeatureRepository planFeatureRepository;

    public SubscriptionEntitlementSnapshot fromPlan(Plan plan) {
        return fromPlan(plan, Set.of());
    }

    public SubscriptionEntitlementSnapshot fromPlan(Plan plan, Set<String> selectedAddOnFeatureCodes) {
        Set<String> addOns = selectedAddOnFeatureCodes != null ? selectedAddOnFeatureCodes : Set.of();
        var features = new ArrayList<>(planFeatureRepository.findAllByPlanId(plan.getId()).stream()
                .filter(planFeature -> planFeature.getAddOnPrice() == null
                        || addOns.contains(planFeature.getFeature().getCode()))
                .map(planFeature -> new SubscriptionFeatureSnapshot(
                        planFeature.getFeature().getCode(),
                        planFeature.getAddOnPrice(),
                        planFeature.getQuotaConfigs() != null ? planFeature.getQuotaConfigs() : java.util.List.of()))
                .sorted(Comparator.comparing(SubscriptionFeatureSnapshot::featureCode))
                .toList());

        return new SubscriptionEntitlementSnapshot(
                plan.getCode(),
                plan.getPrice(),
                features
        );
    }
}
