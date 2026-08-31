package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BillingConfigurationValidator {

    private final FeatureRepository featureRepository;
    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;

    public Feature validatePlanFeature(
            String featureCode,
            PlanFeatureMode mode,
            List<QuotaLimitEntry> quotaConfigs,
            String planCurrencyCode) {
        FeatureDefinition definition = requirePlanAssignableDefinition(featureCode);
        Feature feature = requireConfigurableFeature(featureCode);
        if (mode == null) {
            throw invalid("Plan feature mode is required.");
        }
        if (mode != PlanFeatureMode.INCLUDED && quotaConfigs != null && !quotaConfigs.isEmpty()) {
            throw invalid("Only included Plan features may define base quota limits.");
        }
        validateQuotaConfigs(definition, quotaConfigs);
        return feature;
    }

    /**
     * PLAN-FLOW-007: quota intent is always explicit. Every quota-able resource of an included
     * feature must carry a configuration — a numeric limit or an explicit UNLIMITED — before the
     * plan may activate; an absent configuration is an unfinished draft, never "unlimited".
     */
    public void requireCompleteQuotaConfiguration(String featureCode, List<QuotaLimitEntry> quotaConfigs) {
        FeatureDefinition definition = requirePlanAssignableDefinition(featureCode);
        Set<String> configured = quotaConfigs == null
                ? Set.of()
                : quotaConfigs.stream().map(QuotaLimitEntry::resource).collect(java.util.stream.Collectors.toSet());
        for (var slot : definition.quotaSlots()) {
            if (!configured.contains(slot.resource())) {
                throw invalid("Feature " + definition.code() + ": resource '" + slot.resource()
                        + "' has no quota configuration. Declare a limit or an explicit UNLIMITED before activation.");
            }
        }
    }

    public Feature validateQuotaPackageDefinition(String featureCode, String resource) {
        FeatureDefinition definition = requirePlanAssignableDefinition(featureCode);
        Feature feature = requireConfigurableFeature(featureCode);
        requireQuotaSlot(definition, resource);
        return feature;
    }

    public Feature validateAddOnFeature(
            String featureCode,
            List<QuotaLimitEntry> quotaConfigs,
            String addOnCurrencyCode) {
        FeatureDefinition definition = requirePlanAssignableDefinition(featureCode);
        Feature feature = requireConfigurableFeature(featureCode);
        validateQuotaConfigs(definition, quotaConfigs);
        return feature;
    }

    private void validateQuotaConfigs(
            FeatureDefinition definition,
            List<QuotaLimitEntry> quotaConfigs) {
        Set<String> resources = new HashSet<>();
        if (quotaConfigs == null) {
            return;
        }

        for (QuotaLimitEntry quotaConfig : quotaConfigs) {
            if (quotaConfig == null) {
                throw invalid("Quota configuration cannot be null.");
            }
            requireQuotaSlot(definition, quotaConfig.resource());
            if (!resources.add(quotaConfig.resource())) {
                throw invalid("Duplicate quota configuration for " + definition.code() + "." + quotaConfig.resource() + ".");
            }
        }
    }

    private FeatureDefinition requirePlanAssignableDefinition(String featureCode) {
        if (featureCode == null || featureCode.isBlank()) {
            throw invalid("Feature code is required.");
        }

        FeatureDefinition definition = featureDefinitionCollectorProvider.getObject()
                .collectByCode()
                .get(featureCode);
        if (definition == null || !definition.planAssignable()) {
            throw invalid("Feature " + featureCode + " cannot be assigned to billing configuration.");
        }
        return definition;
    }

    private Feature requireConfigurableFeature(String featureCode) {
        Feature feature = featureRepository.findByCode(featureCode)
                .orElseThrow(() -> invalid("Feature " + featureCode + " does not exist in the registry."));

        if (!feature.isNewSalesEnabled()
                || feature.getStatus() == FeatureStatus.INTERNAL
                || feature.getStatus() == FeatureStatus.DEPRECATED) {
            throw invalid("Feature " + featureCode + " is not available for billing configuration.");
        }
        return feature;
    }

    private void requireQuotaSlot(FeatureDefinition definition, String resource) {
        if (resource == null || resource.isBlank()
                || definition.quotaSlots().stream().noneMatch(slot -> slot.resource().equals(resource))) {
            throw invalid("Quota resource " + resource + " is not declared for feature " + definition.code() + ".");
        }
    }

    private InvalidRequestException invalid(String message) {
        return new InvalidRequestException(message);
    }
}
