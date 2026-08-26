package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionResolutionSource;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.ExtensionAvailabilityIssue;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialSelectionFinalizerTest {

    @Mock private RegistryCatalogVersionService registryCatalogVersionService;
    @Mock private PlanRepository planRepository;
    @Mock private AddOnRepository addOnRepository;
    @Mock private QuotaPackageRepository quotaPackageRepository;
    @Mock private ProductPriceRepository productPriceRepository;
    @Mock private ProductPriceResolver productPriceResolver;
    @Mock private CommercialCatalogResolver catalogResolver;
    @Mock private SubscriptionSnapshotFactory subscriptionSnapshotFactory;
    @Mock private EntityManager entityManager;

    @InjectMocks private CommercialSelectionFinalizer finalizer;

    @Test
    void priceIdentityChurnBetweenResolutionAndLockPassFailsStaleWithoutSnapshotting() {
        Plan plan = plan("FLEX");
        AddOn addOn = addOn("CUSTOM_ROLES");
        ProductPrice planPrice = price(plan, BigDecimal.ZERO);
        ProductPrice firstPrice = price(addOn, BigDecimal.TEN);
        ProductPrice replacement = price(addOn, BigDecimal.valueOf(11));
        var preliminary = selection(plan, planPrice, addOn, firstPrice, List.of());
        var changed = selection(plan, planPrice, addOn, replacement, List.of());

        when(planRepository.findByCodeForUpdate("FLEX")).thenReturn(Optional.of(plan));
        when(addOnRepository.findAllByCodeInForUpdate(Set.of("CUSTOM_ROLES")))
                .thenReturn(List.of(addOn));
        when(productPriceResolver.resolvePlan(plan, null)).thenReturn(planPrice);
        when(catalogResolver.resolveSelection(
                eq(plan), any(CommercialCatalogResolver.PriceTuple.class),
                eq(Set.of("CUSTOM_ROLES")), eq(List.of()),
                eq(CommercialCatalogResolver.Audience.CLIENT_CATALOG),
                eq(CommercialCatalogResolver.RetainedSelection.none())))
                .thenReturn(preliminary, changed);
        when(productPriceRepository.findAllByIdInForUpdate(Set.of(planPrice.getId(), firstPrice.getId())))
                .thenReturn(List.of(planPrice, firstPrice));
        when(planRepository.findByCode("FLEX")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> finalizer.finalizeSelection(
                "FLEX", null, Set.of("CUSTOM_ROLES"), List.of(),
                CommercialCatalogResolver.Audience.CLIENT_CATALOG,
                CommercialCatalogResolver.RetainedSelection.none(), null))
                .isInstanceOf(StaleResourceVersionException.class)
                .hasMessageContaining("changed while it was being finalized");

        verify(subscriptionSnapshotFactory, never()).fromResolvedSelection(any(), any(), any(), any());
    }

    @Test
    void blockedClientSelectionUsesGenericUnavailableContractBeforeSnapshotting() {
        Plan plan = plan("FLEX");
        ProductPrice planPrice = price(plan, BigDecimal.ZERO);
        var issue = new ExtensionAvailabilityIssue(
                ExtensionAvailabilityReason.PRODUCT_NOT_FOUND,
                ExtensionResolutionSource.PRODUCT_LIFECYCLE,
                "HIDDEN_OR_UNKNOWN");
        var blocked = new CommercialCatalogResolver.SelectionResolution(
                planResolution(plan, planPrice, List.of()),
                Set.of("HIDDEN_OR_UNKNOWN"), List.of(), List.of(issue), Map.of());

        when(planRepository.findByCodeForUpdate("FLEX")).thenReturn(Optional.of(plan));
        when(addOnRepository.findAllByCodeInForUpdate(Set.of("HIDDEN_OR_UNKNOWN")))
                .thenReturn(List.of());
        when(productPriceResolver.resolvePlan(plan, null)).thenReturn(planPrice);
        when(catalogResolver.resolveSelection(
                eq(plan), any(CommercialCatalogResolver.PriceTuple.class),
                eq(Set.of("HIDDEN_OR_UNKNOWN")), eq(List.of()),
                eq(CommercialCatalogResolver.Audience.CLIENT_CATALOG),
                eq(CommercialCatalogResolver.RetainedSelection.none())))
                .thenReturn(blocked);
        when(productPriceRepository.findAllByIdInForUpdate(Set.of(planPrice.getId())))
                .thenReturn(List.of(planPrice));
        when(planRepository.findByCode("FLEX")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> finalizer.finalizeSelection(
                "FLEX", null, Set.of("HIDDEN_OR_UNKNOWN"), List.of(),
                CommercialCatalogResolver.Audience.CLIENT_CATALOG,
                CommercialCatalogResolver.RetainedSelection.none(), null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The requested commercial selection is unavailable.");

        verify(subscriptionSnapshotFactory, never()).fromResolvedSelection(any(), any(), any(), any());
    }

    private CommercialCatalogResolver.SelectionResolution selection(
            Plan plan,
            ProductPrice planPrice,
            AddOn addOn,
            ProductPrice addOnPrice,
            List<ExtensionAvailabilityIssue> issues
    ) {
        var addOnResolution = new CommercialCatalogResolver.AddOnResolution(
                addOn, issues, List.of(addOnPrice),
                Set.of(CommercialCatalogResolver.PriceTuple.from(addOnPrice)), Set.of());
        return new CommercialCatalogResolver.SelectionResolution(
                planResolution(plan, planPrice, List.of(addOnResolution)),
                Set.of(addOn.getCode()), List.of(), List.of(), Map.of());
    }

    private CommercialCatalogResolver.PlanResolution planResolution(
            Plan plan,
            ProductPrice price,
            List<CommercialCatalogResolver.AddOnResolution> addOns
    ) {
        return new CommercialCatalogResolver.PlanResolution(
                plan, List.of(), List.of(price), List.of(), addOns, List.of(),
                plan.getExtensionPolicy(), plan.getSalesVisibility());
    }

    private Plan plan(String code) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        plan.setCode(code);
        plan.setName(code);
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setMoney(Money.zero("USD"));
        plan.setBillingCycle(BillingCycle.MONTHLY);
        return plan;
    }

    private AddOn addOn(String code) {
        AddOn addOn = new AddOn();
        ReflectionTestUtils.setField(addOn, "id", UUID.randomUUID());
        addOn.setCode(code);
        addOn.setName(code);
        addOn.setStatus(AddOnStatus.ACTIVE);
        addOn.setMoney(Money.of(BigDecimal.TEN, "USD"));
        addOn.setBillingCycle(BillingCycle.MONTHLY);
        return addOn;
    }

    private ProductPrice price(Plan owner, BigDecimal amount) {
        ProductPrice price = ProductPrice.draft(
                owner, Money.of(amount, "USD"), BillingCycle.MONTHLY, Instant.EPOCH, null);
        price.activate();
        ReflectionTestUtils.setField(price, "id", UUID.randomUUID());
        return price;
    }

    private ProductPrice price(AddOn owner, BigDecimal amount) {
        ProductPrice price = ProductPrice.draft(
                owner, Money.of(amount, "USD"), BillingCycle.MONTHLY, Instant.EPOCH, null);
        price.activate();
        ReflectionTestUtils.setField(price, "id", UUID.randomUUID());
        return price;
    }
}
