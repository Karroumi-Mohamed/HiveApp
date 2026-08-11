package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.PlanFeatureDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import org.springframework.stereotype.Component;

/**
 * Projects commercial entities onto their operator read models.
 *
 * <p>These projections previously lived in the plan admin controllers, which meant the API layer
 * held persistence entities and completed response composition outside the service transaction.
 * They run inside the service now; the relationships they read
 * ({@code PlanFeature.feature}, {@code AddOn.features}, {@code QuotaPackage.feature}) are already
 * loaded up front by the entity graphs added under {@code MAPPER-001}, so moving them here does
 * not reintroduce a statement per row.</p>
 */
@Component
public class PlanAdminReadModels {

    public PlanDto toDto(Plan plan) {
        return new PlanDto(
                plan.getId(), plan.getCode(), plan.getName(),
                plan.getDescription(), plan.getPrice(), plan.getCurrencyCode(),
                plan.getBillingCycle(), plan.getStatus(),
                plan.getLineageId(), plan.getRevisionNumber(),
                plan.getSourcePlan() != null ? plan.getSourcePlan().getId() : null,
                plan.getCreationReason());
    }

    public PlanFeatureDto toDto(PlanFeature planFeature) {
        return new PlanFeatureDto(
                planFeature.getId(),
                planFeature.getFeature().getCode(),
                planFeature.getMode(),
                planFeature.getQuotaConfigs());
    }

    public AddOnDto toDto(AddOn addOn) {
        return new AddOnDto(
                addOn.getId(), addOn.getCode(), addOn.getName(), addOn.getDescription(),
                addOn.getPrice(), addOn.getCurrencyCode(), addOn.getBillingCycle(), addOn.getStatus(),
                addOn.getDefinitionVersion(), addOn.getAllowedPlanCodes(), addOn.getBlockedPlanCodes(),
                addOn.getDependencyCodes(), addOn.getExclusionCodes(),
                addOn.getFeatures().stream().map(this::toDto).toList());
    }

    public AddOnDto.FeatureItem toDto(AddOnFeature feature) {
        return new AddOnDto.FeatureItem(
                feature.getId(), feature.getFeature().getCode(), feature.getQuotaConfigs());
    }

    public QuotaPackageDto toDto(QuotaPackage item) {
        return new QuotaPackageDto(
                item.getId(), item.getCode(), item.getName(), item.getDescription(),
                item.getFeature().getCode(), item.getResource(), item.getCapacityPerUnit(),
                item.getPrice(), item.getCurrencyCode(), item.getBillingCycle(), item.isRepeatable(),
                item.getMaximumQuantity(), item.getStatus(), item.getDefinitionVersion(),
                item.getAllowedPlanCodes(), item.getAllowedAddOnCodes());
    }
}
