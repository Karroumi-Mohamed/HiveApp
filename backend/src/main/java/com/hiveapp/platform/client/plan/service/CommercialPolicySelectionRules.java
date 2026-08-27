package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.dto.ExtensionAvailabilityIssue;

import java.util.List;

/** Waives only client DIRECT_ONLY visibility when an exact active policy grants that product. */
public final class CommercialPolicySelectionRules {

    private CommercialPolicySelectionRules() {}

    public static CommercialCatalogResolver.PlanResolution adjustPlan(
            CommercialCatalogResolver.PlanResolution resolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        if (audience != CommercialCatalogResolver.Audience.CLIENT_CATALOG
                || !evaluation.allowsDirectSelection(
                        CommercialPolicyProductType.PLAN, resolution.plan().getId())) {
            return resolution;
        }
        return new CommercialCatalogResolver.PlanResolution(
                resolution.plan(), withoutDirectOnly(resolution.issues()), resolution.prices(),
                resolution.planFeatures(), resolution.addOns(), resolution.quotaPackages(),
                resolution.effectiveExtensionPolicy(), resolution.effectiveSalesVisibility());
    }

    public static CommercialCatalogResolver.SelectionResolution adjustSelection(
            CommercialCatalogResolver.SelectionResolution resolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        if (audience != CommercialCatalogResolver.Audience.CLIENT_CATALOG) return resolution;
        var plan = adjustPlan(resolution.plan(), audience, evaluation);
        List<ExtensionAvailabilityIssue> issues = resolution.issues().stream()
                .filter(issue -> !waivedDirectOnly(issue, resolution, evaluation))
                .toList();
        return new CommercialCatalogResolver.SelectionResolution(
                plan, resolution.addOnCodes(), resolution.quotaPackages(), issues,
                resolution.packageResolutions());
    }

    public static boolean planSelectable(
            CommercialCatalogResolver.PlanResolution resolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        return planVisible(resolution, audience, evaluation)
                && !evaluation.blocksProduct(
                        CommercialPolicyProductType.PLAN, resolution.plan().getId());
    }

    /** A policy-blocked product remains explainable in the catalogue but cannot be selected. */
    public static boolean planVisible(
            CommercialCatalogResolver.PlanResolution resolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        CommercialCatalogResolver.PlanResolution adjusted = adjustPlan(resolution, audience, evaluation);
        if (!adjusted.selectable()) return false;
        return audience == CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR
                || adjusted.effectiveSalesVisibility()
                == com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC
                || evaluation.allowsDirectSelection(
                        CommercialPolicyProductType.PLAN, adjusted.plan().getId());
    }

    public static boolean addOnSelectable(
            CommercialCatalogResolver.AddOnResolution resolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        return addOnVisible(resolution, audience, evaluation)
                && !evaluation.blocksProduct(
                        CommercialPolicyProductType.ADD_ON, resolution.addOn().getId());
    }

    public static boolean addOnVisible(
            CommercialCatalogResolver.AddOnResolution resolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        List<ExtensionAvailabilityIssue> issues = audience == CommercialCatalogResolver.Audience.CLIENT_CATALOG
                && evaluation.allowsDirectSelection(
                        CommercialPolicyProductType.ADD_ON, resolution.addOn().getId())
                ? withoutDirectOnly(resolution.issues()) : resolution.issues();
        if (!issues.isEmpty()) return false;
        return audience == CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR
                || resolution.addOn().getSalesVisibility()
                == com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC
                || evaluation.allowsDirectSelection(
                        CommercialPolicyProductType.ADD_ON, resolution.addOn().getId());
    }

    public static boolean quotaPackageSelectable(
            CommercialCatalogResolver.QuotaPackageResolution resolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        return quotaPackageVisible(resolution, audience, evaluation)
                && !evaluation.blocksProduct(
                        CommercialPolicyProductType.QUOTA_PACKAGE,
                        resolution.quotaPackage().getId());
    }

    public static boolean quotaPackageVisible(
            CommercialCatalogResolver.QuotaPackageResolution resolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        List<ExtensionAvailabilityIssue> issues = audience == CommercialCatalogResolver.Audience.CLIENT_CATALOG
                && evaluation.allowsDirectSelection(
                        CommercialPolicyProductType.QUOTA_PACKAGE,
                        resolution.quotaPackage().getId())
                ? withoutDirectOnly(resolution.issues()) : resolution.issues();
        if (!issues.isEmpty()) return false;
        return audience == CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR
                || resolution.quotaPackage().getSalesVisibility()
                == com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC
                || evaluation.allowsDirectSelection(
                        CommercialPolicyProductType.QUOTA_PACKAGE,
                        resolution.quotaPackage().getId());
    }

    private static boolean waivedDirectOnly(
            ExtensionAvailabilityIssue issue,
            CommercialCatalogResolver.SelectionResolution resolution,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        if (issue.reason() != ExtensionAvailabilityReason.DIRECT_ONLY) return false;
        var addOn = resolution.plan().addOns().stream()
                .filter(item -> item.code().equals(issue.sourceCode())).findFirst();
        if (addOn.isPresent()) {
            return evaluation.allowsDirectSelection(
                    CommercialPolicyProductType.ADD_ON, addOn.orElseThrow().addOn().getId());
        }
        var quotaPackage = resolution.plan().quotaPackages().stream()
                .filter(item -> item.code().equals(issue.sourceCode())).findFirst();
        return quotaPackage.isPresent() && evaluation.allowsDirectSelection(
                CommercialPolicyProductType.QUOTA_PACKAGE,
                quotaPackage.orElseThrow().quotaPackage().getId());
    }

    private static List<ExtensionAvailabilityIssue> withoutDirectOnly(
            List<ExtensionAvailabilityIssue> issues
    ) {
        return issues.stream()
                .filter(issue -> issue.reason() != ExtensionAvailabilityReason.DIRECT_ONLY)
                .toList();
    }
}
