package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.PlanActivationBlocker;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.dto.ProductActivationPriceDto;
import com.hiveapp.shared.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Computes the complete, deterministic Plan activation decision without mutating catalogue state. */
@Service
@RequiredArgsConstructor
public class PlanActivationAssessor {

    private final PlanFeatureRepository planFeatureRepository;
    private final ProductPriceRepository productPriceRepository;
    private final BillingConfigurationValidator billingConfigurationValidator;

    public Assessment assess(Plan plan, Instant evaluatedAt) {
        EnumSet<PlanActivationBlocker> blockers = EnumSet.noneOf(PlanActivationBlocker.class);
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            blockers.add(PlanActivationBlocker.ARCHIVED_TERMINAL);
        } else if (plan.getStatus() != PlanStatus.DRAFT
                && plan.getStatus() != PlanStatus.INACTIVE) {
            blockers.add(PlanActivationBlocker.WRONG_LIFECYCLE_STATE);
        }

        List<PlanFeature> features = planFeatureRepository.findAllByPlanId(plan.getId()).stream()
                .sorted(Comparator.comparing(feature -> feature.getId().toString()))
                .toList();
        long included = features.stream()
                .filter(feature -> feature.getMode() == PlanFeatureMode.INCLUDED).count();
        long optional = features.stream()
                .filter(feature -> feature.getMode() == PlanFeatureMode.OPTIONAL_ADD_ON).count();
        long blocked = features.stream()
                .filter(feature -> feature.getMode() == PlanFeatureMode.BLOCKED_FOR_PLAN).count();
        if (included == 0) blockers.add(PlanActivationBlocker.NO_INCLUDED_FEATURES);

        for (PlanFeature feature : features) {
            try {
                billingConfigurationValidator.validatePlanFeature(
                        feature.getFeature().getCode(), feature.getMode(),
                        feature.getQuotaConfigs(), plan.getCurrencyCode());
            } catch (InvalidRequestException exception) {
                blockers.add(PlanActivationBlocker.FEATURE_CONFIGURATION_INVALID);
                continue;
            }
            if (feature.getMode() == PlanFeatureMode.INCLUDED) {
                try {
                    billingConfigurationValidator.requireCompleteQuotaConfiguration(
                            feature.getFeature().getCode(), feature.getQuotaConfigs());
                } catch (InvalidRequestException exception) {
                    blockers.add(PlanActivationBlocker.INCOMPLETE_QUOTA_CONFIGURATION);
                }
            }
        }

        List<ProductPrice> allPrices = productPriceRepository.findAllByPlanId(plan.getId()).stream()
                .sorted(Comparator.comparing(ProductPrice::getId))
                .toList();
        List<ProductPrice> reviewedPrices = allPrices.stream()
                .filter(price -> price.isApplicableAt(evaluatedAt))
                .toList();
        if (reviewedPrices.isEmpty()) blockers.add(PlanActivationBlocker.NO_APPLICABLE_PRICE);

        String fingerprint = fingerprint(plan, features, allPrices, blockers, evaluatedAt);
        return new Assessment(
                List.copyOf(blockers), included, optional, blocked,
                reviewedPrices.stream().map(ActivationAssessmentFingerprint::toDto).toList(),
                fingerprint);
    }

    private String fingerprint(
            Plan plan,
            List<PlanFeature> features,
            List<ProductPrice> prices,
            Collection<PlanActivationBlocker> blockers,
            Instant evaluatedAt
    ) {
        StringBuilder state = new StringBuilder();
        ActivationAssessmentFingerprint.append(state, "plan-activation");
        ActivationAssessmentFingerprint.append(state, plan.getId());
        ActivationAssessmentFingerprint.append(state, plan.getVersion());
        ActivationAssessmentFingerprint.append(state, plan.getStatus());
        ActivationAssessmentFingerprint.append(state, plan.getLineageId());
        ActivationAssessmentFingerprint.append(state, plan.getRevisionNumber());
        ActivationAssessmentFingerprint.append(state, plan.getCurrencyCode());
        ActivationAssessmentFingerprint.append(state, plan.getBillingCycle());
        for (PlanFeature feature : features) {
            ActivationAssessmentFingerprint.append(state, feature.getId());
            ActivationAssessmentFingerprint.append(state, feature.getFeature().getId());
            ActivationAssessmentFingerprint.append(state, feature.getFeature().getCode());
            ActivationAssessmentFingerprint.append(state, feature.getMode());
            ActivationAssessmentFingerprint.appendQuotaEntries(state, feature.getQuotaConfigs());
        }
        ActivationAssessmentFingerprint.appendActivationPrices(state, prices, evaluatedAt);
        ActivationAssessmentFingerprint.appendValues(
                state, "blockers", blockers.stream().map(Enum::name).sorted().toList());
        return ActivationAssessmentFingerprint.digest(state.toString());
    }

    public record Assessment(
            List<PlanActivationBlocker> blockers,
            long includedFeatureCount,
            long optionalAddOnFeatureCount,
            long blockedFeatureCount,
            List<ProductActivationPriceDto> reviewedPrices,
            String fingerprint
    ) {
        public Assessment {
            blockers = List.copyOf(blockers);
            reviewedPrices = List.copyOf(reviewedPrices);
        }
    }
}
