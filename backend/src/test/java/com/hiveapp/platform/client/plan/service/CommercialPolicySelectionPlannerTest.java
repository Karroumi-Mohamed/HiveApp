package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicyEffect;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommercialPolicySelectionPlannerTest {

    private static final CommercialCatalogResolver.PriceTuple TUPLE =
            new CommercialCatalogResolver.PriceTuple("USD", BillingCycle.MONTHLY);

    @Test
    void addOnGrantDependenciesResolveIndependentOfEffectOrderWhenEachHasAnAcceptedGrant() {
        CommercialCatalogResolver resolver = mock(CommercialCatalogResolver.class);
        Plan plan = mock(Plan.class);
        AddOn base = addOn("BASE");
        AddOn dependent = addOn("DEPENDENT");
        var baseResolution = addOnResolution(base, Set.of());
        var dependentResolution = addOnResolution(dependent, Set.of("BASE"));
        stubSelection(resolver, plan, List.of(dependentResolution, baseResolution));

        // The dependent row deliberately comes first. Resolution must follow the dependency
        // graph, not effect-row order.
        var dependentGrant = grantCandidate(dependent, 0);
        var baseGrant = grantCandidate(base, 1);
        var result = new CommercialPolicySelectionPlanner(resolver).plan(
                plan, TUPLE, Set.of(), List.of(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialCatalogResolver.RetainedSelection.none(),
                evaluation(dependentGrant, baseGrant));

        assertThat(result.addOnCodes()).containsExactlyInAnyOrder("BASE", "DEPENDENT");
        assertThat(result.acceptedGrants())
                .extracting(candidate -> candidate.effect().productCode())
                .containsExactlyInAnyOrder("BASE", "DEPENDENT");
        assertThat(result.rejectedGrants()).isEmpty();
    }

    @Test
    void addOnGrantNeverSilentlyInsertsAnUngrantablePaidDependency() {
        CommercialCatalogResolver resolver = mock(CommercialCatalogResolver.class);
        Plan plan = mock(Plan.class);
        AddOn base = addOn("PAID_BASE");
        AddOn dependent = addOn("DEPENDENT");
        var baseResolution = addOnResolution(base, Set.of());
        var dependentResolution = addOnResolution(dependent, Set.of("PAID_BASE"));
        stubSelection(resolver, plan, List.of(dependentResolution, baseResolution));

        var dependentGrant = grantCandidate(dependent, 0);
        var result = new CommercialPolicySelectionPlanner(resolver).plan(
                plan, TUPLE, Set.of(), List.of(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialCatalogResolver.RetainedSelection.none(),
                evaluation(dependentGrant));

        assertThat(result.addOnCodes()).isEmpty();
        assertThat(result.acceptedGrants()).isEmpty();
        assertThat(result.rejectedGrants()).containsExactly(dependentGrant);
    }

    private void stubSelection(
            CommercialCatalogResolver resolver,
            Plan plan,
            List<CommercialCatalogResolver.AddOnResolution> addOns
    ) {
        var planResolution = new CommercialCatalogResolver.PlanResolution(
                plan, List.of(), List.of(), List.of(), addOns, List.of(),
                PlanExtensionPolicy.OPEN_COMPATIBLE, ProductSalesVisibility.PUBLIC);
        when(resolver.resolveSelection(
                eq(plan), eq(TUPLE), anySet(), anyList(),
                eq(CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR),
                any(CommercialCatalogResolver.RetainedSelection.class)))
                .thenAnswer(invocation -> new CommercialCatalogResolver.SelectionResolution(
                        planResolution, invocation.getArgument(2), invocation.getArgument(3),
                        List.of(), Map.of()));
    }

    private CommercialCatalogResolver.AddOnResolution addOnResolution(
            AddOn addOn,
            Set<String> dependencyClosure
    ) {
        return new CommercialCatalogResolver.AddOnResolution(
                addOn, List.of(), List.of(), Set.of(TUPLE), dependencyClosure);
    }

    private AddOn addOn(String code) {
        AddOn addOn = mock(AddOn.class);
        when(addOn.getId()).thenReturn(UUID.randomUUID());
        when(addOn.getCode()).thenReturn(code);
        when(addOn.getSalesVisibility()).thenReturn(ProductSalesVisibility.PUBLIC);
        return addOn;
    }

    private CommercialPolicyEvaluator.Candidate grantCandidate(AddOn addOn, int order) {
        CommercialPolicy policy = mock(CommercialPolicy.class);
        CommercialPolicyEffect effect = mock(CommercialPolicyEffect.class);
        when(effect.getId()).thenReturn(UUID.randomUUID());
        when(effect.getEffectOrder()).thenReturn(order);
        when(effect.getType()).thenReturn(CommercialPolicyEffectType.GRANT_ADD_ON);
        when(effect.getProductType()).thenReturn(CommercialPolicyProductType.ADD_ON);
        UUID productId = addOn.getId();
        String productCode = addOn.getCode();
        when(effect.productId()).thenReturn(productId);
        when(effect.productCode()).thenReturn(productCode);
        return new CommercialPolicyEvaluator.Candidate(policy, effect, UUID.randomUUID());
    }

    private CommercialPolicyEvaluator.Evaluation evaluation(
            CommercialPolicyEvaluator.Candidate... candidates
    ) {
        Map<CommercialPolicyEvaluator.ProductKey, CommercialPolicyEvaluator.WinnerSet> products =
                new LinkedHashMap<>();
        for (CommercialPolicyEvaluator.Candidate candidate : candidates) {
            products.put(new CommercialPolicyEvaluator.ProductKey(
                            CommercialPolicyProductType.ADD_ON, candidate.effect().productId()),
                    new CommercialPolicyEvaluator.WinnerSet(candidate, List.of()));
        }
        return new CommercialPolicyEvaluator.Evaluation(
                Instant.parse("2026-08-27T10:00:00Z"),
                CommercialPolicyEvaluator.WinnerSet.empty(),
                CommercialPolicyEvaluator.WinnerSet.empty(), products, Map.of(), Map.of());
    }
}
