package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnActivationBlocker;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.ProductActivationPriceDto;
import com.hiveapp.shared.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Computes AddOn publication compatibility and lineage impact without applying it. */
@Service
@RequiredArgsConstructor
public class AddOnActivationAssessor {

    private final AddOnFeatureRepository addOnFeatureRepository;
    private final ProductPriceRepository productPriceRepository;
    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final BillingConfigurationValidator billingConfigurationValidator;
    private final CommercialCatalogResolver commercialCatalogResolver;

    public Assessment assess(AddOn addOn, List<AddOn> lineage, Instant evaluatedAt) {
        EnumSet<AddOnActivationBlocker> blockers =
                EnumSet.noneOf(AddOnActivationBlocker.class);
        if (addOn.getStatus() == AddOnStatus.ARCHIVED) {
            blockers.add(AddOnActivationBlocker.ARCHIVED_TERMINAL);
        } else if (addOn.getStatus() != AddOnStatus.DRAFT
                && addOn.getStatus() != AddOnStatus.INACTIVE) {
            blockers.add(AddOnActivationBlocker.WRONG_LIFECYCLE_STATE);
        }

        List<AddOnFeature> features = addOnFeatureRepository.findAllByAddOnId(addOn.getId()).stream()
                .sorted(Comparator.comparing(feature -> feature.getId().toString()))
                .toList();
        if (features.isEmpty()) blockers.add(AddOnActivationBlocker.NO_FEATURES);
        for (AddOnFeature feature : features) {
            try {
                billingConfigurationValidator.validateAddOnFeature(
                        feature.getFeature().getCode(), feature.getQuotaConfigs(),
                        addOn.getCurrencyCode());
            } catch (InvalidRequestException exception) {
                blockers.add(AddOnActivationBlocker.FEATURE_CONFIGURATION_INVALID);
            }
        }

        List<ProductPrice> allPrices = productPriceRepository.findAllByAddOnId(addOn.getId()).stream()
                .sorted(Comparator.comparing(ProductPrice::getId))
                .toList();
        List<ProductPrice> reviewedPrices = allPrices.stream()
                .filter(price -> price.isApplicableAt(evaluatedAt))
                .toList();
        if (reviewedPrices.isEmpty()) blockers.add(AddOnActivationBlocker.NO_APPLICABLE_PRICE);

        List<PlanAssessment> planAssessments = new ArrayList<>();
        if (!addOn.getAllowedPlanCodes().isEmpty()) {
            List<String> requestedCodes = addOn.getAllowedPlanCodes().stream().sorted().toList();
            List<Plan> candidates = planRepository.findAllByCodeInOrderByIdAsc(requestedCodes);
            Set<String> foundCodes = candidates.stream().map(Plan::getCode).collect(Collectors.toSet());
            if (!foundCodes.containsAll(requestedCodes)) {
                blockers.add(AddOnActivationBlocker.TARGET_PLAN_MISSING);
            }
            assessPlanBatch(addOn, candidates, planAssessments);
            if (planAssessments.stream().anyMatch(assessment -> !assessment.selectable())) {
                blockers.add(AddOnActivationBlocker.TARGET_PLAN_INCOMPATIBLE);
            }
        } else {
            int page = 0;
            Slice<Plan> candidates;
            do {
                candidates = planRepository.findAllByStatus(
                        PlanStatus.ACTIVE,
                        PageRequest.of(page++, 100, Sort.by(Sort.Direction.ASC, "id")));
                List<Plan> batch = candidates.getContent().stream()
                        .filter(plan -> !addOn.getBlockedPlanCodes().contains(plan.getCode()))
                        .toList();
                assessPlanBatch(addOn, batch, planAssessments);
            } while (candidates.hasNext());
            if (planAssessments.stream().noneMatch(PlanAssessment::selectable)) {
                blockers.add(AddOnActivationBlocker.NO_COMPATIBLE_ACTIVE_PLAN);
            }
        }

        List<UUID> addOnsToDeactivate = lineage.stream()
                .filter(candidate -> !candidate.getId().equals(addOn.getId()))
                .filter(candidate -> candidate.getStatus() == AddOnStatus.ACTIVE)
                .map(AddOn::getId)
                .sorted()
                .toList();
        if (!addOnsToDeactivate.isEmpty()) {
            Set<UUID> ids = Set.copyOf(addOnsToDeactivate);
            if (!addOnRepository.countInboundAddOnReferencesByStatusIn(
                    ids, List.of(AddOnStatus.ACTIVE, AddOnStatus.INACTIVE, AddOnStatus.DRAFT)).isEmpty()) {
                blockers.add(AddOnActivationBlocker.DEPENDENT_ADD_ON_REQUIRES_MIGRATION);
            }
            if (!quotaPackageRepository.countAddOnReferencesByStatusIn(
                    ids, List.of(QuotaPackageStatus.ACTIVE, QuotaPackageStatus.INACTIVE,
                            QuotaPackageStatus.DRAFT)).isEmpty()) {
                blockers.add(AddOnActivationBlocker.TARGETED_QUOTA_PACKAGE_REQUIRES_MIGRATION);
            }
        }

        String fingerprint = fingerprint(
                addOn, lineage, features, allPrices, planAssessments, blockers, evaluatedAt);
        long compatiblePlans = planAssessments.stream().filter(PlanAssessment::selectable).count();
        return new Assessment(
                List.copyOf(blockers), features.size(), planAssessments.size(), compatiblePlans,
                reviewedPrices.stream().map(ActivationAssessmentFingerprint::toDto).toList(),
                addOnsToDeactivate, fingerprint);
    }

    private void assessPlanBatch(
            AddOn addOn,
            List<Plan> candidates,
            List<PlanAssessment> assessments
    ) {
        if (candidates.isEmpty()) return;
        Map<UUID, CommercialCatalogResolver.AddOnActivationResolution> resolutions =
                commercialCatalogResolver.resolveAddOnActivation(addOn.getId(), candidates);
        candidates.stream().sorted(Comparator.comparing(Plan::getId)).forEach(plan -> {
            var resolution = resolutions.get(plan.getId());
            assessments.add(new PlanAssessment(
                    plan.getId(), plan.getCode(), plan.getVersion(),
                    resolution != null && resolution.selectable(),
                    resolution == null ? List.of("TARGET_PLAN_MISSING")
                            : resolution.issues().stream()
                                    .map(issue -> issue.reason().name() + ":"
                                            + issue.source().name() + ":" + issue.sourceCode())
                                    .sorted().toList()));
        });
    }

    private String fingerprint(
            AddOn addOn,
            List<AddOn> lineage,
            List<AddOnFeature> features,
            List<ProductPrice> prices,
            List<PlanAssessment> planAssessments,
            Collection<AddOnActivationBlocker> blockers,
            Instant evaluatedAt
    ) {
        StringBuilder state = new StringBuilder();
        ActivationAssessmentFingerprint.append(state, "add-on-activation");
        ActivationAssessmentFingerprint.append(state, addOn.getId());
        ActivationAssessmentFingerprint.append(state, addOn.getRowVersion());
        ActivationAssessmentFingerprint.append(state, addOn.getDefinitionVersion());
        ActivationAssessmentFingerprint.append(state, addOn.getStatus());
        ActivationAssessmentFingerprint.append(state, addOn.getLineageId());
        ActivationAssessmentFingerprint.append(state, addOn.getRevisionNumber());
        ActivationAssessmentFingerprint.append(state, addOn.getCurrencyCode());
        ActivationAssessmentFingerprint.append(state, addOn.getBillingCycle());
        ActivationAssessmentFingerprint.appendValues(
                state, "allowed-plans", addOn.getAllowedPlanCodes().stream().sorted().toList());
        ActivationAssessmentFingerprint.appendValues(
                state, "blocked-plans", addOn.getBlockedPlanCodes().stream().sorted().toList());
        ActivationAssessmentFingerprint.appendValues(
                state, "dependencies", addOn.getDependencyCodes().stream().sorted().toList());
        ActivationAssessmentFingerprint.appendValues(
                state, "exclusions", addOn.getExclusionCodes().stream().sorted().toList());
        for (AddOn candidate : lineage.stream().sorted(Comparator.comparing(AddOn::getId)).toList()) {
            ActivationAssessmentFingerprint.append(state, candidate.getId());
            ActivationAssessmentFingerprint.append(state, candidate.getRowVersion());
            ActivationAssessmentFingerprint.append(state, candidate.getStatus());
            ActivationAssessmentFingerprint.append(state, candidate.getRevisionNumber());
        }
        for (AddOnFeature feature : features) {
            ActivationAssessmentFingerprint.append(state, feature.getId());
            ActivationAssessmentFingerprint.append(state, feature.getFeature().getId());
            ActivationAssessmentFingerprint.append(state, feature.getFeature().getCode());
            ActivationAssessmentFingerprint.appendQuotaEntries(state, feature.getQuotaConfigs());
        }
        for (PlanAssessment assessment : planAssessments.stream()
                .sorted(Comparator.comparing(PlanAssessment::planId)).toList()) {
            ActivationAssessmentFingerprint.append(state, assessment.planId());
            ActivationAssessmentFingerprint.append(state, assessment.planCode());
            ActivationAssessmentFingerprint.append(state, assessment.planVersion());
            ActivationAssessmentFingerprint.append(state, assessment.selectable());
            ActivationAssessmentFingerprint.appendValues(state, "issues", assessment.issues());
        }
        ActivationAssessmentFingerprint.appendActivationPrices(state, prices, evaluatedAt);
        ActivationAssessmentFingerprint.appendValues(
                state, "blockers", blockers.stream().map(Enum::name).sorted().toList());
        return ActivationAssessmentFingerprint.digest(state.toString());
    }

    private record PlanAssessment(
            UUID planId,
            String planCode,
            long planVersion,
            boolean selectable,
            List<String> issues
    ) {}

    public record Assessment(
            List<AddOnActivationBlocker> blockers,
            long featureCount,
            long evaluatedPlanCount,
            long compatiblePlanCount,
            List<ProductActivationPriceDto> reviewedPrices,
            List<UUID> addOnsToDeactivate,
            String fingerprint
    ) {
        public Assessment {
            blockers = List.copyOf(blockers);
            reviewedPrices = List.copyOf(reviewedPrices);
            addOnsToDeactivate = List.copyOf(addOnsToDeactivate);
        }
    }
}
