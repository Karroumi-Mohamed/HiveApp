package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaOverride;
import com.hiveapp.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingCalculatorTest {

    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private AddOnRepository addOnRepository;
    @Mock private SubscriptionOverrideReader subscriptionOverrideReader;
    @Mock private SubscriptionSnapshotReader subscriptionSnapshotReader;

    private BillingCalculator billingCalculator;

    @BeforeEach
    void setUp() {
        billingCalculator = new BillingCalculator(
                planFeatureRepository,
                addOnRepository,
                subscriptionOverrideReader,
                subscriptionSnapshotReader);
    }

    @Test
    void usesSnapshotBaseAndAddOnPricingBeforeLiveCatalogue() {
        Subscription subscription = subscription("{\"addOnCodes\":[\"COMPANY_MODULE\"]}");
        subscription.getPlan().setPrice(BigDecimal.valueOf(999));
        SubscriptionEntitlementSnapshot snapshot = new SubscriptionEntitlementSnapshot(
                "PRO",
                BigDecimal.valueOf(10),
                "USD",
                BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot("platform.company", List.of())),
                List.of(new SubscriptionAddOnSnapshot(
                        "COMPANY_MODULE", "Company module", 1, BigDecimal.valueOf(5),
                        "USD", BillingCycle.MONTHLY, List.of("platform.company"))));

        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(snapshot));
        when(subscriptionOverrideReader.read(subscription.getCustomOverrides()))
                .thenReturn(new SubscriptionOverrides(Set.of("COMPANY_MODULE"), List.of()));

        assertThat(billingCalculator.calculate(subscription)).isEqualByComparingTo("15");
        verifyNoInteractions(planFeatureRepository);
    }

    @Test
    void usesSnapshotQuotaPricingBeforeLivePlanTemplate() {
        Subscription subscription = subscription("{\"quotaOverrides\":[{\"featureCode\":\"platform.workspace\"}]}");
        SubscriptionEntitlementSnapshot snapshot = new SubscriptionEntitlementSnapshot(
                "PRO",
                BigDecimal.valueOf(10),
                "USD",
                BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(
                        "platform.workspace",
                        List.of(new QuotaLimitEntry("members", 3L, BigDecimal.valueOf(2), "USD")))),
                List.of());

        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(snapshot));
        when(subscriptionOverrideReader.read(subscription.getCustomOverrides()))
                .thenReturn(new SubscriptionOverrides(
                        Set.of(),
                        List.of(new QuotaOverride("platform.workspace", "members", 5L))));

        assertThat(billingCalculator.calculate(subscription)).isEqualByComparingTo("14");
        verifyNoInteractions(planFeatureRepository);
    }

    @Test
    void rejectsMixedCurrencySnapshotItemsInsteadOfSilentlyAddingThem() {
        Subscription subscription = subscription("{\"addOnCodes\":[\"COMPANY_MODULE\"]}");
        SubscriptionEntitlementSnapshot snapshot = new SubscriptionEntitlementSnapshot(
                "PRO", BigDecimal.TEN, "USD",
                BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot("platform.company", List.of())),
                List.of(new SubscriptionAddOnSnapshot(
                        "COMPANY_MODULE", "Company module", 1, BigDecimal.ONE,
                        "EUR", BillingCycle.MONTHLY, List.of("platform.company"))));
        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(snapshot));
        when(subscriptionOverrideReader.read(subscription.getCustomOverrides()))
                .thenReturn(new SubscriptionOverrides(Set.of("COMPANY_MODULE"), List.of()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> billingCalculator.calculateMoney(subscription))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    private Subscription subscription(String overrides) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        plan.setCode("PRO");
        plan.setMoney(Money.zero("USD"));

        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setCustomOverrides(overrides);
        subscription.setEntitlementSnapshot("{\"snapshot\":true}");
        return subscription;
    }
}
