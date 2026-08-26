package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPriceResolverTest {

    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");

    @Mock private ProductPriceRepository productPriceRepository;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void missingLegacyTupleFallsBackOnlyWhenOneUnambiguousPriceExists() {
        Plan plan = plan();
        ProductPrice annual = activePrice(plan, BillingCycle.YEARLY);
        ProductPriceResolver resolver = new ProductPriceResolver(productPriceRepository, clock);
        when(productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, plan.getId(), "USD", BillingCycle.MONTHLY, NOW))
                .thenReturn(List.of());
        when(productPriceRepository.findAllApplicable(ProductPriceOwnerType.PLAN, plan.getId(), NOW))
                .thenReturn(List.of(annual));

        assertThat(resolver.resolvePlan(plan, null)).isSameAs(annual);
    }

    @Test
    void missingLegacyTupleNeverGuessesBetweenMultipleCommercialChoices() {
        Plan plan = plan();
        ProductPrice monthly = activePrice(plan, BillingCycle.MONTHLY);
        ProductPrice annual = activePrice(plan, BillingCycle.YEARLY);
        ProductPriceResolver resolver = new ProductPriceResolver(productPriceRepository, clock);
        when(productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, plan.getId(), "USD", BillingCycle.MONTHLY, NOW))
                .thenReturn(List.of());
        when(productPriceRepository.findAllApplicable(ProductPriceOwnerType.PLAN, plan.getId(), NOW))
                .thenReturn(List.of(monthly, annual));

        assertThatThrownBy(() -> resolver.resolvePlan(plan, null))
                .isInstanceOf(InvalidStateException.class)
                .hasMessage("This Plan revision has multiple active prices; an exact price selection is required.");
    }

    private Plan plan() {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        plan.setCode("FREE");
        plan.setName("Free");
        plan.setMoney(Money.zero("USD"));
        plan.setBillingCycle(BillingCycle.MONTHLY);
        return plan;
    }

    private ProductPrice activePrice(Plan plan, BillingCycle cycle) {
        ProductPrice price = ProductPrice.draft(plan, Money.zero("USD"), cycle, NOW.minusSeconds(60), null);
        price.activate();
        return price;
    }
}
