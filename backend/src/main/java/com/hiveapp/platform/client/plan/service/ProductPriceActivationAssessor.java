package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceBlocker;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Computes the deterministic decision signed by a ProductPrice activation preview. */
@Service
@RequiredArgsConstructor
public class ProductPriceActivationAssessor {

    private final ProductPriceResolver productPriceResolver;

    public Assessment assess(ProductPrice price, Instant evaluatedAt) {
        List<ProductPriceBlocker> blockers = blockers(price, evaluatedAt);
        StringBuilder state = new StringBuilder();
        ActivationAssessmentFingerprint.append(state, "product-price-activation");
        ActivationAssessmentFingerprint.append(state, price.getId());
        ActivationAssessmentFingerprint.append(state, price.getVersion());
        ActivationAssessmentFingerprint.append(state, price.getStatus());
        ActivationAssessmentFingerprint.append(state, price.getOwnerType());
        ActivationAssessmentFingerprint.append(state, price.ownerId());
        appendOwnerState(state, price);
        ActivationAssessmentFingerprint.append(state, price.getAmount().toPlainString());
        ActivationAssessmentFingerprint.append(state, price.getCurrencyCode());
        ActivationAssessmentFingerprint.append(state, price.getBillingCycle());
        ActivationAssessmentFingerprint.append(state, price.getEffectiveFrom());
        ActivationAssessmentFingerprint.append(state, price.getEffectiveUntil());
        ActivationAssessmentFingerprint.append(state, price.getLineageId());
        ActivationAssessmentFingerprint.append(state, price.getRevisionNumber());
        ActivationAssessmentFingerprint.append(
                state, price.getSourcePrice() == null ? null : price.getSourcePrice().getId());
        ActivationAssessmentFingerprint.append(state, price.isCompatibilityDefault());
        ActivationAssessmentFingerprint.append(
                state, ActivationAssessmentFingerprint.priceWindowState(price, evaluatedAt));
        ActivationAssessmentFingerprint.appendValues(
                state, "blockers", blockers.stream().map(Enum::name).sorted().toList());
        return new Assessment(blockers, ActivationAssessmentFingerprint.digest(state.toString()));
    }

    private List<ProductPriceBlocker> blockers(ProductPrice price, Instant evaluatedAt) {
        List<ProductPriceBlocker> blockers = new ArrayList<>();
        if (price.getStatus() == ProductPriceStatus.ARCHIVED) {
            return List.of(ProductPriceBlocker.ARCHIVED_TERMINAL);
        }
        if (price.getStatus() != ProductPriceStatus.DRAFT
                && price.getStatus() != ProductPriceStatus.INACTIVE) {
            blockers.add(ProductPriceBlocker.WRONG_LIFECYCLE_STATE);
        }
        if (!ownerIsAvailable(price)) {
            blockers.add(ProductPriceBlocker.OWNER_NOT_ACTIVE);
        }
        if (price.getEffectiveUntil() != null
                && !price.getEffectiveUntil().isAfter(evaluatedAt)) {
            blockers.add(ProductPriceBlocker.EFFECTIVE_WINDOW_EXPIRED);
        }
        if (price.getId() != null && productPriceResolver.hasActiveOverlap(price)) {
            blockers.add(ProductPriceBlocker.ACTIVE_WINDOW_OVERLAP);
        }
        return List.copyOf(blockers);
    }

    private boolean ownerIsAvailable(ProductPrice price) {
        return switch (price.getOwnerType()) {
            case PLAN -> price.getPlan().getStatus() == PlanStatus.ACTIVE
                    || price.getPlan().getStatus() == PlanStatus.DRAFT
                    || price.getPlan().getStatus() == PlanStatus.INACTIVE;
            case ADD_ON -> price.getAddOn().getStatus() == AddOnStatus.ACTIVE
                    || price.getAddOn().getStatus() == AddOnStatus.DRAFT
                    || price.getAddOn().getStatus() == AddOnStatus.INACTIVE;
            case QUOTA_PACKAGE -> price.getQuotaPackage().getStatus() == QuotaPackageStatus.ACTIVE
                    || price.getQuotaPackage().getStatus() == QuotaPackageStatus.DRAFT
                    || price.getQuotaPackage().getStatus() == QuotaPackageStatus.INACTIVE;
        };
    }

    private void appendOwnerState(StringBuilder state, ProductPrice price) {
        switch (price.getOwnerType()) {
            case PLAN -> {
                Plan owner = price.getPlan();
                ActivationAssessmentFingerprint.append(state, owner.getId());
                ActivationAssessmentFingerprint.append(state, owner.getVersion());
                ActivationAssessmentFingerprint.append(state, owner.getCode());
                ActivationAssessmentFingerprint.append(state, owner.getStatus());
            }
            case ADD_ON -> {
                AddOn owner = price.getAddOn();
                ActivationAssessmentFingerprint.append(state, owner.getId());
                ActivationAssessmentFingerprint.append(state, owner.getRowVersion());
                ActivationAssessmentFingerprint.append(state, owner.getDefinitionVersion());
                ActivationAssessmentFingerprint.append(state, owner.getCode());
                ActivationAssessmentFingerprint.append(state, owner.getStatus());
            }
            case QUOTA_PACKAGE -> {
                QuotaPackage owner = price.getQuotaPackage();
                ActivationAssessmentFingerprint.append(state, owner.getId());
                ActivationAssessmentFingerprint.append(state, owner.getRowVersion());
                ActivationAssessmentFingerprint.append(state, owner.getDefinitionVersion());
                ActivationAssessmentFingerprint.append(state, owner.getCode());
                ActivationAssessmentFingerprint.append(state, owner.getStatus());
                ActivationAssessmentFingerprint.append(state, owner.getFeature().getId());
                ActivationAssessmentFingerprint.append(state, owner.getFeature().getCode());
            }
        }
    }

    public record Assessment(List<ProductPriceBlocker> blockers, String fingerprint) {
        public Assessment {
            blockers = List.copyOf(blockers);
        }
    }
}
