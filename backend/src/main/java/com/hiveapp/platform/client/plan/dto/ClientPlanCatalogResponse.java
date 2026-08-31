package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.RetainedEntitlementState;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.money.ExactDecimal;
import com.hiveapp.shared.quota.QuotaLimitMode;
import com.hiveapp.shared.quota.QuotaSlot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ClientPlanCatalogResponse(
        CurrentSubscription currentSubscription,
        List<CatalogPlan> plans,
        List<ClientCommercialPolicyDecision> commercialPolicyDecisions
) {
    public ClientPlanCatalogResponse {
        plans = plans == null ? List.of() : List.copyOf(plans);
        commercialPolicyDecisions = commercialPolicyDecisions == null
                ? List.of() : List.copyOf(commercialPolicyDecisions);
    }

    public record CurrentSubscription(
            UUID id,
            String planCode,
            SubscriptionStatus status,
            @ExactDecimal BigDecimal currentPrice,
            String currentPriceCurrencyCode,
            UUID planPriceEntryId,
            BillingCycle billingCycle,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages,
            List<RetainedAddOn> retainedAddOns,
            List<RetainedQuotaPackage> retainedQuotaPackages
    ) {}

    /** Exact held terms for a selected AddOn; never sourced from another Account's catalogue. */
    public record RetainedAddOn(
            String code,
            String name,
            long definitionVersion,
            @ExactDecimal BigDecimal unitPrice,
            String currencyCode,
            BillingCycle billingCycle,
            List<String> featureCodes,
            UUID priceEntryId,
            RetainedEntitlementState state,
            boolean removable,
            boolean selectableForNewSale
    ) {}

    /** Exact held package terms plus safe quantity-management metadata. */
    public record RetainedQuotaPackage(
            String code,
            String name,
            long definitionVersion,
            String featureCode,
            String resource,
            long capacityPerUnit,
            int quantity,
            @ExactDecimal BigDecimal unitPrice,
            String currencyCode,
            BillingCycle billingCycle,
            UUID priceEntryId,
            RetainedEntitlementState state,
            boolean removable,
            boolean quantityEditable,
            Integer maximumSelectableQuantity
    ) {}

    public record CatalogPlan(
            String code,
            String name,
            String description,
            @ExactDecimal BigDecimal basePrice,
            String currencyCode,
            BillingCycle billingCycle,
            boolean current,
            boolean selectable,
            List<CatalogFeature> features,
            List<CatalogAddOn> addOns,
            List<CatalogQuotaPackage> quotaPackages,
            List<CatalogPrice> prices,
            List<ClientCommercialPolicyDecision> commercialPolicyDecisions
    ) {
        public CatalogPlan(
                String code, String name, String description, BigDecimal basePrice,
                String currencyCode, BillingCycle billingCycle, boolean current,
                List<CatalogFeature> features, List<CatalogAddOn> addOns,
                List<CatalogQuotaPackage> quotaPackages
        ) {
            this(code, name, description, basePrice, currencyCode, billingCycle, current, true,
                    features, addOns, quotaPackages, List.of(), List.of());
        }
    }

    public record CatalogFeature(
            String featureCode,
            String displayName,
            String description,
            PlanFeatureMode mode,
            List<CatalogQuota> quotas
    ) {}

    public record CatalogAddOn(
            String code,
            String name,
            String description,
            @ExactDecimal BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle,
            long definitionVersion,
            Set<String> dependencyCodes,
            Set<String> exclusionCodes,
            List<CatalogAddOnFeature> features,
            List<CatalogPrice> prices,
            boolean selectable,
            List<ClientCommercialPolicyDecision> commercialPolicyDecisions
    ) {
        public CatalogAddOn(
                String code, String name, String description, BigDecimal price,
                String currencyCode, BillingCycle billingCycle, long definitionVersion,
                Set<String> dependencyCodes, Set<String> exclusionCodes,
                List<CatalogAddOnFeature> features
        ) {
            this(code, name, description, price, currencyCode, billingCycle, definitionVersion,
                    dependencyCodes, exclusionCodes, features, List.of(), true, List.of());
        }
    }

    public record CatalogAddOnFeature(
            String featureCode,
            String displayName,
            String description,
            List<CatalogQuota> quotas
    ) {}

    public record CatalogQuota(
            String featureCode,
            QuotaSlot slot,
            QuotaLimitMode mode,
            Long limit,
            boolean unlimited,
            Long currentUsage
    ) {}

    public record CatalogQuotaPackage(
            String code,
            String name,
            String description,
            long definitionVersion,
            String featureCode,
            String resource,
            long capacityPerUnit,
            @ExactDecimal BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle,
            boolean repeatable,
            int maximumQuantity,
            Set<String> allowedPlanCodes,
            Set<String> allowedAddOnCodes,
            List<CatalogPrice> prices,
            boolean directlyAvailable,
            Set<String> requiresAddOnCodes,
            boolean selectable,
            List<ClientCommercialPolicyDecision> commercialPolicyDecisions
    ) {
        public CatalogQuotaPackage(
                String code, String name, String description, long definitionVersion,
                String featureCode, String resource, long capacityPerUnit, BigDecimal price,
                String currencyCode, BillingCycle billingCycle, boolean repeatable,
                int maximumQuantity, Set<String> allowedPlanCodes, Set<String> allowedAddOnCodes
        ) {
            this(code, name, description, definitionVersion, featureCode, resource, capacityPerUnit,
                    price, currencyCode, billingCycle, repeatable, maximumQuantity,
                    allowedPlanCodes, allowedAddOnCodes, List.of(), false, Set.of(), true, List.of());
        }
    }

    public record CatalogPrice(
            UUID priceEntryId,
            @ExactDecimal BigDecimal amount,
            String currencyCode,
            BillingCycle billingCycle,
            Instant effectiveFrom,
            Instant effectiveUntil
    ) {}
}
