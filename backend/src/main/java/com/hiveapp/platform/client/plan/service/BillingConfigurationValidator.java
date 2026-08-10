package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaOverride;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
        Money planCurrency = money(BigDecimal.ZERO, planCurrencyCode, "Plan currency");
        if (mode == null) {
            throw invalid("Plan feature mode is required.");
        }
        if (mode != PlanFeatureMode.INCLUDED && quotaConfigs != null && !quotaConfigs.isEmpty()) {
            throw invalid("Only included Plan features may define base quota limits.");
        }
        validateQuotaConfigs(definition, quotaConfigs, planCurrency);
        return feature;
    }

    public void validateSubscriptionOverrides(List<QuotaOverride> quotaOverrides) {
        Set<String> quotaKeys = new HashSet<>();
        if (quotaOverrides != null) {
            for (QuotaOverride override : quotaOverrides) {
                if (override == null) {
                    throw invalid("Quota override cannot be null.");
                }
                FeatureDefinition definition = requirePlanAssignableDefinition(override.featureCode());
                requireConfigurableFeature(override.featureCode());
                requireQuotaSlot(definition, override.resource());
                if (!quotaKeys.add(override.featureCode() + ":" + override.resource())) {
                    throw invalid("Duplicate quota override for " + override.featureCode() + "." + override.resource() + ".");
                }
                validateNonNegative(override.limit(), "Quota override limit");
            }
        }
    }

    public Feature validateAddOnFeature(
            String featureCode,
            List<QuotaLimitEntry> quotaConfigs,
            String addOnCurrencyCode) {
        FeatureDefinition definition = requirePlanAssignableDefinition(featureCode);
        Feature feature = requireConfigurableFeature(featureCode);
        validateQuotaConfigs(
                definition,
                quotaConfigs,
                money(BigDecimal.ZERO, addOnCurrencyCode, "AddOn currency"));
        return feature;
    }

    private void validateQuotaConfigs(
            FeatureDefinition definition,
            List<QuotaLimitEntry> quotaConfigs,
            Money planCurrency) {
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
            validateNonNegative(quotaConfig.limit(), "Quota limit");
            validateNonNegative(quotaConfig.pricePerUnit(), "Quota price per unit");
            if (quotaConfig.pricePerUnit() != null) {
                requireSameCurrency(planCurrency, quotaConfig.priceMoney(), "Quota price per unit");
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

    private void validateNonNegative(Long value, String label) {
        if (value != null && value < 0) {
            throw invalid(label + " cannot be negative.");
        }
    }

    private void validateNonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            throw invalid(label + " cannot be negative.");
        }
    }

    private Money money(BigDecimal amount, String currencyCode, String label) {
        try {
            return Money.of(amount, currencyCode);
        } catch (IllegalArgumentException exception) {
            throw invalid(label + " is invalid: " + exception.getMessage());
        }
    }

    private void requireSameCurrency(Money expected, Money actual, String label) {
        try {
            expected.requireSameCurrency(actual);
        } catch (IllegalArgumentException exception) {
            throw invalid(label + " must use plan currency " + expected.currencyCode() + ".");
        }
    }

    private InvalidRequestException invalid(String message) {
        return new InvalidRequestException(message);
    }
}
