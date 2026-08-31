package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds one explicit selection plus compatible, bounded policy grants. */
@Service
@RequiredArgsConstructor
public class CommercialPolicySelectionPlanner {

    private final CommercialCatalogResolver catalogResolver;

    public PlannedSelection plan(
            Plan targetPlan,
            CommercialCatalogResolver.PriceTuple tuple,
            Set<String> requestedAddOns,
            List<QuotaPackageSelection> requestedPackages,
            CommercialCatalogResolver.Audience audience,
            CommercialCatalogResolver.RetainedSelection retained,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        Set<String> requestedAddOnCodes = Set.copyOf(
                requestedAddOns == null ? Set.of() : requestedAddOns);
        List<QuotaPackageSelection> requestedPackageItems = List.copyOf(
                requestedPackages == null ? List.of() : requestedPackages);
        var base = CommercialPolicySelectionRules.adjustSelection(
                catalogResolver.resolveSelection(
                        targetPlan, tuple, requestedAddOnCodes, requestedPackageItems,
                        audience, retained),
                audience, evaluation);
        if (!base.selectable()) {
            return new PlannedSelection(base, requestedAddOnCodes, requestedPackageItems,
                    List.of(), List.of());
        }

        List<CommercialPolicyEvaluator.Candidate> grantCandidates = evaluation.products().values().stream()
                .map(CommercialPolicyEvaluator.WinnerSet::winner)
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> candidate.effect().getType() == CommercialPolicyEffectType.GRANT_ADD_ON
                        || candidate.effect().getType() == CommercialPolicyEffectType.GRANT_QUOTA_PACKAGE)
                // Dependency evaluation must not depend on the author's effect-row order.
                // AddOn grants are resolved first so a quota-package grant can safely rely on a
                // separately granted AddOn owner.
                .sorted(Comparator
                        .comparingInt((CommercialPolicyEvaluator.Candidate candidate) ->
                                candidate.effect().getType() == CommercialPolicyEffectType.GRANT_ADD_ON ? 0 : 1)
                        .thenComparingInt(candidate -> candidate.effect().getEffectOrder())
                        .thenComparing(candidate -> candidate.effect().getId()))
                .toList();
        if (grantCandidates.isEmpty()) {
            return new PlannedSelection(base, requestedAddOnCodes, requestedPackageItems,
                    List.of(), List.of());
        }

        Map<java.util.UUID, CommercialCatalogResolver.AddOnResolution> addOnsById =
                base.plan().addOns().stream().collect(java.util.stream.Collectors.toMap(
                        item -> item.addOn().getId(), java.util.function.Function.identity()));
        Map<java.util.UUID, CommercialCatalogResolver.QuotaPackageResolution> packagesById =
                base.plan().quotaPackages().stream().collect(java.util.stream.Collectors.toMap(
                        item -> item.quotaPackage().getId(), java.util.function.Function.identity()));
        Set<String> effectiveAddOns = new LinkedHashSet<>(requestedAddOnCodes);
        Map<String, Integer> effectivePackages = new LinkedHashMap<>();
        requestedPackageItems.forEach(item -> effectivePackages.put(item.packageCode(), item.quantity()));
        List<CommercialPolicyEvaluator.Candidate> accepted = new ArrayList<>();
        List<CommercialPolicyEvaluator.Candidate> rejected = new ArrayList<>();
        List<CommercialPolicyEvaluator.Candidate> pendingAddOnGrants = grantCandidates.stream()
                .filter(candidate -> candidate.effect().getType() == CommercialPolicyEffectType.GRANT_ADD_ON)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Set<String> acceptedGrantedAddOnCodes = new LinkedHashSet<>();
        boolean progressed;
        do {
            progressed = false;
            var iterator = pendingAddOnGrants.iterator();
            while (iterator.hasNext()) {
                CommercialPolicyEvaluator.Candidate candidate = iterator.next();
                CommercialCatalogResolver.AddOnResolution item =
                        addOnsById.get(candidate.effect().productId());
                if (item == null || !CommercialPolicySelectionRules.addOnSelectable(
                        item, audience, evaluation)) {
                    rejected.add(candidate);
                    iterator.remove();
                    continue;
                }
                boolean dependenciesSatisfied = item.dependencyClosureCodes().stream().allMatch(code -> {
                    CommercialCatalogResolver.AddOnResolution dependency = base.plan().addOns().stream()
                            .filter(result -> result.code().equals(code)).findFirst().orElse(null);
                    return dependency != null && CommercialPolicySelectionRules.addOnSelectable(
                            dependency, audience, evaluation)
                            && (requestedAddOnCodes.contains(code)
                            || acceptedGrantedAddOnCodes.contains(code));
                });
                if (!dependenciesSatisfied) continue;

                // Dependencies are never made billable as an invisible side effect. They must
                // already be explicitly selected or have their own accepted zero-price grant.
                effectiveAddOns.addAll(item.dependencyClosureCodes());
                effectiveAddOns.add(item.code());
                acceptedGrantedAddOnCodes.add(item.code());
                accepted.add(candidate);
                iterator.remove();
                progressed = true;
            }
        } while (progressed && !pendingAddOnGrants.isEmpty());
        rejected.addAll(pendingAddOnGrants);

        for (CommercialPolicyEvaluator.Candidate candidate : grantCandidates) {
            if (candidate.effect().getType() != CommercialPolicyEffectType.GRANT_QUOTA_PACKAGE) {
                continue;
            }

            CommercialCatalogResolver.QuotaPackageResolution item =
                    packagesById.get(candidate.effect().productId());
            Integer existingQuantity = item == null ? null : effectivePackages.get(item.code());
            boolean owned = item != null && (item.directlySelectable()
                    || item.requiredAddOnCodes().stream().anyMatch(effectiveAddOns::contains));
            if (item == null
                    || !CommercialPolicySelectionRules.quotaPackageSelectable(item, audience, evaluation)
                    || !owned
                    || (existingQuantity != null && existingQuantity != 1)) {
                rejected.add(candidate);
                continue;
            }
            effectivePackages.put(item.code(), 1);
            accepted.add(candidate);
        }

        List<QuotaPackageSelection> effectivePackageItems = effectivePackages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new QuotaPackageSelection(entry.getKey(), entry.getValue()))
                .toList();
        var combined = CommercialPolicySelectionRules.adjustSelection(
                catalogResolver.resolveSelection(
                        targetPlan, tuple, effectiveAddOns, effectivePackageItems, audience, retained),
                audience, evaluation);
        if (!combined.selectable()) {
            rejected.addAll(accepted);
            return new PlannedSelection(base, requestedAddOnCodes, requestedPackageItems,
                    List.of(), List.copyOf(rejected));
        }
        return new PlannedSelection(combined, Set.copyOf(effectiveAddOns), effectivePackageItems,
                List.copyOf(accepted), List.copyOf(rejected));
    }

    public record PlannedSelection(
            CommercialCatalogResolver.SelectionResolution resolution,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages,
            List<CommercialPolicyEvaluator.Candidate> acceptedGrants,
            List<CommercialPolicyEvaluator.Candidate> rejectedGrants
    ) {
        public PlannedSelection {
            addOnCodes = Set.copyOf(addOnCodes);
            quotaPackages = List.copyOf(quotaPackages);
            acceptedGrants = List.copyOf(acceptedGrants);
            rejectedGrants = List.copyOf(rejectedGrants);
        }
    }
}
