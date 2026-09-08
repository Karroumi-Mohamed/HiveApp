package com.hiveapp.platform.client.plan.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.shared.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SubscriptionRepricingRulesTest {
  private final SubscriptionRepricingRules rules = new SubscriptionRepricingRules();
  private final UUID plan = UUID.randomUUID(), addon = UUID.randomUUID(), pack = UUID.randomUUID();
  private final UUID owner = UUID.randomUUID();

  private ProductPrice price(UUID id, ProductPriceOwnerType type, String amount) {
    ProductPrice price = mock(ProductPrice.class);
    when(price.getId()).thenReturn(id);
    when(price.getOwnerType()).thenReturn(type);
    when(price.ownerId()).thenReturn(owner);
    when(price.getCurrencyCode()).thenReturn("MAD");
    when(price.getBillingCycle()).thenReturn(BillingCycle.MONTHLY);
    when(price.getAmount()).thenReturn(new BigDecimal(amount));
    return price;
  }

  private SubscriptionEntitlementSnapshot snapshot() {
    return new SubscriptionEntitlementSnapshot(
        4,
        "PRO",
        "Pro",
        9,
        new BigDecimal("100"),
        "MAD",
        BillingCycle.MONTHLY,
        Instant.parse("2026-08-01T00:00:00Z"),
        Instant.parse("2026-09-01T00:00:00Z"),
        List.of(new SubscriptionFeatureSnapshot("platform.staff", List.of())),
        List.of(
            new SubscriptionAddOnSnapshot(
                "ROLES",
                "Roles",
                3,
                new BigDecimal("20"),
                "MAD",
                BillingCycle.MONTHLY,
                List.of("roles"),
                addon)),
        List.of(
            new SubscriptionQuotaPackageSnapshot(
                "MEMBERS",
                "Members",
                2,
                "staff",
                "members",
                5,
                4,
                new BigDecimal("10"),
                "MAD",
                BillingCycle.MONTHLY,
                pack)),
        plan,
        null,
        null);
  }

  @Test
  void planReplacementDoesNotRebuildAnyOtherEntitlementOrPrice() {
    var s = snapshot();
    var target = price(UUID.randomUUID(), ProductPriceOwnerType.PLAN, "200");
    var result = rules.replace(s, price(plan, ProductPriceOwnerType.PLAN, "100"), target);
    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("basePrice", "planPriceEntryId")
        .isEqualTo(s);
    assertThat(result.basePrice()).isEqualByComparingTo("200");
    assertThat(result.planPriceEntryId()).isEqualTo(target.getId());
  }

  @Test
  void addonChangesOnlyTheHeldAddonsExactTariff() {
    var s = snapshot();
    var target = price(UUID.randomUUID(), ProductPriceOwnerType.ADD_ON, "25");
    var result = rules.replace(s, price(addon, ProductPriceOwnerType.ADD_ON, "20"), target);
    assertThat(result).usingRecursiveComparison().ignoringFields("addOns").isEqualTo(s);
    assertThat(result.addOns().getFirst())
        .usingRecursiveComparison()
        .ignoringFields("price", "priceEntryId")
        .isEqualTo(s.addOns().getFirst());
    assertThat(result.addOns().getFirst().price()).isEqualByComparingTo("25");
  }

  @Test
  void packChangesUnitPriceWithoutTouchingQuantityOrCapacity() {
    var s = snapshot();
    var source = price(pack, ProductPriceOwnerType.QUOTA_PACKAGE, "10");
    var target = price(UUID.randomUUID(), ProductPriceOwnerType.QUOTA_PACKAGE, "15");
    assertThat(rules.quantity(s, source)).isEqualTo(4);
    var result = rules.replace(s, source, target);
    assertThat(result).usingRecursiveComparison().ignoringFields("quotaPackages").isEqualTo(s);
    var changed = result.quotaPackages().getFirst();
    assertThat(changed)
        .usingRecursiveComparison()
        .ignoringFields("unitPrice", "priceEntryId")
        .isEqualTo(s.quotaPackages().getFirst());
    assertThat(changed.purchasedCapacity()).isEqualTo(20);
    assertThat(changed.unitPrice().multiply(BigDecimal.valueOf(changed.quantity())))
        .isEqualByComparingTo("60");
  }

  @Test
  void productCurrencyAndCycleMismatchesFailClosed() {
    var source = price(plan, ProductPriceOwnerType.PLAN, "100");
    var target = price(UUID.randomUUID(), ProductPriceOwnerType.PLAN, "200");
    when(target.getCurrencyCode()).thenReturn("USD");
    assertThatThrownBy(() -> rules.requirePair(source, target))
        .isInstanceOf(InvalidRequestException.class);
    when(target.getCurrencyCode()).thenReturn("MAD");
    when(target.getBillingCycle()).thenReturn(BillingCycle.YEARLY);
    assertThatThrownBy(() -> rules.requirePair(source, target))
        .isInstanceOf(InvalidRequestException.class);
    when(target.getBillingCycle()).thenReturn(BillingCycle.MONTHLY);
    when(target.ownerId()).thenReturn(UUID.randomUUID());
    assertThatThrownBy(() -> rules.requirePair(source, target))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void monthlyAndYearlyThresholdsNeverInterruptAPaidPeriod() {
    var periods = new SubscriptionPeriodCalculator(Clock.systemUTC());
    Instant end = Instant.parse("2026-09-08T10:00:00Z");
    assertThat(rules.effectiveAt(end, null, BillingCycle.MONTHLY, periods)).isEqualTo(end);
    assertThat(rules.effectiveAt(end, end, BillingCycle.MONTHLY, periods)).isEqualTo(end);
    assertThat(rules.effectiveAt(end, end.plusSeconds(1), BillingCycle.MONTHLY, periods))
        .isEqualTo(Instant.parse("2026-10-08T10:00:00Z"));
    assertThat(rules.effectiveAt(end, end.plusSeconds(1), BillingCycle.YEARLY, periods))
        .isEqualTo(Instant.parse("2027-09-08T10:00:00Z"));
  }
}
